package com.example.rpc.common.loadbalance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡
 */
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public String select(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        return addresses.get(ThreadLocalRandom.current().nextInt(addresses.size()));
    }
}
