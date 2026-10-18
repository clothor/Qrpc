package com.example.rpc.common.loadbalance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String select(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        int index = Math.abs(counter.getAndIncrement() % addresses.size());
        return addresses.get(index);
    }
}
