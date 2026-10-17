package com.example.rpc.server;

import com.example.rpc.common.HelloService;

/**
 * 接口实现类
 */
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name;
    }
}
