package com.example.rpc.common;

/**
 * RPC协议常量
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
    }

    // 魔数：用于快速校验是否为RPC协议报文
    public static final int MAGIC = 0x12345678;

    // 协议版本
    public static final byte VERSION = 1;

    // 消息类型
    public static final byte TYPE_REQUEST = 1;
    public static final byte TYPE_RESPONSE = 2;

    // 响应状态
    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_ERROR = 1;

    // 序列化类型
    public static final byte SERIALIZE_JDK = 0;
    public static final byte SERIALIZE_HESSIAN = 1;
    public static final byte DEFAULT_SERIALIZE = SERIALIZE_HESSIAN;

    // 报文头长度：magic(4) + version(1) + type(1) + serialize(1) + status(1) + requestId(8) + bodyLength(4)
    public static final int HEADER_LENGTH = 4 + 1 + 1 + 1 + 1 + 8 + 4;

    // 单个消息体最大长度，防止恶意超长帧
    public static final int MAX_BODY_LENGTH = 8 * 1024 * 1024;
}
