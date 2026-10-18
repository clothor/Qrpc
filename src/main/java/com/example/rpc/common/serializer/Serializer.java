package com.example.rpc.common.serializer;

/**
 * 序列化器接口，不同实现通过 getType() 返回的类型标识区分
 */
public interface Serializer {

    byte[] serialize(Object obj);

    <T> T deserialize(byte[] data, Class<T> clazz);

    byte getType();
}
