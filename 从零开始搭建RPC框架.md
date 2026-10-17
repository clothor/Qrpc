# 项目总目的

> 
> 目标：**实现最简RPC，让业务代码调用接口方法时，底层自动走TCP网络，调用另一台JVM里的服务**
> 限制：第一版**不做注册中心，硬编码IP+端口**；先单请求可用，后面再修复并发问题。
> 核心思想：客户端动态代理拦截方法 → 组装请求对象 → Netty发TCP → 服务端收到请求反射执行方法 → 返回结果。

## 整体拆成 5 个小目标，逐个完成

### 目标1：定义公共模型（两端共用）

**目标作用**：定义网络传输的数据包。客户端打包、服务端解包，两边约定好数据结构。
需要写：

1. 业务接口 `HelloService`（客户端和服务端必须完全一样）
2. 请求实体 `RpcRequest`：存调用哪个接口、哪个方法、参数
3. 响应实体 `RpcResponse`：存返回结果 / 异常

**流程**

1. 创建接口，声明要远程调用的方法
2. 创建 `RpcRequest`，属性：interfaceName、methodName、paramTypes、params
3. 创建 `RpcResponse`，属性：result、exception

---

### 目标2：服务端（Provider）开发：接收请求，反射执行业务方法

**目标作用**：启动Netty TCP服务，监听端口；收到网络请求，找到本地实现类，反射调用方法，把结果返回给客户端。
需要写：

1. 接口实现类 `HelloServiceImpl`
2. `RpcServerHandler`：Netty的入站处理器，收到`RpcRequest`，反射调用，封装`RpcResponse`写回网络
3. `RpcServer` 启动类：启动Netty服务，绑定端口，装配编解码器+handler

**流程**

1. 维护本地服务注册表 `Map<接口全类名, 服务实例>`
2. Netty收到对象，转成RpcRequest
3. 根据接口全名取出本地实现对象
4. 反射拿到Method，invoke执行
5. 捕获异常，放进RpcResponse
6. 通过channel写回response

---

### 目标3：客户端网络层：Netty客户端，发送RpcRequest，等待响应

**目标作用**：建立TCP连接，把请求发出去，阻塞等待服务端返回的RpcResponse。
需要写：

1. `RpcClientHandler`：Netty客户端处理器，收到响应后保存结果
2. `RpcClient`：建立连接、发送请求、等待返回

**流程**

1. Netty Bootstrap连接服务端 127.0.0.1:8888
2. 把RpcRequest写入channel发送
3. 阻塞等待响应回来
4. 返回RpcResponse给上层

> 
> ⚠️ 当前版本缺陷：每次调用新建连接；**没有requestId，只能单请求，并发会乱**

---

### 目标4：客户端动态代理（RPC灵魂）

**目标作用**：对业务接口生成代理对象。业务调用`helloService.sayHello()`时，拦截这个方法，自动组装RpcRequest，调用RpcClient发送网络请求。
需要写：
`RpcProxyFactory`，JDK动态代理工厂

**流程**

1. 业务获取代理对象 `HelloService helloService = RpcProxyFactory.getProxy(HelloService.class)`
2. 业务执行 `helloService.sayHello("xxx")`
3. 代理的invoke拦截方法：拿到接口名、方法名、参数
4. 组装RpcRequest
5. 调用RpcClient发送，拿到RpcResponse
6. 如果有异常直接抛出；没有异常返回result给业务代码

---

### 目标5：测试运行，验证链路通不通

**目标作用**：验证整套流程跑通
流程：

1. 启动 `RpcServer` （先起服务端，监听8888）
2. 启动 `RpcClientMain` 客户端
3. 客户端调用代理对象方法，打印返回结果

# 依赖（Maven pom.xml）

```
<dependencies>
    <!-- Netty 网络通信 -->
    <dependency>
        <groupId>io.netty</groupId>
        <artifactId>netty-all</artifactId>
        <version>4.1.100.Final</version>
    </dependency>
</dependencies>
```

# 分目标代码（一段一段，对应每个目标）

## 目标1 公共模型代码

```
// 业务接口，客户端、服务端共用
public interface HelloService {
    String sayHello(String name);
}
```

```
import java.io.Serializable;
// RPC请求包
public class RpcRequest implements Serializable {
    private String interfaceName;
    private String methodName;
    private Class<?>[] paramTypes;
    private Object[] params;

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public Class<?>[] getParamTypes() { return paramTypes; }
    public void setParamTypes(Class<?>[] paramTypes) { this.paramTypes = paramTypes; }
    public Object[] getParams() { return params; }
    public void setParams(Object[] params) { this.params = params; }
}
```

