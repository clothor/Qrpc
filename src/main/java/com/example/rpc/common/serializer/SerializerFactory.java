package com.example.rpc.common.serializer;

import com.example.rpc.common.ProtocolConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * 序列化器工厂，按类型标识获取序列化器
 */
public class SerializerFactory {

    private static final Map<Byte, Serializer> SERIALIZERS = new HashMap<>();

    static {
        SERIALIZERS.put(ProtocolConstants.SERIALIZE_JDK, new JdkSerializer());
        SERIALIZERS.put(ProtocolConstants.SERIALIZE_HESSIAN, new HessianSerializer());
    }

    public static Serializer get(byte type) {
        Serializer serializer = SERIALIZERS.get(type);
        if (serializer == null) {
            throw new IllegalArgumentException("不支持的序列化类型: " + type);
        }
        return serializer;
    }
}
