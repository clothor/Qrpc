package com.example.rpc.common.registry;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 基于ZooKeeper的注册中心：服务节点使用临时节点，Provider宕机自动剔除
 */
public class ZookeeperRegistry implements Registry {

    private static final String ROOT_PATH = "/rpc";

    private final CuratorFramework client;

    public ZookeeperRegistry(String connectString) {
        this.client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(4000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        try {
            client.blockUntilConnected();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("连接ZooKeeper失败: " + connectString, e);
        }
    }

    @Override
    public void register(String serviceName, String address) {
        String path = servicePath(serviceName) + "/" + address;
        try {
            createEphemeral(path);
        } catch (Exception e) {
            throw new RuntimeException("服务注册失败: " + path, e);
        }
    }

    private void createEphemeral(String path) throws Exception {
        try {
            client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(path);
        } catch (KeeperException.NodeExistsException e) {
            client.delete().forPath(path);
            client.create().withMode(CreateMode.EPHEMERAL).forPath(path);
        }
    }

    @Override
    public List<String> discover(String serviceName) {
        String path = servicePath(serviceName);
        try {
            return client.getChildren().forPath(path);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public void watch(String serviceName, Consumer<List<String>> listener) {
        String path = servicePath(serviceName);
        try {
            if (client.checkExists().forPath(path) == null) {
                client.create().creatingParentsIfNeeded().forPath(path);
            }
            TreeCache treeCache = new TreeCache(client, path);
            treeCache.start();
            treeCache.getListenable().addListener((client, event) -> listener.accept(discover(serviceName)));
        } catch (Exception e) {
            throw new RuntimeException("监听服务失败: " + path, e);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private String servicePath(String serviceName) {
        return ROOT_PATH + "/" + serviceName;
    }
}
