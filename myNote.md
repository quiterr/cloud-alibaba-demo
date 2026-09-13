# 随笔

## 账号
1、虚拟机账号：Huang@入职公司年

## 启动命令

1、idea中启动nacos
```shell
cd nacos dir
./startup.cmd -m standalone
```

2、启动powerjob：idea打开powerjob项目，找到powerjob-server-starter，启动主程序。

## 测试微服务

测试用户服务 
```shell
http://localhost:8081/user/1
```

测试订单服务
```shell
http://localhost:8082/order/getUser/1
```

测试网关服务
```shell
http://localhost:8080/user-service/user/1
http://localhost:8080/order-service/order/getUser/1
```

## nacos连接达梦数据库

nacos默认使用本地数据库derby，如果要连接达梦数据库，则需要下载官方插件源码。
比较坑的是官方插件维护得不咋地（豆包也是傻傻搞不定的），代码和文档都有所欠缺，和nacos的版本对应感觉有点乱。
这次nacos我用的2.x的最新版2.5.2，插件我一开始下的最新代码，结果遇到两个坑：
- 不知道编译出来的插件jar放哪个目录，不知道除了插件jar还需要把达梦的jdbc jar也放到指定目录。
- 插件源码目录中的sql语句不完整，2.5版本后nacos需要config_info_gray表。
- nacos 2.5.2版本最高支持jdk 11，相应的插件也必须用jdk 11编译，不能用jdk 17编译。

最后摸索半天才知道插件jar应该放在plugins目录，而达梦jdbc jar需要放在plugins/mysql目录。
插件源码要下载v2-develop分支。

**以后遇到问题要看官方的issue，有人遇到了同样的问题，并且给了答案**

## powerjob连接达梦数据库

源代码克隆下来之后，需要参考官方文档修改powerjob的配置文件，启动后报：

```shell
Failed to load driver class dm.jdbc.driver.DmDriver
```

这个报错和nacos连达梦报的一样，那么解决方法大概率也是一样，首先pom文件加上：


```shell
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmJdbcDriver18</artifactId>
    <version>${jdbc.dm.version}</version>
    <scope>system</scope>
    <systemPath>${basedir}/lib/DmJdbcDriver18.jar</systemPath>
</dependency>
```

接着在项目根目录放上lib/DmJdbcDriver18.jar

配置文件：
```properties
####### Database properties(Configure according to the the environment) #######
spring.datasource.core.driver-class-name=dm.jdbc.driver.DmDriver
spring.datasource.core.jdbc-url=jdbc:dm://127.0.0.1:5236/powerjob-schema
spring.datasource.core.username=SYSDBA
spring.datasource.core.password=Huang@2020
spring.datasource.core.maximum-pool-size=20
spring.datasource.core.minimum-idle=5
```

由于是在idea中运行powerjob server，还需要在模块设置->依赖中加入DmJdbcDriver18.jar所在的目录，否则报错：
```shell
Failed to load driver class dm.jdbc.driver.DmDriver in either of HikariConfig class loader or Thread context classloader
```

如果不引入方言的配置会报错，引入也报错：
```shell
Unable to load class [org.hibernate.dialect.DmDialect]
```

需要引入方言的依赖：
```shell
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmDialect-for-hibernate5.6</artifactId>
    <version>${jdbc.dm.version}</version>
</dependency>
```

启动成功后，访问 http://127.0.0.1:7700/

账号 ADMIN，密码 powerjob_admin

## 整合shiro

在parent的pom文件dependencyManagement中加入下面这个依赖，然后去模块的pom文件加入shiro的依赖会报错
```shell
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-bom</artifactId>
    <version>2.1.0</version>
    <scope>import</scope>
    <type>pom</type>
</dependency>
```

报错内容：
```shell
程序包org.apache.shiro.realm不存在
```

解决之后又报：
```shell
java.lang.ClassNotFoundException: javax.servlet.Filter
```

**解决之道就是要用英文版的谷歌进行搜索**
```shell
https://github.com/apache/shiro/tree/main/samples/spring-boot-3-web
```

## 在服务器上部署微服务

