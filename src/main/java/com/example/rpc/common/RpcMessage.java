package com.example.rpc.common;

/**
 * RPC协议报文：固定报文头 + 序列化后的消息体
 */
public class RpcMessage {

    private byte version = ProtocolConstants.VERSION;
    private byte type;
    private byte serialize;
    private byte status;
    private long requestId;
    private byte[] body;

    public byte getVersion() { return version; }
    public void setVersion(byte version) { this.version = version; }

    public byte getType() { return type; }
    public void setType(byte type) { this.type = type; }

    public byte getSerialize() { return serialize; }
    public void setSerialize(byte serialize) { this.serialize = serialize; }

    public byte getStatus() { return status; }
    public void setStatus(byte status) { this.status = status; }

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }

    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body; }
}
