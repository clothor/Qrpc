package com.example.rpc.common.codec;

import com.example.rpc.common.ProtocolConstants;
import com.example.rpc.common.RpcMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * RPC编码器：把RpcMessage写入二进制报文
 */
public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        out.writeInt(ProtocolConstants.MAGIC);
        out.writeByte(msg.getVersion());
        out.writeByte(msg.getType());
        out.writeByte(msg.getSerialize());
        out.writeByte(msg.getStatus());
        out.writeLong(msg.getRequestId());
        byte[] body = msg.getBody();
        out.writeInt(body.length);
        out.writeBytes(body);
    }
}
