package com.example.rpc.common.serializer;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.example.rpc.common.ProtocolConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Hessian2序列化实现
 */
public class HessianSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Hessian2Output out = new Hessian2Output(bos);
        try {
            out.writeObject(obj);
            out.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Hessian序列化失败", e);
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        Hessian2Input in = new Hessian2Input(new ByteArrayInputStream(data));
        try {
            return (T) in.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Hessian反序列化失败", e);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public byte getType() {
        return ProtocolConstants.SERIALIZE_HESSIAN;
    }
}
