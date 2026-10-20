# Docker 部署指南（SSH / FinalShell）

本文说明：使用 SSH 客户端连接 Linux 服务器，通过 **Docker Compose** 部署本项目；可选 **离线镜像**（本机 `docker save` 后上传），减轻服务器拉镜像的压力。

---

## 私密信息与合规提示（必读）

以下内容属于**敏感信息**，请勿写入对外分享的文档、截图、聊天记录或提交到 Git；本文一律用**文字占位**表示，请在你自己的服务器与本机 `.env` 中填写真实值：

| 类型 | 说明 |
|------|------|
| **服务器地址** | 公网 IP、域名、内网 IP —— 勿泄露给他人除非必要 |
| **SSH** | 端口（常为 22）、用户名、`root` 或其它账号的**密码**、**私钥文件** —— 私钥切勿外传 |
| **云平台** | 控制台登录方式、API Token、安全组规则中与业务无关的细节 |
| **应用密钥** | `SPRING_DEEPSEEK_API_KEY` 及各类第三方 API Key —— 仅保存在服务器本地 `.env`，泄露后请**立即轮换** |
| **数据库与中间件** | MySQL root 密码、Redis 密码（若启用）、MinIO 访问密钥 —— `docker-compose.yml` 若含默认口令，**生产环境务必改为强密码**并避免把真实口令写进文档 |
| **`.env` 文件** | 加入 `.gitignore`，不要随仓库推送；备份时使用加密渠道 |

若本文档曾被填入真实 IP、密钥等，请先**替换为占位符**再分享，并已泄露的密钥**作废并重签**。

---

## 一、环境与假设

| 项目 | 说明 |
|------|------|
| 服务器系统 | Ubuntu（公网 IP 或域名请在你本地记录，勿写入对外文档） |
| 登录用户 | `root`（或其它具备 Docker 权限的用户） |
| SSH 工具 | FinalShell 或其它 SSH 客户端 |
| 本机 | 已安装 Docker Desktop（Windows），用于构建与导出镜像 |
| 项目结构 | 仓库根目录含 `docker-compose.yml`、`.env` |

默认对外端口：**前端 `18088`**，后端 **`8180`**（一般由前端 Nginx 反代 `/api`，浏览器主要访问 `18088`）。

---

## 二、SSH 连接服务器（FinalShell）

1. 打开 FinalShell，新建 SSH 连接。
2. 填写：
   - **主机**：服务器公网 IP（或域名）
   - **端口**：一般为 `22`
   - **用户名**：如 `root`
   - **密码**或 **密钥**：按云厂商提供的认证方式填写
3. 保存并连接；成功后终端提示类似：`root@hostname:~#`
4. 下方文件管理器可用于 **上传文件**（单个或多个文件）；不支持上传文件夹时，请将项目打成 **zip** 上传。

---

## 三、服务器端 Docker 自检（首次建议执行）

在 SSH 终端执行：

```bash
docker version
```

需同时看到 **Client** 与 **Server**，且 **OS/Arch** 为 `linux/amd64`（与本机构建镜像架构一致）。

```bash
systemctl status docker --no-pager
```

状态应为 **active (running)**。

```bash
docker compose version
```

需能输出版本号（如 Compose v2/v5 插件）。

磁盘建议预留足够空间（镜像与数据卷较大）：

```bash
df -h /
docker system df
```

---

## 四、配置 `.env`（部署前必做）

在**项目根目录**（与 `docker-compose.yml` 同级）维护 `.env`。浏览器通过 **公网 IP + 端口** 访问时，至少修改或确认（以下值为**占位说明**，请替换为你自己的地址与密钥，且勿将填好真实密钥的文件提交仓库）：

```env
# 用户浏览器访问前端的根地址，无末尾斜杠（将「你的公网IP」换成真实 IP 或域名）
FRONTEND_PUBLIC_BASE_URL=http://你的公网IP:18088

# 后端 CORS：允许从这些 Origin 调用 API（逗号分隔、建议无空格）
CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*,http://你的公网IP:*

# 可选：第三方 AI 等密钥 —— 仅在服务器本机填写，勿出现在文档或截图中
# SPRING_DEEPSEEK_API_KEY=在此处填写你的密钥，不要复制示例真实串
```

镜像标签若与仓库默认不一致，可在 `.env` 中覆盖（示例）：

```env
MYSQL_IMAGE=mysql:8.4
REDIS_IMAGE=redis:7.4
MINIO_IMAGE=minio/minio:RELEASE.2025-09-07T16-13-09Z
```

