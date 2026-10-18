package com.example.rpc.client;

import com.example.rpc.common.RpcRequest;
import com.example.rpc.common.RpcResponse;

import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * 代理工厂，生成远程接口代理对象（JDK动态代理）
 */
public class RpcProxyFactory {

    private static final RpcClient CLIENT = RpcClient.getInstance();

    public static <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                (proxy, method, args) -> {
                    RpcRequest request = new RpcRequest();
                    request.setInterfaceName(interfaceClass.getName());
                    request.setMethodName(method.getName());
                    request.setParamTypeNames(Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray(String[]::new));
                    request.setParams(args);

                    RpcResponse response;
                    try {
                        response = CLIENT.send(request).get();
                    } catch (Exception e) {
                        throw new RuntimeException("RPC调用失败: " + method.getName(), e);
                    }
                    if (response.getException() != null) {
                        throw response.getException();
                    }
                    return response.getResult();
                }
        );
    }
}
