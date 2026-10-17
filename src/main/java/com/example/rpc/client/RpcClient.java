package com.example.rpc.client;

import com.example.rpc.common.RpcRequest;
import com.example.rpc.common.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.util.concurrent.CompletableFuture;

/**
 * RPC客户端：建立连接、发送请求、等待返回
 */
public class RpcClient {

    public RpcResponse send(RpcRequest request) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        RpcClientHandler handler = new RpcClientHandler();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new ObjectEncoder())
                                .addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)))
                                .addLast(handler);
                    }
                });
        var channel = bootstrap.connect("127.0.0.1", 8888).sync().channel();
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        handler.future = future;
        channel.writeAndFlush(request);
        RpcResponse resp = future.get();
        channel.close();
        group.shutdownGracefully();
        return resp;
    }
}
