# Docker 常见命令速查

---

## 1. 镜像（Image）相关

| 命令 | 说明 |
|------|------|
| `docker images` / `docker image ls` | 列出本地所有镜像 |
| `docker pull <镜像名>:<标签>` | 从仓库拉取镜像（如 `docker pull nginx:latest`） |
| `docker push <镜像名>:<标签>` | 推送镜像到仓库 |
| `docker build -t <名称>:<标签> .` | 根据当前目录 Dockerfile 构建镜像 |
| `docker rmi <镜像ID或名>` | 删除指定镜像 |
| `docker image prune` | 删除所有未被使用的镜像 |
| `docker tag <原镜像> <新镜像名>:<标签>` | 给镜像打标签 |
| `docker history <镜像名>` | 查看镜像构建历史 |
| `docker save -o <文件.tar> <镜像名>` | 将镜像保存为 tar 文件 |
| `docker load -i <文件.tar>` | 从 tar 文件加载镜像 |

---

## 2. 容器（Container）相关

### 运行与停止

| 命令 | 说明 |
|------|------|
| `docker run <镜像名>` | 创建并启动容器 |
| `docker run -it <镜像> /bin/bash` | 交互模式启动并进入容器 |
| `docker run -d -p 8080:80 --name mynginx nginx` | 后台运行，端口映射，指定容器名 |
| `docker run -v /宿主机路径:/容器路径 <镜像>` | 挂载数据卷 |
| `docker run --rm <镜像>` | 容器退出后自动删除 |
| `docker start <容器ID/名>` | 启动已停止的容器 |
| `docker stop <容器ID/名>` | 停止容器 |
| `docker restart <容器ID/名>` | 重启容器 |
| `docker pause <容器ID/名>` | 暂停容器 |
| `docker unpause <容器ID/名>` | 恢复容器 |

### 查看与管理

| 命令 | 说明 |
|------|------|
| `docker ps` | 查看正在运行的容器 |
| `docker ps -a` | 查看所有容器（含已停止的） |
| `docker ps -q` | 仅显示容器 ID |
| `docker logs <容器ID/名>` | 查看容器日志 |
| `docker logs -f <容器ID/名>` | 实时跟踪日志输出 |
| `docker exec -it <容器ID> /bin/bash` | 进入正在运行的容器 |
| `docker inspect <容器ID/名>` | 查看容器详细信息（IP、挂载等） |
| `docker top <容器ID>` | 查看容器内运行的进程 |
| `docker stats` | 实时查看所有容器资源占用（CPU/内存） |
| `docker cp <容器ID>:<容器内路径> <宿主机路径>` | 从容器复制文件到宿主机 |
| `docker cp <宿主机路径> <容器ID>:<容器内路径>` | 从宿主机复制文件到容器 |
| `docker rm <容器ID/名>` | 删除已停止的容器 |
| `docker rm -f <容器ID/名>` | 强制删除运行中的容器 |
| `docker container prune` | 删除所有已停止的容器 |

---

## 3. 网络（Network）相关

| 命令 | 说明 |
|------|------|
| `docker network ls` | 列出所有网络 |
| `docker network create <网络名>` | 创建自定义网络 |
| `docker network inspect <网络名>` | 查看网络详情 |
| `docker network connect <网络名> <容器名>` | 将容器连接到网络 |
| `docker network disconnect <网络名> <容器名>` | 断开容器与网络的连接 |
| `docker network rm <网络名>` | 删除网络 |
| `docker network prune` | 删除所有未使用的网络 |

---

## 4. 数据卷（Volume）相关

| 命令 | 说明 |
|------|------|
| `docker volume ls` | 列出所有数据卷 |
| `docker volume create <卷名>` | 创建数据卷 |
| `docker volume inspect <卷名>` | 查看数据卷详情 |
| `docker volume rm <卷名>` | 删除数据卷 |
| `docker volume prune` | 删除所有未被使用的数据卷 |

---

## 5. Docker Compose 相关

| 命令 | 说明 |
|------|------|
| `docker compose up` | 根据 compose.yml 启动所有服务 |
| `docker compose up -d` | 后台启动 |
| `docker compose down` | 停止并移除所有容器、网络 |
| `docker compose ps` | 查看 compose 项目中的容器状态 |
| `docker compose logs` | 查看 compose 服务日志 |
| `docker compose logs -f` | 实时跟踪 compose 日志 |
| `docker compose build` | 构建 compose 中定义的服务镜像 |
| `docker compose restart` | 重启 compose 中的服务 |
| `docker compose stop` | 停止 compose 中的服务 |
| `docker compose exec <服务名> <命令>` | 在指定服务容器中执行命令 |

---

## 6. 系统 & 清理

| 命令 | 说明 |
|------|------|
| `docker info` | 查看 Docker 系统信息 |
| `docker version` | 查看 Docker 版本 |
| `docker system df` | 查看磁盘占用情况 |
| `docker system prune` | 删除所有停止的容器、未用的网络、悬空镜像 |
| `docker system prune -a` | 彻底清理（含未被容器引用的所有镜像）⚠️ |

---

## 7. 常用参数速记
`docker run` 最常用的参数组合：
```shell
docker run -d \
-p 8080:80 \ # 端口映射：宿主机:容器
-v /data:/data \ # 数据卷挂载
--name myapp \ # 指定容器名
--restart always \ # 总是自动重启
--network mynet \ # 指定网络
nginx:latest
```

---

## 8. 快速记忆口诀

> **镜像**：pull → build → tag → push → rmi  
> **容器**：run → ps → exec → logs → stop → rm  
> **清理**：prune 全家桶（image / container / volume / network）