package com.example.rpc.client;

import com.example.rpc.common.ProtocolConstants;
import com.example.rpc.common.RpcMessage;
import com.example.rpc.common.RpcRequest;
import com.example.rpc.common.RpcResponse;
import com.example.rpc.common.codec.RpcDecoder;
import com.example.rpc.common.codec.RpcEncoder;
import com.example.rpc.common.serializer.Serializer;
import com.example.rpc.common.serializer.SerializerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC客户端：单例长连接复用，按 requestId 配对响应，时间轮统一管理超时
 */
public class RpcClient {

    private static volatile RpcClient instance;

    private final NioEventLoopGroup group;
    private final Bootstrap bootstrap;
    private final HashedWheelTimer timer;
    private final long timeoutMillis;
    private final String host;
    private final int port;

    private final Map<Long, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong();

    private volatile Channel channel;

    private RpcClient(String host, int port, long timeoutMillis) {
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
        this.group = new NioEventLoopGroup(0, new DefaultThreadFactory("rpc-client", true));
        this.timer = new HashedWheelTimer(new DefaultThreadFactory("rpc-timer", true));
        this.bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new RpcDecoder())
                                .addLast(new RpcEncoder())
                                .addLast(new RpcClientHandler(RpcClient.this));
                    }
                });
    }

    public static RpcClient getInstance() {
        if (instance == null) {
            synchronized (RpcClient.class) {
                if (instance == null) {
                    instance = new RpcClient("127.0.0.1", 8888, 3000);
                    Runtime.getRuntime().addShutdownHook(new Thread(instance::shutdown));
                }
            }
        }
        return instance;
    }

    public CompletableFuture<RpcResponse> send(RpcRequest request) {
        long requestId = requestIdGenerator.incrementAndGet();
        request.setRequestId(requestId);

        Serializer serializer = SerializerFactory.get(ProtocolConstants.DEFAULT_SERIALIZE);
        RpcMessage message = new RpcMessage();
        message.setVersion(ProtocolConstants.VERSION);
        message.setType(ProtocolConstants.TYPE_REQUEST);
        message.setSerialize(serializer.getType());
        message.setRequestId(requestId);
        message.setBody(serializer.serialize(request));

        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pending.put(requestId, future);

        timer.newTimeout(timeout -> {
            CompletableFuture<RpcResponse> removed = pending.remove(requestId);
            if (removed != null) {
                removed.completeExceptionally(new TimeoutException("RPC请求超时, requestId=" + requestId));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        Channel ch;
        try {
            ch = getChannel();
        } catch (Exception e) {
            pending.remove(requestId);
            future.completeExceptionally(e);
            return future;
        }

        ch.writeAndFlush(message);
        return future;
    }

    private Channel getChannel() {
        Channel ch = channel;
        if (ch != null && ch.isActive()) {
            return ch;
        }
        synchronized (this) {
            if (channel == null || !channel.isActive()) {
                channel = connect();
            }
            return channel;
        }
    }

    private Channel connect() {
        try {
            return bootstrap.connect(host, port).sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("连接RPC服务失败: " + host + ":" + port, e);
        }
    }

    void completeResponse(RpcResponse response) {
        CompletableFuture<RpcResponse> future = pending.remove(response.getRequestId());
        if (future != null) {
            future.complete(response);
        }
    }

    void onChannelInactive() {
        RuntimeException ex = new IllegalStateException("RPC连接已断开");
        for (CompletableFuture<RpcResponse> future : pending.values()) {
            future.completeExceptionally(ex);
        }
        pending.clear();
    }

    public void shutdown() {
        timer.stop();
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }
}
