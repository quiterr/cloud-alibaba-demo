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

## 将slave节点加入k8s集群
以下是安装k8s之前的初始化操作

```shell
#关闭防火墙
systemctl stop firewalld
systemctl disable firewalld

#关闭selinux
vim /etc/selinux/config
#设置SELINUX=disabled
reboot

#设置虚拟机IP固定
vim /etc/sysconfig/network-scripts/ifcfg-ens33
#加上如下配置
IPADDR=192.168.133.182  #按需修改
NETMASK=255.255.255.0
GATEWAY=192.168.133.2  #在vmware编辑->虚拟网络编辑器 可以查看网关地址
DNS1=223.5.5.5   
DNS2=114.114.114.114

#关闭swap，内存够久不需要swap
vim /etc/fstab
#注释掉下面这行
# /dev/mapper/klas-swap swap swap defaults 0 0

#设置主机名和IP
hostnamectl set-hostname k8s-node1
vim /etc/hosts
#在文件中加两行
192.168.133.182 k8s-node1
192.168.133.184 k8s-node2

#加载内核模块
cat <<EOF | tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

#网络参数
cat <<EOF | tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sysctl --system
```

开启 ip_forward
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

接着是安装container和k8s，一直到下面这条命令执行成功：
```shell
systemctl enable --now kubelet
```

安装完成后可以加入k8s集群。

#slave节点加入k8s集群
```shell
#在master节点创建token，并生成join命令
kubeadm token create --print-join-command

#生成出来的命令像这样，复制到slave节点上执行
kubeadm join 192.168.133.129:6443 --token fqnk6b.ez169hd02hb1gguz --discovery-token-ca-cert-hash sha256:dbf8d9919e131f47b72816546c7ff1ff0f24887a473d2a6217f04b872cd071c9

#加入成功的打印
This node has joined the cluster:
* Certificate signing request was sent to apiserver and a response was received.
* The Kubelet was informed of the new secure connection details.

Run 'kubectl get nodes' on the control-plane to see this node join the cluster.

#此时slave节点还是not ready状态，安装 Calico 网络插件（必须装，节点才会 Ready）
#在master节点执行
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.26.4/manifests/calico.yaml

#上面的命令执行成功了，但还是not ready状态
#通过下述命令可以看到calico镜像拉取失败了
kubectl get pods -n kube-system -o wide

#豆包推荐配置 containerd 全局镜像加速（必做，根治以后所有镜像拉取失败）
#每台节点都要做
#备份当前可用配置
cp /etc/containerd/config.toml /etc/containerd/config.toml.bak

# 开启SystemdCgroup
sed -i 's/SystemdCgroup \= false/SystemdCgroup \= true/g' /etc/containerd/config.toml

# 插入DaoCloud镜像源
sed -i '/\[plugins."io.containerd.grpc.v1.cri".registry.mirrors\]/a \
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]\
      endpoint = ["https://docker.m.daocloud.io"]\
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."quay.io"]\
      endpoint = ["https://docker.m.daocloud.io"]\
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."registry.k8s.io"]\
      endpoint = ["https://k8s.m.daocloud.io"]' /etc/containerd/config.toml
# 重启containerd
systemctl daemon-reload
systemctl restart containerd
systemctl status containerd

#重新拉取镜像，下载镜像有点慢，可能要等七八分钟才行
kubectl delete -f https://raw.githubusercontent.com/projectcalico/calico/v3.26.4/manifests/calico.yaml
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.26.4/manifests/calico.yaml

#镜像下载正常流转
ImagePullBackOff → Pulling → Pulled → Init:1/3 → 1/1 Running

#查看pod的运行状态，calico都是Running状态就正常了
kubectl get pods -n kube-system -o wide

#查看slave节点是否成功加入k8s集群
[root@k8s-node1 ~]# kubectl get nodes
NAME        STATUS   ROLES           AGE   VERSION
k8s-node1   Ready    control-plane   72d   v1.28.0
k8s-node2   Ready    <none>          23h   v1.28.0

#在slave节点执行kubectl
#在master节点执行
# 打包管理员kube配置
tar -zcvf kube-config.tar.gz /root/.kube
# 传到k8s-node2
scp kube-config.tar.gz root@192.168.133.184:/root

#在slave节点执行
cd /root
tar -zxvf kube-config.tar.gz
# 验证命令
kubectl get nodes
```

