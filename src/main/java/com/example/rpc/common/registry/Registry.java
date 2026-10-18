package com.example.rpc.common.registry;

import java.util.List;
import java.util.function.Consumer;

/**
 * 注册中心接口，屏蔽具体实现（Zookeeper/Nacos等）
 */
public interface Registry {

    void register(String serviceName, String address);

    List<String> discover(String serviceName);

    void watch(String serviceName, Consumer<List<String>> listener);

    void close();
}
