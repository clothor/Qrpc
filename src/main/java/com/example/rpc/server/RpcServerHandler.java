package com.example.rpc.server;

import com.example.rpc.common.HelloService;
import com.example.rpc.common.RpcRequest;
import com.example.rpc.common.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Netty服务处理器：收到请求，反射调用
 */
public class RpcServerHandler extends ChannelInboundHandlerAdapter {

    // 本地服务注册表：接口全类名 -> 服务实例
    private static final Map<String, Object> SERVICE_MAP = new HashMap<>();

    static {
        SERVICE_MAP.put(HelloService.class.getName(), new HelloServiceImpl());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        RpcRequest request = (RpcRequest) msg;
        RpcResponse response = new RpcResponse();
        response.setRequestId(request.getRequestId());
        try {
            Object serviceBean = SERVICE_MAP.get(request.getInterfaceName());
            Method method = serviceBean.getClass().getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(serviceBean, request.getParams());
            response.setResult(result);
        } catch (Exception e) {
            response.setException(e);
        }
        ctx.writeAndFlush(response);
    }
}
