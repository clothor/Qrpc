package com.example.rpc.client;

import com.example.rpc.common.HelloService;

/**
 * 测试客户端主类
 */
public class RpcClientMain {
    public static void main(String[] args) throws Exception {
        HelloService helloService = RpcProxyFactory.getProxy(HelloService.class);
        String res = helloService.sayHello("小明");
        System.out.println(res);
    }
}
