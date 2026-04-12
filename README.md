# 随笔

## 账号
1、家里虚拟机root账号：Huang@2020

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

## 麒麟V10安装k8s

k8s版本1.28。

### k8s前置配置

```shell
# 1. 关闭交换分区(必须)
swapoff -a
sed -i '/swap/s/^/#/' /etc/fstab

# 2. 关闭防火墙
systemctl stop firewalld
systemctl disable firewalld

# 3. 关闭SELinux
setenforce 0
sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config

# 4. 加载内核模块
cat <<EOF | tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

# 5. 网络参数(必须)
cat <<EOF | tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sysctl --system
```

### 安装container
第一步：清理所有垃圾
```shell
yum remove -y containerd* runc* docker* docker-runc
rm -rf /etc/containerd /var/lib/containerd
rm -f /etc/yum.repos.d/docker*.repo
```

第二步：直接创建 Docker 源（不用 yum-utils）
```shell
cat > /etc/yum.repos.d/docker-ce.repo <<EOF
[docker-ce-stable]
name=Docker CE Stable - x86_64
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/x86_64/stable/
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
EOF
```

第三步：刷新源
```shell
yum clean all
yum makecache
```

第四步：安装 el8 老版本 containerd（兼容麒麟 V10）
这是最关键的一步，装这个版本不会出现 GLIBC 错误！
```shell
yum install -y containerd.io-1.6.21-3.1.el8 --allowerasing
```

第五步：创建配置
```shell
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
```

第六步：启动（这次一定成功！）
```shell
systemctl daemon-reload
systemctl enable --now containerd
systemctl status containerd
```

## K8s 安装

添加阿里云 K8s 源（国内最快、最稳）
```shell
cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=http://mirrors.aliyun.com/kubernetes/yum/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=0
repo_gpgcheck=0
EOF
```

安装 K8s 1.28.0（固定版本，不漂移）
```shell
yum clean all
yum makecache

yum install -y kubelet-1.28.0 kubeadm-1.28.0 kubectl-1.28.0
```

启动 kubelet 并开机自启
```shell
systemctl enable --now kubelet
```

### 初始化 K8s 集群
先查看你虚拟机的 IP
```shell
ip addr
```
找到你的内网 IP，例如：192.168.xxx.xxx

用下面命令初始化（把 IP 换成你自己的）
```shell
kubeadm init \
  --apiserver-advertise-address=192.168.133.129 \
  --image-repository registry.aliyuncs.com/google_containers \
  --kubernetes-version v1.28.0 \
  --service-cidr=10.96.0.0/12 \
  --pod-network-cidr=10.244.0.0/16
```

遇到一个报错[WARNING Hostname]: hostname "k8s-node1" could not be reached：
```shell
[init] Using Kubernetes version: v1.28.0
[preflight] Running pre-flight checks
        [WARNING Hostname]: hostname "k8s-node1" could not be reached
        [WARNING Hostname]: hostname "k8s-node1": lookup k8s-node1 on 192.168.133.2:53: no such host
error execution phase preflight: [preflight] Some fatal errors occurred:
        [ERROR FileContent--proc-sys-net-ipv4-ip_forward]: /proc/sys/net/ipv4/ip_forward contents are not set to 1
[preflight] If you know what you are doing, you can make a check non-fatal with `--ignore-preflight-errors=...`
To see the stack trace of this error execute with --v=5 or higher
```

解决之后还是报：
```shell
error execution phase preflight: [preflight] Some fatal errors occurred:
        [ERROR FileContent--proc-sys-net-ipv4-ip_forward]: /proc/sys/net/ipv4/ip_forward contents are not set to 1
```

豆包建议执行：
```shell
# 1. 强制开启 ip_forward
echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf

# 2. 强制覆盖系统检查文件
echo 1 > /proc/sys/net/ipv4/ip_forward

# 3. 强制加载所有网络参数
sysctl -p /etc/sysctl.conf
sysctl --system

# 4. 验证是否真的变成 1（必须输出 1 才算成功）
cat /proc/sys/net/ipv4/ip_forward
```

并且再执行一遍：
```shell
cat <<EOF | tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sysctl --system
```