遇到了报错：
```shell
[root@k8s-node1 gateway]# java -jar -Dspring.profiles.active=test gateway-server-0.0.1-SNAPSHOT.jar
2026-04-12T20:37:06.700+08:00 ERROR 271683 --- [remote.worker.1] c.a.n.c.remote.client.grpc.GrpcClient    : Server check fail, please check server 127.0.0.1 ,port 9848 is available , error ={}

java.util.concurrent.ExecutionException: com.alibaba.nacos.shaded.io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
        at com.alibaba.nacos.shaded.com.google.common.util.concurrent.AbstractFuture.getDoneValue(AbstractFuture.java:592) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.com.google.common.util.concurrent.AbstractFuture.get(AbstractFuture.java:467) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.common.remote.client.grpc.GrpcClient.serverCheck(GrpcClient.java:243) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.common.remote.client.grpc.GrpcClient.connectToServer(GrpcClient.java:367) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.common.remote.client.RpcClient.reconnect(RpcClient.java:502) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.common.remote.client.RpcClient.lambda$start$1(RpcClient.java:329) ~[nacos-client-2.3.2.jar!/:na]
        at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:539) ~[na:na]
        at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264) ~[na:na]
        at java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304) ~[na:na]
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136) ~[na:na]
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635) ~[na:na]
        at java.base/java.lang.Thread.run(Thread.java:833) ~[na:na]
Caused by: com.alibaba.nacos.shaded.io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
        at com.alibaba.nacos.shaded.io.grpc.Status.asRuntimeException(Status.java:537) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:548) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.DelayedClientCall$DelayedListener$3.run(DelayedClientCall.java:489) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.DelayedClientCall$DelayedListener.delayOrExecute(DelayedClientCall.java:453) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.DelayedClientCall$DelayedListener.onClose(DelayedClientCall.java:486) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133) ~[nacos-client-2.3.2.jar!/:na]
        ... 3 common frames omitted
Caused by: com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.AbstractChannel$AnnotatedConnectException: 拒绝连接: /127.0.0.1:9848
Caused by: java.net.ConnectException: 拒绝连接
        at java.base/sun.nio.ch.Net.pollConnect(Native Method) ~[na:na]
        at java.base/sun.nio.ch.Net.pollConnectNow(Net.java:672) ~[na:na]
        at java.base/sun.nio.ch.SocketChannelImpl.finishConnect(SocketChannelImpl.java:946) ~[na:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel.doFinishConnect(NioSocketChannel.java:337) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe.finishConnect(AbstractNioChannel.java:334) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:776) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74) ~[nacos-client-2.3.2.jar!/:na]
        at com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30) ~[nacos-client-2.3.2.jar!/:na]
        at java.base/java.lang.Thread.run(Thread.java:833) ~[na:na]

2026-04-12T20:37:06.731+08:00  INFO 271683 --- [remote.worker.1] com.alibaba.nacos.common.remote.client   : [bcd98ce9-fd2f-4317-96ba-11130d4d0597_config-0] Fail to connect server, after trying 4 times, last try server is {serverIp = '127.0.0.1', server main port = 8848}, error = unknown
2026-04-12T20:37:06.816+08:00  WARN 271683 --- [           main] c.a.c.n.c.NacosConfigDataLoader          : [Nacos Config] config[dataId=192.168.133.1:8848, group=DEFAULT_GROUP] is empty
2026-04-12T20:37:06.816+08:00  WARN 271683 --- [           main] c.a.c.n.c.NacosConfigDataLoader          : [Nacos Config] config[dataId=192.168.133.1:8848, group=DEFAULT_GROUP] is empty
```

这个报错比较隐蔽就是容易被下面日志误导：
```shell
Caused by: com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.AbstractChannel$AnnotatedConnectException: 拒绝连接: /127.0.0.1:9848
```

一直朝着127.0.0.1:9948这个方向去解决，尝试了很多方法都不行。

实际关键日志是：
```shell
Fail to connect server, after trying 4 times, last try server is {serverIp = '127.0.0.1', server main port = 8848}
```

把这个日志丢给豆包之后，他马上给出了有效的解决办法
```shell
java -jar -Dspring.profiles.active=test -Dspring.cloud.nacos.server-addr=192.168.133.1:8848 -Dspring.cloud.nacos.discovery.server-addr=192.168.133.1:8848 -Dspring.cloud.nacos.config.server-addr=192.168.133.1:8848 gateway-server-0.0.1-SNAPSHOT.jar
```

**解决了，但是我不理解。因为我已经用-Dspring.profiles.active=test指定了test配置。**
豆包的解释是（不保证对）：
Nacos 客户端在 bootstrap 阶段就会初始化！
它的执行顺序是：
加载 bootstrap.yml（最优先）
初始化 Nacos
然后才去读 spring.profiles.active=test
最后才读 application.yml