**说明：** 容器之间通信使用 Compose **服务名**（`mysql`、`redis`、`backend`、`minio` 等）。`docker-compose.yml` 已为后端配置 JDBC、Redis、MinIO 等指向这些服务名，一般无需再改 `application.yml` 里的 `localhost`；**浏览器与公网访问**主要靠 `FRONTEND_PUBLIC_BASE_URL`、`CORS_ALLOWED_ORIGIN_PATTERNS` 等环境变量。

仓库 **`.env.example`** 中还包含（可复制到 `.env` 后按需修改）：

- **`COMPOSE_PROJECT_NAME`**：Compose 工程名，用于稳定容器与网络前缀；更换解压目录时建议保持与首次部署一致。
- **构建基镜像**：`BACKEND_MAVEN_IMAGE`、`BACKEND_RUNTIME_IMAGE`、`FRONTEND_NODE_IMAGE`、`FRONTEND_NGINX_IMAGE`（对应 Compose 里 `build.args`）。
- **业务镜像名**：`BACKEND_IMAGE`、`FRONTEND_IMAGE`（对应各服务的 `image` 字段默认值可被覆盖）。

若未在 `.env` 中设置 `MYSQL_IMAGE` / `REDIS_IMAGE`，Compose **默认**分别为 **`mysql:8.0`**、**`redis:7.0`**（以仓库根目录 `docker-compose.yml` 为准）。离线部署执行 `docker save` 时，请以本机 `docker images` 中**实际存在的镜像名与标签**为准，不必与本文示例版本号完全一致。

### 4.1 外部数据卷（首次部署必读）

当前 `docker-compose.yml` 将 **`mysql_data`**、**`redis_data`**、**`minio_data`** 声明为 **`external: true`**。在**首次** `docker compose up` 之前，宿主机上必须已存在这三个卷，否则 Compose 会报错。默认名称如下（若 `.env` 中设置了 `MYSQL_VOLUME_NAME`、`REDIS_VOLUME_NAME`、`MINIO_VOLUME_NAME`，则创建与 Compose 使用**相同**名称的卷）：

```bash
docker volume create mysql_data
docker volume create redis_data
docker volume create minio_data
```

迁移旧数据时：用 `docker volume ls` 核对现有卷名，通过上述变量挂载到已有卷，或导入备份后再启动栈。

---

## 五、路径约定

| 用途 | 建议路径 |
|------|----------|
| 项目代码与 Compose | `/root/TimberSage`（或与 `docker-compose.yml` 所在目录一致） |
| 镜像离线包 `.tar` | 任意路径均可；可与项目同目录，加载后可删除以省空间 |

所有 `docker compose` 命令必须在 **`docker-compose.yml` 所在目录**执行，或使用 `docker compose -f /完整路径/docker-compose.yml`。

---

## 六、方案 A：离线部署（推荐弱网服务器）

适合：服务器访问 Docker Hub 慢或不稳定。

### 6.1 本机准备代码（可选压缩）

- 将整个项目打成 **`TimberSage.zip`**（内含 `docker-compose.yml`、`.env`、`backend`、`frontend` 等）。
- 若需减小体积，可排除 `frontend/node_modules`、`backend/target`（镜像构建会在容器内重新安装依赖）。

### 6.2 本机构建并导出镜像

在本机项目根目录（将路径换成你电脑上的实际路径，勿在公开场合粘贴含用户名目录的完整路径若你介意隐私）：

```bash
cd /path/to/TimberSage
docker compose pull
docker compose build
docker images
```

根据 `docker images` 中的实际名称导出（以你机器显示为准；不要依赖目录名推断镜像名）。下表标签仅为示例，**请替换为你 `docker compose pull` / `build` 后的真实镜像**（例如未改 `.env` 时 MySQL 常为 `mysql:8.0`）：

```bash
docker save -o mysql.tar mysql:8.0
docker save -o redis.tar redis:7.0
docker save -o minio.tar minio/minio:RELEASE.2025-09-07T16-13-09Z
# 下面两行把镜像名替换成你机器上实际看到的名称（常见形式为 <compose工程名>-backend / <compose工程名>-frontend）
docker save -o backend.tar <你的后端镜像名>:latest
docker save -o frontend.tar <你的前端镜像名>:latest
```

生成：`mysql.tar`、`redis.tar`、`minio.tar`、`backend.tar`、`frontend.tar`。

### 6.3 上传到服务器

使用 FinalShell 文件管理器上传：

- **`TimberSage.zip`**（若服务器尚无最新代码）
- 上述 **`*.tar`** 文件  

上传到 **`/root`** 或 **`/root/TimberSage`** 均可。

