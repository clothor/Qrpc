package com.example.rpc.client;

import com.example.rpc.common.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * 客户端处理器：按 requestId 配对响应，连接断开时唤醒所有等待请求
 */
public class RpcClientHandler extends ChannelInboundHandlerAdapter {

    private final RpcClient client;

    public RpcClientHandler(RpcClient client) {
        this.client = client;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        client.completeResponse((RpcResponse) msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        client.onChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