没办法，作为测试，如果我在打包的时候就在bootstrap.yml中指定test环境，是否可以呢？
答案是不可以，无论怎么搞都是不可以。

而且我发现之前那个命令可一件简化：
```shell
```shell
java -jar -Dspring.cloud.nacos.discovery.server-addr=192.168.133.1:8848 -Dspring.cloud.nacos.config.server-addr=192.168.133.1:8848 gateway-server-0.0.1-SNAPSHOT.jar
```

不过不推荐用config.server-addr，推荐用import。不管怎样，老感觉程序加载根本没有读配置。

这个命令有点用，可以看jar包里面的东西：
```shell
cd temp && jar -xf ../gateway-server-0.0.1-SNAPSHOT.jar
```

现在最新的情况是禁用了bootstrap.yml之类的配置文件，包括依赖也删掉了。用命令行能正常启动。
用application.yml就是不行，但是100%确定程序读了application.yml中的配置，因为我把
```shell
spring:
  config:
    import: optional:nacos:${spring.cloud.nacos.discovery.server-addr}
```

写成了
```shell
spring:
  cloud:
    config:
      import: optional:nacos:${spring.cloud.nacos.discovery.server-addr}
```

会报错：
```shell
No spring.config.import property has been defined
```

所以100%配置是读了的。
我后边又测试下面这个命令，也是成功的：
```shell
java -jar  -Dspring.cloud.nacos.config.server-addr=192.168.133.1:8848 gateway-server-0.0.1-SNAPSHOT.jar
```

我现在怀疑从nacos服务端引入配置之后，把本地的冲掉了，导致老是去连127.0.0.1。明天试试在服务端创建配置。


## springcloud gateway

第一个注意点：别引入spring-boot-starter-web

第二个注意点：与acurator结合使用时，要加enable: true
```shell
management:
  endpoints:
    web:
      exposure:
        include: "*"              # 暴露所有端点（生产环境按需）
      base-path: /actuator        # 访问前缀
  endpoint:
    health:
      show-details: always        # 显示详细健康信息
    gateway:
      enabled: true
```

第三个注意点，下面这个配置
```shell
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/user-service/**
```

访问会报404
```shell
http://localhost:8080/user-service/user/1
```

原因是路径多了一层，类似这样
```shell
http://localhost:8081/user-service/user-service/user/1
```

正确的配置：
```shell
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/user-service/**
          filters:
            - StripPrefix=1  # 👈 加这个！
```

默认就用自动路由吧，省心省力。

第四个注意点，路由就在本地配置，别去nacos上折腾，不起作用。

最后来一个tips，开启gateway端点后，访问http://localhost:8080/actuator/gateway/routes，可以看到路由
```shell
[
{
"predicate": "Paths: [/user-service/**], match trailing slash: true",
"metadata": {
"management.endpoints.web.base-path": "/actuator",
"nacos.instanceId": "192.168.133.1#8081##DEFAULT_GROUP@@user-service",
"nacos.weight": "1.0",
"nacos.cluster": "DEFAULT",
"IPv6": "[2401:7e00:820:c770:27a7:ca50:a89:ae1d]",
"nacos.ephemeral": "true",
"nacos.healthy": "true",
"preserved.register.source": "SPRING_CLOUD"
},
"route_id": "ReactiveCompositeDiscoveryClient_user-service",
"filters": [
"[[RewritePath /user-service/?(?<remaining>.*) = '/${remaining}'], order = 1]"
],
"uri": "lb://user-service",
"order": 0
},
{
"predicate": "Paths: [/order-service/**], match trailing slash: true",
"metadata": {
"nacos.instanceId": "192.168.133.1#8082##DEFAULT_GROUP@@order-service",
"nacos.weight": "1.0",
"nacos.cluster": "DEFAULT",
"IPv6": "[2401:7e00:820:c770:27a7:ca50:a89:ae1d]",
"nacos.ephemeral": "true",
"nacos.healthy": "true",
"preserved.register.source": "SPRING_CLOUD"
},
"route_id": "ReactiveCompositeDiscoveryClient_order-service",
"filters": [
"[[RewritePath /order-service/?(?<remaining>.*) = '/${remaining}'], order = 1]"
],
"uri": "lb://order-service",
"order": 0
}
]
```