```
import java.io.Serializable;
// RPC响应包
public class RpcResponse implements Serializable {
    private Object result;
    private Exception exception;

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public Exception getException() { return exception; }
    public void setException(Exception exception) { this.exception = exception; }
}
```

## 目标2 服务端代码

```
// 接口实现类
public class HelloServiceImpl implements HelloService{
    @Override
    public String sayHello(String name) {
        return "Hello, " + name;
    }
}
```

```
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// Netty服务处理器：收到请求，反射调用
public class RpcServerHandler extends ChannelInboundHandlerAdapter {
    // 本地服务注册表
    private static final Map<String, Object> SERVICE_MAP = new HashMap<>();
    static {
        SERVICE_MAP.put(HelloService.class.getName(), new HelloServiceImpl());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        RpcRequest request = (RpcRequest) msg;
        RpcResponse response = new RpcResponse();
        try {
            Object serviceBean = SERVICE_MAP.get(request.getInterfaceName());
            Method method = serviceBean.getClass().getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(serviceBean, request.getParams());
            response.setResult(result);
        } catch (Exception e) {
            response.setException(e);
        }
        ctx.writeAndFlush(response);
    }
}
```

```
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

// 服务端启动类
public class RpcServer {
    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new ObjectEncoder())
                                    .addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)))
                                    .addLast(new RpcServerHandler());
                        }
                    });
            bootstrap.bind(8888).sync().channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
```

## 目标3 客户端网络代码

```
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.concurrent.CompletableFuture;

// 客户端处理器，接收返回结果
public class RpcClientHandler extends ChannelInboundHandlerAdapter {
    public CompletableFuture<RpcResponse> future;
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        RpcResponse resp = (RpcResponse) msg;
        future.complete(resp);
    }
}
```

```
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import java.util.concurrent.CompletableFuture;

// RPC客户端：建立连接、发送请求
public class RpcClient {
    public RpcResponse send(RpcRequest request) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        RpcClientHandler handler = new RpcClientHandler();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new ObjectEncoder())
                                .addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)))
                                .addLast(handler);
                    }
                });
        var channel = bootstrap.connect("127.0.0.1", 8888).sync().channel();
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        handler.future = future;
        channel.writeAndFlush(request);
        RpcResponse resp = future.get();
        channel.close();
        group.shutdownGracefully();
        return resp;
    }
}
```

## 目标4 动态代理代码

```
import java.lang.reflect.Proxy;

// 代理工厂，生成远程接口代理对象
public class RpcProxyFactory {
    public static <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                (proxy, method, args) -> {
                    RpcRequest request = new RpcRequest();
                    request.setInterfaceName(interfaceClass.getName());
                    request.setMethodName(method.getName());
                    request.setParamTypes(method.getParameterTypes());
                    request.setParams(args);

                    RpcClient rpcClient = new RpcClient();
                    RpcResponse response = rpcClient.send(request);
                    if(response.getException() != null){
                        throw response.getException();
                    }
                    return response.getResult();
                }
        );
    }
}
```

## 目标5 测试客户端主类

```
public class RpcClientMain {
    public static void main(String[] args) throws Exception {
        HelloService helloService = RpcProxyFactory.getProxy(HelloService.class);
        String res = helloService.sayHello("小明");
        System.out.println(res);
    }
}
```

# 运行顺序

1. 运行 `RpcServer`，启动服务端
2. 运行 `RpcClientMain`，启动客户端
3. 控制台输出：`Hello, 小明`

# 当前版本限制（记住，这是下一阶段要解决的目标）

1. 没有requestId，**多线程并发调用会出现请求响应配对错乱**
2. 每次调用新建Netty连接，无连接池，性能差
3. JDK序列化，不安全，不适合生产
4. 硬编码IP端口，没有注册中心

## 下一阶段小目标（迭代升级）

- 目标A：给RpcRequest加上requestId；客户端维护 `requestId -> Future` Map，支持并发调用（最高优先级）
- 目标B：自定义二进制协议，替换JDK序列化，处理TCP粘包拆包
- 目标C：接入Nacos，实现服务注册与订阅
- 目标D：负载均衡
- 目标E：超时、重试容错

你可以先把这一版跑通，跑成功之后，我们再进入目标A（增加requestId支持并发）。
要不要我们先把这个项目的包结构整理出来，方便你在IDEA里直接创建对应的package？