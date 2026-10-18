package com.example.rpc.client;

import com.example.rpc.common.ProtocolConstants;
import com.example.rpc.common.RpcMessage;
import com.example.rpc.common.RpcRequest;
import com.example.rpc.common.RpcResponse;
import com.example.rpc.common.codec.RpcDecoder;
import com.example.rpc.common.codec.RpcEncoder;
import com.example.rpc.common.loadbalance.LoadBalancer;
import com.example.rpc.common.loadbalance.RoundRobinLoadBalancer;
import com.example.rpc.common.registry.Registry;
import com.example.rpc.common.registry.ServiceDiscovery;
import com.example.rpc.common.registry.ZookeeperRegistry;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC客户端：服务发现 + 负载均衡 + 长连接复用，按 requestId 配对响应
 */
public class RpcClient {

    private static volatile RpcClient instance;

    private final NioEventLoopGroup group;
    private final Bootstrap bootstrap;
    private final HashedWheelTimer timer;
    private final long timeoutMillis;

    private final ServiceDiscovery discovery;
    private final LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

    private final Map<Long, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong();

    private RpcClient(String zkAddress, long timeoutMillis) {
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
        this.discovery = new ServiceDiscovery(new ZookeeperRegistry(zkAddress));
    }

    public static RpcClient getInstance() {
        if (instance == null) {
            synchronized (RpcClient.class) {
                if (instance == null) {
                    instance = new RpcClient("127.0.0.1:2181", 3000);
                    Runtime.getRuntime().addShutdownHook(new Thread(instance::shutdown));
                }
            }
        }
        return instance;
    }

    public CompletableFuture<RpcResponse> send(String serviceName, RpcRequest request) {
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

        discovery.subscribe(serviceName);
        List<String> addresses = discovery.getAddresses(serviceName);
        if (addresses.isEmpty()) {
            pending.remove(requestId);
            future.completeExceptionally(new IllegalStateException("没有可用的服务节点: " + serviceName));
            return future;
        }

        String address = loadBalancer.select(addresses);
        Channel ch;
        try {
            ch = getOrCreateChannel(address);
        } catch (Exception e) {
            pending.remove(requestId);
            future.completeExceptionally(e);
            return future;
        }

        ch.writeAndFlush(message);
        return future;
    }

    private Channel getOrCreateChannel(String address) {
        Channel ch = channels.get(address);
        if (ch != null && ch.isActive()) {
            return ch;
        }
        synchronized (this) {
            ch = channels.get(address);
            if (ch != null && ch.isActive()) {
                return ch;
            }
            Channel newChannel = connect(address);
            channels.put(address, newChannel);
            return newChannel;
        }
    }

    private Channel connect(String address) {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        try {
            return bootstrap.connect(host, port).sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("连接RPC服务失败: " + address, e);
        }
    }

    void completeResponse(RpcResponse response) {
        CompletableFuture<RpcResponse> future = pending.remove(response.getRequestId());
        if (future != null) {
            future.complete(response);
        }
    }

    void onChannelInactive(Channel channel) {
        channels.values().removeIf(ch -> ch == channel);
        RuntimeException ex = new IllegalStateException("RPC连接已断开");
        for (CompletableFuture<RpcResponse> future : pending.values()) {
            future.completeExceptionally(ex);
        }
        pending.clear();
    }

    public void shutdown() {
        timer.stop();
        for (Channel ch : channels.values()) {
            ch.close();
        }
        group.shutdownGracefully();
    }
}
