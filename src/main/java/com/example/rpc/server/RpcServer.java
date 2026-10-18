package com.example.rpc.server;

import com.example.rpc.common.HelloService;
import com.example.rpc.common.codec.RpcDecoder;
import com.example.rpc.common.codec.RpcEncoder;
import com.example.rpc.common.registry.Registry;
import com.example.rpc.common.registry.ZookeeperRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

/**
 * 服务端启动类：启动Netty服务，注册到注册中心，装配编解码器+handler
 */
public class RpcServer {

    private final String zkAddress;
    private final String host;
    private final int port;

    public RpcServer(String zkAddress, String host, int port) {
        this.zkAddress = zkAddress;
        this.host = host;
        this.port = port;
    }

    public void start() throws InterruptedException {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new RpcDecoder())
                                    .addLast(new RpcEncoder())
                                    .addLast(new RpcServerHandler());
                        }
                    });

            Channel channel = bootstrap.bind(port).sync().channel();
            int actualPort = ((InetSocketAddress) channel.localAddress()).getPort();

            Registry registry = new ZookeeperRegistry(zkAddress);
            registry.register(HelloService.class.getName(), host + ":" + actualPort);

            System.out.println("RPC Server started on " + host + ":" + actualPort + ", registered to " + zkAddress);
            channel.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new RpcServer("127.0.0.1:2181", "127.0.0.1", 8888).start();
    }
}
