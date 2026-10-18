package com.example.rpc.common.loadbalance;

import java.util.List;

/**
 * 负载均衡策略接口
 */
public interface LoadBalancer {

    String select(List<String> addresses);
}