### 6.4 服务器解压项目（若上传的是 zip）

```bash
cd /root
unzip -o TimberSage.zip -d TimberSage
```

若解压后 `docker-compose.yml` 不在预期位置：

```bash
find /root -name docker-compose.yml
```

进入包含 `docker-compose.yml` 与 `.env` 的目录：

```bash
cd /root/TimberSage
```

### 6.5 加载镜像

若 `.tar` 与 `docker-compose.yml` 同在 `/root/TimberSage`：

```bash
cd /root/TimberSage
for f in mysql.tar redis.tar minio.tar backend.tar frontend.tar; do docker load -i "$f"; done
```

也可逐条执行 `docker load -i xxx.tar`。

验证：

```bash
docker images
```

### 6.6 启动服务

若尚未创建外部数据卷，请先执行 [4.1 外部数据卷](#41-外部数据卷首次部署必读) 中的 `docker volume create` 命令。

```bash
cd /root/TimberSage
export COMPOSE_PULL_POLICY=never
docker compose up -d
docker compose ps
```

查看日志：

```bash
docker compose logs --tail=100
```

### 6.7 加载成功后清理离线包（可选）

```bash
cd /root/TimberSage
rm -f mysql.tar redis.tar minio.tar backend.tar frontend.tar
```

---

## 七、方案 B：在线部署（服务器网络良好）

仅上传 **`TimberSage.zip`**（或直接 Git 克隆），解压后配置好 `.env`，并确保已创建 [4.1](#41-外部数据卷首次部署必读) 中的三个 **external** 数据卷，再执行：

```bash
cd /root/TimberSage
docker compose up -d --build
```

服务器将自行拉取基础镜像并构建；耗时与带宽取决于网络。

---

## 八、云安全组与防火墙

在云控制台 **安全组** 中放行（按需）：

| 端口 | 用途 |
|------|------|
| **18088** | 前端页面（必选） |
| **8180** | 后端直连调试（可选，生产可不放行） |
| **22** | SSH |

不建议对公网开放 **13307**（MySQL）、**6394**（Redis）、MinIO 映射端口，除非有明确运维需求。

若启用 `ufw`：

```bash
ufw allow 22/tcp
ufw allow 18088/tcp
ufw reload
```

---

## 九、验证访问

浏览器打开：

```text
http://你的公网IP:18088
```

若静态资源或登录接口异常，检查 `.env` 中 **`FRONTEND_PUBLIC_BASE_URL`** 与 **`CORS_ALLOWED_ORIGIN_PATTERNS`** 是否与浏览器地址栏一致。

---

## 十、常用运维命令

```bash
cd /root/TimberSage

# 查看状态
docker compose ps

# 跟随日志
docker compose logs -f --tail=200

# 停止 / 启动
docker compose stop
docker compose start

# 停止并删除容器（数据卷默认保留）
docker compose down

# 查看磁盘占用
docker system df
```

---

## 十一、常见问题

**1. `no configuration file provided: not found`**  
当前目录没有 `docker-compose.yml`。使用 `find` 定位后 `cd` 到正确目录，或使用 `-f` 指定文件路径。

**2. `pull access denied` / 拉镜像失败**  
改用离线方案；或检查镜像名称、登录镜像仓库、网络代理。

**3. CORS 或 MinIO 资源地址错误**  
核对 `.env` 中公网 URL；修改后执行 `docker compose up -d` 使后端容器重新创建（若仅改 env，可能需要 `docker compose up -d --force-recreate backend`）。

**4. 磁盘不足**  
删除无用镜像、`docker compose down` 后清理不再使用的卷（谨慎）、或扩容云盘。

**5. 端口被占用**  
修改 `docker-compose.yml` 中对应 `ports` 映射左侧宿主机端口，并同步防火墙与安全组。

**6. `volume ... not found` / external volume 不存在**  
当前 Compose 要求 `mysql_data`、`redis_data`、`minio_data` 为预先创建的 external 卷。按 [4.1](#41-外部数据卷首次部署必读) 执行 `docker volume create`，或检查 `.env` 中 `MYSQL_VOLUME_NAME` 等是否与已创建卷名一致。

---

## 十二、涉及的服务与端口映射摘要

与仓库内 `docker-compose.yml` 一致时：

| 服务 | 宿主机端口 → 容器 |
|------|-------------------|
| 前端（Nginx） | 18088 → 80 |
| 后端（Spring Boot） | 8180 → 8080 |
| MySQL | 13307 → 3306 |
| Redis | 6394 → 6379 |
| MinIO API / 控制台 | 19100 / 19101 |

---

## 十三、MySQL + MinIO 数据一并迁移（保证线上资源可用）

业务里：**MySQL** 保存模型路径、缩略图路径等元数据；**MinIO** 保存实际 `.glb`、图片等对象。两者需都迁到服务器，前端通过 Nginx `/minio` 反代访问的对象才能与库中记录一致。

### 1. 私密说明

- 备份文件（`.sql`、`.tar.gz`）可能含**业务与账号信息**，勿提交 Git、勿随意外发；生产环境 **MinIO / 数据库口令** 若与默认不同，下述命令中的账号密码请改为**你环境真实值**（勿写进对外文档）。

### 2. 本机导出 MySQL（简要）

在 **My机** 已运行 `docker compose` 且 `mysql` 容器正常时（数据库名见 `docker-compose.yml`），可用例如：

```bash
docker exec mysql sh -c "mysqldump -uroot -p'你的MySQL-root密码' --databases mortise_and_tenon_joint_library --single-transaction --routines --triggers --default-character-set=utf8mb4 --set-gtid-purged=OFF > /tmp/mortise_dump.sql"
docker cp mysql:/tmp/mortise_dump.sql ./mortise_dump.sql
```

将生成的 `mortise_dump.sql` 与下文 MinIO 包一并上传服务器。

### 3. 本机导出 MinIO（整卷 `/data`，含桶与元数据）

MinIO 官方镜像内常无 `tar`，建议用 **临时 Alpine 容器**挂载同一 **Docker 卷** 再打包。先查卷名（把 `minio` 换成你的容器名）：

```bash
docker inspect minio --format "{{json .Mounts}}"
```

记下 `Destination` 为 `/data` 的那条 **`Name`**（常见形如 `项目目录名_minio_data`）。

在项目根目录执行（把 `卷名` 换成上一步的名称；`-v "${PWD}:/out"` 在 CMD/Git Bash 中可将输出写到当前目录）：

```bash
docker run --rm -v 卷名:/data -v "${PWD}:/out" alpine:latest tar czf /out/minio_data.tar.gz -C /data .
```

得到 **`minio_data.tar.gz`**，用 FinalShell 上传到服务器上与 `docker-compose.yml` 同目录（或与 `mortise_dump.sql` 相同目录）。

**说明：** 后端对象存储默认桶名为 **`models`**（模型文件）与 **`thumbnails`**（缩略图），可通过配置项 `minio.bucket.models` / `minio.bucket.thumbnails` 覆盖（见 `MinioServiceImpl`）。整卷备份不依赖逐个桶枚举，还原后与备份来源环境的存储布局一致。

### 4. 服务器导入顺序建议

1. 先 **`docker compose up -d`** 跑起栈（首次会创建空卷）。
2. **导入 MySQL**（与上文「导出」对应的反向操作，在含 `mortise_dump.sql` 的目录）：

```bash
docker exec -i mysql mysql -uroot -p'你的MySQL-root密码' < mortise_dump.sql
```

3. **停机 MinIO 并覆盖对象存储卷**（会清空该卷内原有对象，**仅适合你确认要整块替换为空环境或整机迁移的场景**）：

```bash
cd /你的项目目录   # 含 docker-compose.yml
docker compose stop minio
```

再次用 `docker inspect minio --format "{{json .Mounts}}"`（在 **stop 后仍可 inspect 容器**，若你已 `compose down` 删了容器，可用 `docker volume ls` 找 `*_minio_data`）得到 **MinIO 的卷名**，记为 `卷名`，然后：

```bash
docker run --rm -v 卷名:/data alpine:latest sh -c "find /data -mindepth 1 -delete"
docker run --rm -v 卷名:/data -v "$(pwd):/backup" alpine:latest tar xzf /backup/minio_data.tar.gz -C /data
docker compose start minio
```

4. 确认 MinIO 起来后，再 **`docker compose restart backend`**（或 `up -d`），避免后端缓存了旧连接。

### 5. 验证

- MySQL：`SHOW TABLES` 及抽查业务表是否有数据。
- 浏览器打开前端，查看模型/缩略图是否加载；若 404，检查 `.env` 中 **`FRONTEND_PUBLIC_BASE_URL`** 是否与访问地址一致，以及 Nginx `/minio/` 反代是否正常。

---

## 十四、相关文档

- **数据库库名与表结构**：见仓库根目录 [`database_documentation.md`](./database_documentation.md)（与实体/JPA 同步）。

---

文档版本：与仓库根目录 `docker-compose.yml`、`.env.example` 配套；镜像标签与数据卷名以实际 `.env` 为准。最后修订：2026-05-06。
