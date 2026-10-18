package com.example.rpc.client;

import com.example.rpc.common.HelloService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 并发调用测试：验证 requestId 配对与连接复用
 */
public class ConcurrencyTest {

    public static void main(String[] args) throws Exception {
        HelloService service = RpcProxyFactory.getProxy(HelloService.class);

        int total = 20;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        List<Future<String>> futures = new ArrayList<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < total; i++) {
            final int index = i;
            futures.add(pool.submit(() -> service.sayHello("user-" + index)));
        }

        int ok = 0;
        for (int i = 0; i < total; i++) {
            String resp = futures.get(i).get();
            boolean matched = ("Hello, user-" + i).equals(resp);
            if (matched) {
                ok++;
            }
            System.out.println("resp[" + i + "] = " + resp + (matched ? "  [OK]" : "  [MISMATCH]"));
        }
        long cost = System.currentTimeMillis() - start;

        System.out.println("配对正确: " + ok + "/" + total + "，耗时 " + cost + "ms");
        pool.shutdown();
    }
}
