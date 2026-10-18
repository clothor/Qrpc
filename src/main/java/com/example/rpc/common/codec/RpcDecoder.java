package com.example.rpc.common.codec;

import com.example.rpc.common.ProtocolConstants;
import com.example.rpc.common.RpcMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

/**
 * RPC解码器：按固定报文头解析，处理TCP粘包与拆包
 */
public class RpcDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < ProtocolConstants.HEADER_LENGTH) {
            return;
        }
        in.markReaderIndex();

        int magic = in.readInt();
        if (magic != ProtocolConstants.MAGIC) {
            in.resetReaderIndex();
            throw new CorruptedFrameException("非法的魔数: " + magic);
        }

        byte version = in.readByte();
        byte type = in.readByte();
        byte serialize = in.readByte();
        byte status = in.readByte();
        long requestId = in.readLong();
        int bodyLength = in.readInt();

        if (bodyLength < 0 || bodyLength > ProtocolConstants.MAX_BODY_LENGTH) {
            in.resetReaderIndex();
            throw new CorruptedFrameException("非法的消息体长度: " + bodyLength);
        }
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[bodyLength];
        in.readBytes(body);

        RpcMessage message = new RpcMessage();
        message.setVersion(version);
        message.setType(type);
        message.setSerialize(serialize);
        message.setStatus(status);
        message.setRequestId(requestId);
        message.setBody(body);
        out.add(message);
    }
}