接着又报了新的错：
```shell
[init] Using Kubernetes version: v1.28.0
[preflight] Running pre-flight checks
[preflight] Pulling images required for setting up a Kubernetes cluster
[preflight] This might take a minute or two, depending on the speed of your internet connection
[preflight] You can also perform this action in beforehand using 'kubeadm config images pull'
W0406 22:09:07.167188    5152 checks.go:835] detected that the sandbox image "registry.k8s.io/pause:3.6" of the container runtime is inconsistent with that used by kubeadm. It is recommended that using "registry.aliyuncs.com/google_containers/pause:3.9" as the CRI sandbox image.
[certs] Using certificateDir folder "/etc/kubernetes/pki"
[certs] Generating "ca" certificate and key
[certs] Generating "apiserver" certificate and key
[certs] apiserver serving cert is signed for DNS names [k8s-node1 kubernetes kubernetes.default kubernetes.default.svc kubernetes.default.svc.cluster.local] and IPs [10.96.0.1 192.168.133.129]
[certs] Generating "apiserver-kubelet-client" certificate and key
[certs] Generating "front-proxy-ca" certificate and key
[certs] Generating "front-proxy-client" certificate and key
[certs] Generating "etcd/ca" certificate and key
[certs] Generating "etcd/server" certificate and key
[certs] etcd/server serving cert is signed for DNS names [k8s-node1 localhost] and IPs [192.168.133.129 127.0.0.1 ::1]
[certs] Generating "etcd/peer" certificate and key
[certs] etcd/peer serving cert is signed for DNS names [k8s-node1 localhost] and IPs [192.168.133.129 127.0.0.1 ::1]
[certs] Generating "etcd/healthcheck-client" certificate and key
[certs] Generating "apiserver-etcd-client" certificate and key
[certs] Generating "sa" key and public key
[kubeconfig] Using kubeconfig folder "/etc/kubernetes"
[kubeconfig] Writing "admin.conf" kubeconfig file
[kubeconfig] Writing "kubelet.conf" kubeconfig file
[kubeconfig] Writing "controller-manager.conf" kubeconfig file
[kubeconfig] Writing "scheduler.conf" kubeconfig file
[etcd] Creating static Pod manifest for local etcd in "/etc/kubernetes/manifests"
[control-plane] Using manifest folder "/etc/kubernetes/manifests"
[control-plane] Creating static Pod manifest for "kube-apiserver"
[control-plane] Creating static Pod manifest for "kube-controller-manager"
[control-plane] Creating static Pod manifest for "kube-scheduler"
[kubelet-start] Writing kubelet environment file with flags to file "/var/lib/kubelet/kubeadm-flags.env"
[kubelet-start] Writing kubelet configuration to file "/var/lib/kubelet/config.yaml"
[kubelet-start] Starting the kubelet
[wait-control-plane] Waiting for the kubelet to boot up the control plane as static Pods from directory "/etc/kubernetes/manifests". This can take up to 4m0s
[kubelet-check] Initial timeout of 40s passed.

Unfortunately, an error has occurred:
        timed out waiting for the condition

This error is likely caused by:
        - The kubelet is not running
        - The kubelet is unhealthy due to a misconfiguration of the node in some way (required cgroups disabled)

If you are on a systemd-powered system, you can try to troubleshoot the error with the following commands:
        - 'systemctl status kubelet'
        - 'journalctl -xeu kubelet'

Additionally, a control plane component may have crashed or exited when started by the container runtime.
To troubleshoot, list all containers using your preferred container runtimes CLI.
Here is one example how you may list all running Kubernetes containers by using crictl:
        - 'crictl --runtime-endpoint unix:///var/run/containerd/containerd.sock ps -a | grep kube | grep -v pause'
        Once you have found the failing container, you can inspect its logs with:
        - 'crictl --runtime-endpoint unix:///var/run/containerd/containerd.sock logs CONTAINERID'
error execution phase wait-control-plane: couldn't initialize a Kubernetes cluster
To see the stack trace of this error execute with --v=5 or higher
```

还是靠豆包解决：
```shell
sed -i 's|sandbox_image = "registry.k8s.io/pause:3.6"|sandbox_image = "registry.aliyuncs.com/google_containers/pause:3.9"|' /etc/containerd/config.toml
systemctl restart containerd

# 强制拉取正确镜像
kubeadm config images pull --image-repository registry.aliyuncs.com/google_containers

# 重置 kubelet
systemctl stop kubelet
rm -rf /var/lib/kubelet/*
systemctl start kubelet

# 重置 kubeadm（清理失败环境）
kubeadm reset -f

# 重新执行初始化
kubeadm init \
  --apiserver-advertise-address=192.168.133.129 \
  --image-repository registry.aliyuncs.com/google_containers \
  --kubernetes-version v1.28.0 \
  --service-cidr=10.96.0.0/12 \
  --pod-network-cidr=10.244.0.0/16
```

