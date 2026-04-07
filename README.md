# 随笔

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