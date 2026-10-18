package com.example.rpc.server;

import com.example.rpc.common.HelloService;
import com.example.rpc.common.ProtocolConstants;
import com.example.rpc.common.RpcMessage;
import com.example.rpc.common.RpcRequest;
import com.example.rpc.common.RpcResponse;
import com.example.rpc.common.serializer.Serializer;
import com.example.rpc.common.serializer.SerializerFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Netty服务处理器：解码RpcMessage，反射调用，编码RpcMessage写回
 */
public class RpcServerHandler extends ChannelInboundHandlerAdapter {

    // 本地服务注册表：接口全类名 -> 服务实例
    private static final Map<String, Object> SERVICE_MAP = new HashMap<>();

    static {
        SERVICE_MAP.put(HelloService.class.getName(), new HelloServiceImpl());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        RpcMessage message = (RpcMessage) msg;
        Serializer serializer = SerializerFactory.get(message.getSerialize());
        RpcRequest request = serializer.deserialize(message.getBody(), RpcRequest.class);

        RpcResponse response = new RpcResponse();
        response.setRequestId(request.getRequestId());

        RpcMessage responseMessage = new RpcMessage();
        responseMessage.setVersion(ProtocolConstants.VERSION);
        responseMessage.setType(ProtocolConstants.TYPE_RESPONSE);
        responseMessage.setSerialize(message.getSerialize());
        responseMessage.setRequestId(request.getRequestId());

        try {
            Object serviceBean = SERVICE_MAP.get(request.getInterfaceName());
            Method method = serviceBean.getClass().getMethod(request.getMethodName(), resolveParamTypes(request));
            Object result = method.invoke(serviceBean, request.getParams());
            response.setResult(result);
            responseMessage.setStatus(ProtocolConstants.STATUS_SUCCESS);
        } catch (Exception e) {
            response.setException(e);
            responseMessage.setStatus(ProtocolConstants.STATUS_ERROR);
        }

        responseMessage.setBody(serializer.serialize(response));
        ctx.writeAndFlush(responseMessage);
    }

    private Class<?>[] resolveParamTypes(RpcRequest request) throws ClassNotFoundException {
        String[] names = request.getParamTypeNames();
        if (names == null) {
            return new Class<?>[0];
        }
        Class<?>[] types = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            types[i] = Class.forName(names[i]);
        }
        return types;
    }
}
