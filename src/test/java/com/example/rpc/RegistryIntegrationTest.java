package com.example.rpc;

import com.example.rpc.client.RpcProxyFactory;
import com.example.rpc.common.HelloService;
import com.example.rpc.server.RpcServer;
import org.apache.curator.test.TestingServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 注册中心集成测试：内嵌ZK + 服务注册 + 服务发现 + 远程调用
 */
public class RegistryIntegrationTest {

    private static TestingServer zkServer;

    @BeforeClass
    public static void setUp() throws Exception {
        zkServer = new TestingServer(2181, true);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        zkServer.close();
    }

    @Test
    public void testRegisterDiscoverAndCall() throws Exception {
        Thread serverThread = new Thread(() -> {
            try {
                new RpcServer("127.0.0.1:2181", "127.0.0.1", 0).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(2000);

        HelloService service = RpcProxyFactory.getProxy(HelloService.class);
        assertEquals("Hello, zk", service.sayHello("zk"));
    }
}
