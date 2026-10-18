package com.example.rpc.common.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务发现：本地缓存 + ZK监听刷新，ZK不可用时使用本地缓存兜底
 */
public class ServiceDiscovery {

    private final Registry registry;
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();
    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();

    public ServiceDiscovery(Registry registry) {
        this.registry = registry;
    }

    public void subscribe(String serviceName) {
        if (subscribed.add(serviceName)) {
            refresh(serviceName);
            registry.watch(serviceName, list -> cache.put(serviceName, new ArrayList<>(list)));
        }
    }

    public List<String> getAddresses(String serviceName) {
        List<String> list = cache.get(serviceName);
        if (list == null || list.isEmpty()) {
            refresh(serviceName);
            list = cache.get(serviceName);
        }
        return list == null ? Collections.emptyList() : list;
    }

    private void refresh(String serviceName) {
        List<String> list = registry.discover(serviceName);
        cache.put(serviceName, new ArrayList<>(list));
    }
}