## 常用排错命令
```shell
#查看节点状态
kubectl get nodes

#查看集群整体健康
kubectl get cs
kubectl get pods -n kube-system

#节点加入失败排错（node 节点执行）
# 查看kubelet日志
journalctl -u kubelet -f
# 重置节点重新加入（加入失败清理环境）
kubeadm reset -f

#查看某个pod的状态
kubectl describe pod -n kube-system calico-node-b85vk

#重新部署某个节点上的指定pod
kubectl delete pod calico-node-w65f8 -n kube-system

#重新滚动部署所有节点上的指定pod
kubectl rollout restart daemonset calico-node -n kube-system
```

## 在k8s集群中部署nginx测试

到这里，已经学习了node、pod、service三个基本概念了。

```shell
 # 创建 deployment，副本数2
kubectl create deployment nginx-demo --image=nginx:1.24-alpine --replicas=2
# 先创建NodePort服务，自动随机分配 30000-32767 区间 nodePort
kubectl expose deployment nginx-demo --type=NodePort --port=80 --target-port=80
# 通过配置查看暴露的service端口
kubectl edit svc nginx-demo
# 也可以通过命令查看service分配端口
kubectl get svc
#查看部署状态
kubectl get deploy
#验证是否三个节点都部署了nginx
http://192.168.133.129:31010
http://192.168.133.184:31010
http://192.168.133.185:31010
```

## kubectl常用命令
命令可以分为几大类别：
- 初级基础命令，包括create、expose、run、set
- 中级基础命令，包括explain、get、edit、delete
- 部署命令，包括rollout、scale、autoscale
- 集群管理命令，包括certificate、cluster-info等
- 故障排查和调试命令，包括describe、logs等
- 高级命令，包括diff、apply等
- 设置命令，包括label、annotate、completion
- 其他命令，包括config等命令

用法：`kubectl [flags] [options]`

帮助命令：
```shell
kubectl help
kubectl help scale
```

集群 & 节点基础查看
```shell
# 1. 查看所有节点（你最常用）
kubectl get nodes
# 查看节点详细IP、系统版本
kubectl get nodes -o wide
# 查看节点完整详情（污点、标签、事件）
kubectl describe node k8s-node3

# 2. 查看集群版本
kubectl version
# 仅看服务端apiserver版本
kubectl version --short

# 3. 查看集群所有命名空间
kubectl get ns
# 查看kube-system系统命名空间所有资源
kubectl get all -n kube-system
```

Pod 核心实操
```shell
# 1. 查看默认命名空间所有pod
kubectl get pods
# 带IP、节点、镜像详细信息
kubectl get pods -o wide

# 2. 查看指定pod详情（看报错事件，你calico报错就用这个）
kubectl describe pod calico-node-w65f8 -n kube-system

# 3. 实时滚动刷新pod状态（排障神器-w=watch）
kubectl get pods -w

# 4. 查看pod日志（容器输出，定位启动失败原因）
# 查看nginx所有日志
kubectl logs deployment/nginx-demo
# 实时持续打印日志
kubectl logs -f deployment/nginx-demo
# 查看之前崩溃的历史日志
kubectl logs --previous calico-node-w65f8 -n kube-system

# 5. 进入pod容器内部调试
kubectl exec -it deployment/nginx-demo -- sh
# 多容器pod指定容器进入
kubectl exec -it calico-node-w65f8 -n kube-system --container calico-node -- bash

# 6. 临时测试pod网络（快速起一个测试容器）
kubectl run test-pod --image=nginx --rm -it -- curl nginx-demo:80
```

