# 镜像拉取代理设置

## docker代理设置

不要用这些国内加速器，访问docker官方仓库的时候会报403 Forbidden
```text
  "registry-mirrors": [
    "https://uqfvy2u3.mirror.aliyuncs.com",
    "https://mirror.baidubce.com",
    "https://docker.mirrors.ustc.edu.cn",
    "https://docker.m.daocloud.io"
  ]
```

registry-mirrors全部删除，给docker-desktop设置代理，注意不是127.0.0.1，
而是http://172.31.144.1:7897。位置在设置->resource->Proxies->Manual configuration

## 虚拟机设置代理

注意：不能在/etc/profile中设置永久代理，那样kubelet、calico‑node、kube‑proxy都会走代理，
会导致k8s集群内部网络出问题。只能是执行命令的shell设置临时代理，同时三台节点的container都需要
设置代理，以便每个节点都可以拉取镜像。

1、Clash设置，开启局域网连接。

2、需要执行helm命令的节点，在shell设置代理。
```shell
# http/https代理
export http_proxy=http://192.168.133.1:7897
export https_proxy=http://192.168.133.1:7897
# 集群内部无需代理
export no_proxy=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16
```

3、windows防火墙允许7897

高级设置->新建规则，新建一条入网端口允许的规则

4、测试
```shell
curl -I https://hub.docker.com
```

5、设置containerd 镜像拉取代理

k8s 容器运行时 containerd 不会读取 linux 环境变量，三台节点都需要配置代理
```shell
mkdir -p /etc/systemd/system/containerd.service.d
vim /etc/systemd/system/containerd.service.d/http-proxy.conf
```

写入下面的内容
```shell
[Service]
Environment="HTTP_PROXY=http://192.168.133.1:7897"
Environment="HTTPS_PROXY=http://192.168.133.1:7897"
Environment="NO_PROXY=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.svc.cluster.local"
```

重载服务、重启 containerd
```shell
systemctl daemon-reload
systemctl restart containerd
```


可以用命令手动下载镜像
```shell
export CRICTL_CONFIG=/etc/crictl.yaml
echo 'runtime-endpoint: unix:///run/containerd/containerd.sock' > /etc/crictl.yaml
crictl --runtime-endpoint unix:///run/containerd/containerd.sock pull goharbor/harbor-db:v2.14.1
```

取消shell中设置的代理
```shell
# 已经打开的shell，需要执行unset命令
unset http_proxy
unset https_proxy
unset no_proxy
```
