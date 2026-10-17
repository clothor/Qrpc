package com.example.rpc.client;

import com.example.rpc.common.RpcRequest;
import com.example.rpc.common.RpcResponse;

import java.lang.reflect.Proxy;

/**
 * 代理工厂，生成远程接口代理对象（JDK动态代理）
 */
public class RpcProxyFactory {

    public static <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                (proxy, method, args) -> {
                    RpcRequest request = new RpcRequest();
                    request.setInterfaceName(interfaceClass.getName());
                    request.setMethodName(method.getName());
                    request.setParamTypes(method.getParameterTypes());
                    request.setParams(args);

                    RpcClient rpcClient = new RpcClient();
                    RpcResponse response = rpcClient.send(request);
                    if (response.getException() != null) {
                        throw response.getException();
                    }
                    return response.getResult();
                }
        );
    }
}