部署管理
```shell
# 1. 查看所有部署
kubectl get deploy
# 详情
kubectl describe deploy nginx-demo

# 2. 创建部署（一行快速创建nginx）
kubectl create deployment nginx-demo --image=nginx:alpine --replicas=2

# 3. 扩容/缩容副本
# 扩容到3个pod
kubectl scale deployment nginx-demo --replicas=3
# 缩容到1个pod
kubectl scale deployment nginx-demo --replicas=1

# 4. 滚动重启部署（更新pod，解决缓存/配置问题）
kubectl rollout restart deployment nginx-demo
# 查看重启滚动进度
kubectl rollout status deployment nginx-demo

# 5. 查看部署历史版本（回滚用）
kubectl rollout history deployment nginx-demo
# 回滚到上一版本
kubectl rollout undo deployment nginx-demo

# 获取deploy nginx，输出yaml文件
kubectl get deployment nginx-demo -o yaml > nginx-deploy.yaml
```

服务管理
```shell
# 1. 查看所有service
kubectl get svc
# 详细信息
kubectl describe svc nginx-demo

# 2. 根据deployment快速创建service（你之前执行的命令）
kubectl expose deployment nginx-demo --type=NodePort --port=80 --target-port=80

# 3. 编辑service（手动固定nodePort端口）
kubectl edit svc nginx-demo

# 4. 集群内部访问测试（pod里调用service名称）
kubectl run curl-test --image=curlimages/curl --rm -it -- curl nginx-demo:80
```

YAML 资源全生命周期
```shell
# 1. 根据yaml文件创建/更新资源
kubectl apply -f nginx.yaml

# 2. 导出现有资源为yaml（备份用）
kubectl get deploy nginx-demo -o yaml > nginx-deploy-bak.yaml

# 3. 校验yaml语法不实际创建（预校验）
kubectl apply -f nginx.yaml --dry-run=client

# 4. 删除yaml定义的全部资源
kubectl delete -f nginx.yaml
```

标签、筛选资源
```shell
# 给节点打标签
kubectl label node k8s-node3 env=dev
# 给pod打标签
kubectl label pod nginx-demo-xxxxxx test=true

# 根据标签筛选pod（只查nginx）
kubectl get pods -l app=nginx
# 根据标签批量删除pod
kubectl delete pods -l app=nginx
```

删除清理资源
```shell
# 删除单个pod（deployment会自动重建新pod）
kubectl delete pod calico-node-w65f8 -n kube-system
# 强制删除卡死Terminating的pod
kubectl delete pod calico-node-w65f8 -n kube-system --force --grace-period=0

# 删除deployment连带所有pod
kubectl delete deployment nginx-demo
# 删除service
kubectl delete svc nginx-demo

# 删除命名空间下所有资源（慎用！）
kubectl delete all -n test-ns
```

网络 & Calico 排障专属
```shell
# 查看calico集群信息CRD
kubectl get clusterinformations.crd.projectcalico.org

# 打补丁给calico-node权限（你之前修复Unauthorized命令）
kubectl patch clusterrole calico-node --type json -p '[
{
  "op": "add",
  "path": "/rules/-",
  "value": {
    "apiGroups": ["crd.projectcalico.org"],
    "resources": ["clusterinformations"],
    "verbs": ["get","list","watch"]
  }
}]'

# 重启所有calico-node
kubectl rollout restart daemonset calico-node -n kube-system
# 查看daemonset（calico是daemonset类型）
kubectl get ds -n kube-system
```

kubeconfig 客户端排障
```shell
# 查看当前kubectl使用的集群上下文
kubectl config get-contexts
# 查看当前生效配置
kubectl config view
# 指定配置文件执行命令（排查配置路径错误）
kubectl --kubeconfig=/root/.kube/config get nodes
```


