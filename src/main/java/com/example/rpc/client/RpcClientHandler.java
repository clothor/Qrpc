package com.example.rpc.client;

import com.example.rpc.common.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端处理器，接收返回结果
 */
public class RpcClientHandler extends ChannelInboundHandlerAdapter {

    public CompletableFuture<RpcResponse> future;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        RpcResponse resp = (RpcResponse) msg;
        future.complete(resp);
    }
}