出现Your Kubernetes control-plane has initialized successfully!就表示成功。

接着执行：
```shell
# 创建一个存放 K8s 配置文件的目录
mkdir -p $HOME/.kube
# 把系统管理员的集群认证文件复制到当前用户目录（不执行这行 → kubectl 无法连接集群）
cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
# 把配置文件的权限改成当前用户所有
chown $(id -u):$(id -g) $HOME/.kube/config
```

接着安装网络插件
```shell
kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml
```

装完等 30 秒，查看节点状态
```shell
kubectl get nodes
```

结果not ready
```shell
[root@k8s-node1 ~]# kubectl get nodes
NAME        STATUS     ROLES           AGE   VERSION
k8s-node1   NotReady   control-plane   10m   v1.28.0
```

豆包继续带我飞
第一步：看节点为什么 NotReady

```shell
kubectl describe node k8s-node1
```

翻到最下面 Conditions 部分，看看里面写的红色错误
```shell
container runtime network not ready: NetworkReady=false reason:NetworkPluginNotReady message:Network plugin returns error: cni plugin not initialized
```

修复命令：
```shell
# 1. 创建 CNI 目录（containerd 找不到目录导致的！）
mkdir -p /etc/cni/net.d

# 2. 重启 containerd
systemctl restart containerd

# 3. 重启 kubelet
systemctl restart kubelet

# 4. 删除旧的 flannel pod，让它自动重建
kubectl delete pod -n kube-system -l app=flannel
```

终于正常了：
```shell
[root@k8s-node1 ~]# kubectl get nodes
NAME        STATUS   ROLES           AGE   VERSION
k8s-node1   Ready    control-plane   15m   v1.28.0
```

## 将微服务部署到k8s

在虚拟机中创建gateway目录，把jar包、依赖包都放进入，然后创建gateway-deploy.yaml
```shell
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      # 直接用官方JDK镜像，不用你自己构建！
      containers:
      - name: gateway
        image: openjdk:17-jdk-slim
        workingDir: /app
        command: ["java", "-jar", "gateway.jar"]
        ports:
        - containerPort: 8080

        # 把你服务器上的 jar 和 yml 直接挂载进容器！
        volumeMounts:
        - name: app-files
          mountPath: /app

      volumes:
      - name: app-files
        hostPath:
          # 你服务器 jar 所在的路径！
          path: /root/gateway
---
apiVersion: v1
kind: Service
metadata:
  name: gateway
spec:
  type: NodePort
  ports:
  - port: 8080
    nodePort: 30080
  selector:
    app: gateway
```

接着启动gateway
```shell
kubectl apply -f gateway-deploy.yaml
```

检查是否启动成功
```shell
kubectl get pods
```
只要出现 READY 1/1 → 你的网关服务已经跑在 K8s 上了！

windows上使用浏览器访问
```shell
http://192.168.133.129:30080
```

一般都没这么顺利，报错：
```shell
[root@k8s-node1 gateway]# kubectl get pods
NAME                       READY   STATUS    RESTARTS   AGE
gateway-6cfcd4d779-9h8jj   0/1     Pending   0          7m23s
```

查看原因：
```shell
[root@k8s-node1 gateway]# kubectl describe pod gateway | grep -A 20 Events
Events:
  Type     Reason            Age   From               Message
  ----     ------            ----  ----               -------
  Warning  FailedScheduling  62s   default-scheduler  0/1 nodes are available: 1 node(s) had untolerated taint {node-role.kubernetes.io/control-plane: }. preemption: 0/1 nodes are available: 1 Preemption is not helpful for scheduling..
```
逐句翻译
0/1 nodes are available直译：0 个节点可用，总共有 1 个节点人话：没有机器能用来运行你的 Pod
1 node(s) had untolerated taint
taint：污点（K8s 术语，意思是 “这个节点打上了标记，不让普通 Pod 跑上来”）
untolerated：无法容忍、不接受
直译：1 个节点带有不能容忍的污点
人话：你的主节点被加了限制，不让跑业务服务
{node-role.kubernetes.io/control-plane: }直译：节点角色是控制面（master 节点）人话：这是管理集群的主节点，默认不让跑普通微服务
preemption: 0/1 nodes are available直译：抢占模式：0/1 节点可用人话：系统尝试 “插队” 也没用
Preemption is not helpful for scheduling直译：抢占对调度没有帮助人话：实在没机器能跑你这个 Pod

整段合起来一句人话总结：
你只有一台主节点，K8s 默认规定：主节点只负责管理集群，不允许跑咱们自己的微服务，所以 Pod 一直卡在 Pending，启动不了。

去掉主节点的 “禁止运行普通 Pod” 限制，允许在这台机器上跑微服务。
```shell
kubectl taint nodes --all node-role.kubernetes.io/control-plane-
```

这次显示Successfully assigned default/gateway-d8f979f55-jt4sj to k8s-node1，但又有新报错
```shell
[root@k8s-node1 gateway]# kubectl describe pod gateway | grep -A 20 Events
Events:
  Type     Reason                  Age                From               Message
  ----     ------                  ----               ----               -------
  Warning  FailedScheduling        81s (x3 over 11m)  default-scheduler  0/1 nodes are available: 1 node(s) had untolerated taint {node-role.kubernetes.io/control-plane: }. preemption: 0/1 nodes are available: 1 Preemption is not helpful for scheduling..
  Normal   Scheduled               19s                default-scheduler  Successfully assigned default/gateway-d8f979f55-jt4sj to k8s-node1
  Warning  FailedCreatePodSandBox  18s                kubelet            Failed to create pod sandbox: rpc error: code = Unknown desc = failed to setup network for sandbox "d94cb20553f83fe848c4e185f6c3dfc49c66efdcb0da88fb1a3b9db234a22e98": plugin type="loopback" failed (add): failed to find plugin "loopback" in path [/opt/cni/bin]
  Normal   SandboxChanged          4s (x2 over 18s)   kubelet            Pod sandbox changed, it will be killed and re-created.
```

K8s 要给你的微服务创建网络，但是去 /opt/cni/bin 文件夹里找网络插件，结果文件夹是空的！找不到 loopback 这个插件！
```shell
# 1. 创建缺失的CNI插件目录
mkdir -p /opt/cni/bin

# 2. 下载安装缺失的网络插件（1秒搞定）
yum install -y containernetworking-plugins

# 3. 重启服务
systemctl restart kubelet containerd
```

执行完了还是报一样的错，并且网上查别人的/opt/cni/bin有好多个文件，我的只有flannel。
最后还是靠豆包：
我彻底破案了！！！你这是麒麟 V10 超级典型的 BUG！
我直接告诉你最残酷、最真实的原因：
你的系统 containernetworking-plugins 确实安装了
但是！它把文件装到了 /usr/libexec/cni/ 下面，根本没放到 /opt/cni/bin！
所以你 /opt/cni/bin 里 永远只有 flannel，没有 loopback！
第一步：找到真正的 CNI 插件位置（系统藏起来了）
```shell
ls /usr/libexec/cni/
```

第二步：直接把它们全部复制到 /opt/cni/bin（永久解决）
```shell
cp /usr/libexec/cni/* /opt/cni/bin/
```

重启服务
```shell
systemctl restart containerd kubelet
kubectl delete pod --all
```

这次又前进了一步：
```shell
[root@k8s-node1 bin]# kubectl get pods
NAME                      READY   STATUS             RESTARTS   AGE
gateway-d8f979f55-gtjrc   0/1     ImagePullBackOff   0          15m
```

报错很明显：
```shell
[root@k8s-node1 bin]# kubectl describe pod gateway | grep -A 20 Events
Events:
  Type     Reason     Age                 From               Message
  ----     ------     ----                ----               -------
  Normal   Scheduled  16m                 default-scheduler  Successfully assigned default/gateway-d8f979f55-gtjrc to k8s-node1
  Warning  Failed     15m                 kubelet            Failed to pull image "eclipse-temurin:17-jre": failed to pull and unpack image "docker.io/library/eclipse-temurin:17-jre": failed to resolve reference "docker.io/library/eclipse-temurin:17-jre": failed to do request: Head "https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/17-jre": dial tcp 199.96.63.177:443: connect: connection refused
```

Docker 镜像仓库国内被墙，拉不到 eclipse-temurin:17-jre
解决办法就是用新的deploy.yml，指定阿里云的源。（豆包一秒解决，记得要用kubectl apply -f deploy.yml重新部署）
一键清空gateway资源：
```shell
kubectl delete deployment gateway
kubectl delete service gateway
kubectl delete pod --all --force
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

