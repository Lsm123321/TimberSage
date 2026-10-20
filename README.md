# 智筑云览（基于智能技术的 3D 古建筑展示平台）

## 项目简介

本项目是一个古建筑 3D 展示与智能导览平台，名称：**智筑云览（基于智能技术的 3D 古建筑展示平台）**。

- **前端**：Vue 3 + Vite + Element Plus + Three.js + ECharts + Pinia + Vue Router + Tailwind CSS v4（vue-bits 动效组件）
- **后端**：Spring Boot 3.2 + Java 17 + Spring Security(JWT) + Spring Data JPA + Redis + MinIO（Java SDK）
- **数据库**：MySQL 8.x
- **智能能力**：DeepSeek Chat Completions API（导览脚本/构件讲解）；智谱 AI（CogTTS 语音合成 / GLM-TTS-Clone 声音克隆）

## 功能特性

- 3D 模型展示与材质调整
- **四季动态场景**：春·樱雨 / 夏·晴空 / 秋·斜阳 / 冬·落雪 四套程序化环境——动态天空渐变、太阳光晕（位置/色温随季节变化）、漂移云层、阵风粒子（花瓣/落叶/雪花/光斑）、落地堆积、贴地雾带流动、程序化地面肌理，以及季节性生物（粉蝶/蜻蜓/雁阵/寒鸦）
- **AI 语音导览**：DeepSeek 生成导演级分章脚本（旁白 + 镜头 + 构件高亮 + 动作编排），支持环绕展示、模型爆炸分解、推近特写、拉远全景等运镜；播放条含打字机字幕、动作徽标、章节进度
- **语音合成（TTS）**：智谱 CogTTS 多音色为主，浏览器内置 speechSynthesis 自动降级；管理员可通过声音克隆（GLM-TTS-Clone）上传样本创建专属音色（管理后台 → 音色管理）
- 智能导览与讲解文案生成（DeepSeek）
- AI 问答流式响应（SSE；生产环境通过 Nginx 关闭代理缓冲以保证流式正常）
- 中国地图导览（搜索、点位、区域卡片、动态过渡）
- 模型管理与审核（编辑支持替换缩略图，旧图自动清理）
- 用户管理与角色权限
- 评论与站点反馈
- 数据统计与分析与管理后台（含音色管理）

## 环境要求

- **Docker**：Docker Compose v2（**MySQL / Redis / MinIO 基础服务统一由 Docker Compose 托管**，本地开发也直接复用，无需本机安装）
- **Git**
- **JDK 17+**（本地运行后端时需要）
- **Node.js**：建议 **20 LTS**（与前端 Docker 多阶段构建一致）；一般 **18+** 亦可

## 项目结构

```
TimberSage/
├── backend/                 # Spring Boot 后端
│   ├── src/
│   │   └── main/java/com/arch3d/
│   │       ├── controller/   # REST 接口（含 TourScriptController 导览脚本、TtsController 语音合成、AdminVoiceController 声音克隆）
│   │       ├── service/      # 业务（TourScriptService / TtsService / VoiceCloneService / SceneEnvironment 相关）
│   │       ├── client/       # DeepSeekClient 等
│   │       └── entity/       # JPA 实体（含 CustomVoice 克隆音色）
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                # Vue3 + Vite 前端
│   ├── src/
│   │   ├── components/      # ThreeScene.vue（3D 查看器）、StarBorder/Magnet/SpotlightCard（vue-bits 动效）
│   │   ├── composables/     # useSceneEnvironment（四季场景）、useAiTour（导览状态机）、useCameraFlight（运镜）、useSpeech（TTS 抽象）
│   │   └── views/admin/     # 管理后台（含 VoiceManage 音色管理）
│   ├── package.json
│   ├── vite.config.js
│   ├── Dockerfile
│   └── nginx.conf           # 生产镜像内 Nginx：/api、/minio、SSE
├── docker-compose.yml
├── .env.example             # Compose / 运行时环境变量示例
├── docs/
│   └── AI_TOUR.md           # AI 语音导览系统文档（脚本协议/接口/动作编排/声音克隆）
└── README.md
```

### 环境变量（AI 能力相关）

| 变量 | 用途 | 缺省行为 |
|---|---|---|
| `SPRING_DEEPSEEK_API_KEY` | DeepSeek 导览脚本 / 构件讲解 | 未配置时导览回退内置四章模板 |
| `ZHIPU_API_KEY` | 智谱 CogTTS 语音合成 + GLM-TTS-Clone 声音克隆 | 未配置或服务不可用时前端自动降级浏览器内置朗读 |

## 快速开始

### 1. 获取项目（克隆或解压）

如果你是从 Git 获取代码：

```bash
git clone <项目地址>
cd TimberSage
```

如果你是从别人发来的压缩包获取代码：

- 解压到一个**路径较短**、**无中文/特殊符号**的目录（Windows 上更稳）
- 用终端进入项目根目录（能看到 `backend/`、`frontend/`、`docker-compose.yml`、`.env.example`）

### 2. 把项目“压缩发给别人”后，对方如何在本地运行（分发指南）

#### 2.1 你打包压缩文件时要注意

- **不要打包依赖与构建产物**（体积大且不可移植）：
  - 前端：`frontend/node_modules/`、`frontend/dist/`
  - 后端：`backend/target/`
  - 其他：`.idea/`、`.vscode/`（可选）、系统缓存文件等
- **不要打包敏感信息**：
  - 不要把真实 `.env`、密钥、口令放进压缩包
  - 推荐只提供 `.env.example`，并在 README 写清楚需要填写哪些变量
- **建议一起附带**：
  - 本 README（包含运行步骤）
  - `.env.example`

#### 2.2 对方拿到压缩包后的通用步骤（建议写给交付对象）

1. 解压缩（推荐路径：`C:\dev\TimberSage` 这类短路径）
2. 安装运行环境（见上方「环境要求」）
3. 按下面「Docker Compose：首次初始化」章节创建数据卷（如需）并从 `.env.example` 生成 `.env`
4. 选择一种运行方式：
   - **方式 A：Docker Compose（推荐，最省事）**
   - **方式 B：本地开发（前端 Vite + 后端 Spring Boot）**

> 提示：如果对方机器没有 Docker，建议走「方式 B」或让对方安装 Docker Desktop。

### 3. Docker Compose：首次初始化（必读）

当前 `docker-compose.yml` 中 **MySQL / Redis / MinIO 的数据卷为 `external: true`**，`docker compose up` 前需要先创建同名数据卷（名称可通过环境变量覆盖，默认为下表）：

```bash
docker volume create mysql_data
docker volume create redis_data
docker volume create minio_data
```

然后复制环境变量模板并按需修改：

```bash
# macOS / Linux
cp .env.example .env

# Windows PowerShell
Copy-Item .env.example .env
```

`.env.example` 中重点说明：

- **`COMPOSE_PROJECT_NAME`**：用于固定 Compose 工程名，避免目录改名后数据卷/网络“对不上”而误以为数据丢失。
- **`FRONTEND_PUBLIC_BASE_URL`**：后端拼接 MinIO 资源公网地址时使用；容器场景下默认 `http://localhost:18088`，经前端 Nginx `/minio/` 反代 MinIO，保证与页面同源、避免 GLTF 等资源跨域失败。
- **`CORS_ALLOWED_ORIGIN_PATTERNS`**：通过公网 IP/域名访问前端时，必须把对应 `Origin` 模式加进来（逗号分隔、无空格），否则接口可能 CORS 403。
- **`SPRING_DEEPSEEK_API_KEY`**：DeepSeek API Key，生产环境务必注入，勿提交到仓库。

### 4. 配置敏感信息

**注意**：以下仅为说明，实际部署请使用强密码与密钥管理方案。

#### 后端 `backend/src/main/resources/application.yml`（本地开发）

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | 本地 MySQL 连接；默认库名 `mortise_and_tenon_joint_library` |
| `spring.deepseek.api-key` | 建议通过环境变量 `SPRING_DEEPSEEK_API_KEY` 注入 |
| `jwt.secret` | JWT 密钥，生产环境必须更换为足够随机的值 |
| `spring.minio.*` / MinIO 相关 | 本地默认连 `localhost:9000`；桶名由业务代码在启动时创建/维护（默认 `models`、`thumbnails`） |
| `app.map.compliance.*` | 地图合规文案、审图号、年份（前端地图页展示） |
| `app.cors.allowed-origin-patterns` | 与浏览器访问前端的 Origin 匹配；公网部署需包含你的域名/IP 模式 |

#### 前端环境变量

| 文件 | 典型用途 |
|------|----------|
| `frontend/.env.development` | 本地 `npm run dev`：`VITE_APP_API_BASE_URL` 指向后端（当前仓库为 `http://localhost:8080/api`） |
| `frontend/.env.production` | 生产构建：`VITE_APP_API_BASE_URL=/api`（由 Nginx 反代到后端） |

`vite.config.js` 中开发服务器端口为 **5173**，并将 `/api` 代理到 `http://localhost:8080`，与本地后端默认端口一致。

### 5. 启动服务

#### 方式 0（推荐）：Docker 托管基础服务 + 本地跑前后端

MySQL / Redis / MinIO 统一由 Docker Compose 托管（数据持久化在命名卷 `timbersage_*_data`），本地只跑后端 jar 和前端 Vite：

```bash
# 1) 拉起基础服务（MySQL 13307 / Redis 6394 / MinIO 19100 API + 19101 控制台）
docker compose up -d mysql redis minio

# 2) 后端：用环境变量覆盖默认连接，指向 Docker 暴露的端口
cd backend
mvn -DskipTests package
SPRING_DATASOURCE_URL="jdbc:mysql://localhost:13307/mortise_and_tenon_joint_library?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
SPRING_DATASOURCE_PASSWORD=$MYSQL_ROOT_PASSWORD \
SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=6394 \
SPRING_MINIO_ENDPOINT=http://localhost:19100 \
SPRING_MINIO_ACCESS_KEY=$MINIO_ROOT_USER SPRING_MINIO_SECRET_KEY=$MINIO_ROOT_PASSWORD \
SPRING_MINIO_PUBLIC_ENDPOINT=http://localhost:5173/minio \
SPRING_DEEPSEEK_API_KEY=$SPRING_DEEPSEEK_API_KEY ZHIPU_API_KEY=$ZHIPU_API_KEY \
java -jar target/arch3d-backend-1.0.0.jar

# 3) 前端
cd frontend
npm run dev   # http://localhost:5173
```

> 端口速查：**MySQL 13307**（容器内 3306）、**Redis 6394**（6379）、**MinIO API 19100**（9000）/ 控制台 19101（9001）。
> `vite.config.js` 已将 `/minio` 反代到 `http://localhost:19100`，与生产 Nginx 的 `/minio/` 行为一致，dev 下模型/缩略图链接可直接加载。
> 一键全容器部署（前后端也进容器）见 `DOCKER_DEPLOY.md`，端口为前端 18088 / 后端 8180。

#### 方式 A：本地开发（不用 Docker）

**前端**

```bash
cd frontend
npm install
npm run dev
# 默认 http://localhost:5173
```

常用脚本：`npm run build`、`npm run preview`、`npm run lint`、`npm run format`。

**后端**

```bash
cd backend
mvn spring-boot:run
# 默认 http://localhost:8080 ，接口路径以 /api 为前缀（由各 Controller 映射）
```

本地需自备 MySQL（默认连接 `localhost:3306` 与 `application.yml` 中库名一致）、Redis（`localhost:6379`）及 MinIO（`localhost:9000`），或与 Compose 中的基础服务配合使用并相应修改连接配置。

##### Windows：从零安装本地 MySQL / Redis / MinIO

以下步骤基于 **Windows 10/11 + PowerShell（管理员）**，适合不使用 Docker、直接本机安装依赖服务的场景。

**1) 安装 MySQL（推荐 8.x）**

```powershell
# 使用 winget 安装 MySQL Server（若提示源协议，同意即可）
winget install -e --id Oracle.MySQL
```

安装后建议完成这些初始化：

- 记录 root 密码，并确保服务已启动（服务名通常为 `MySQL80`）
- 打开 MySQL Shell / 命令行后执行：

```sql
CREATE DATABASE IF NOT EXISTS mortise_and_tenon_joint_library
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

可选验证：

```powershell
mysql --version
mysql -u root -p -e "SHOW DATABASES;"
```

**2) 安装 Redis（推荐 7.x，Memurai 兼容 Redis 协议）**

Windows 原生 Redis 官方长期维护方案建议采用 Memurai（命令与 Redis 基本兼容）：

```powershell
winget install -e --id Memurai.MemuraiDeveloper
```

安装完成后验证：

```powershell
redis-cli -p 6379 ping
# 期望返回：PONG
```

> 若你的终端无法直接识别 `redis-cli`，请重开终端，或从 Memurai 安装目录执行对应客户端命令。

**3) 安装 MinIO（本地单机）**

先创建目录并下载可执行文件：

```powershell
New-Item -ItemType Directory -Force C:\minio\data | Out-Null
New-Item -ItemType Directory -Force C:\minio\bin | Out-Null
Invoke-WebRequest "https://dl.min.io/server/minio/release/windows-amd64/minio.exe" -OutFile "C:\minio\bin\minio.exe"
```

在当前终端设置账号并启动（开发环境示例）：

```powershell
$env:MINIO_ROOT_USER="minioadmin"
$env:MINIO_ROOT_PASSWORD="你的MinIO密码"
& C:\minio\bin\minio.exe server C:\minio\data --console-address ":9001"
```

启动后访问：

- API Endpoint：`http://localhost:9000`
- Console：`http://localhost:9001`

**4) 与本项目配置对齐**

请确认 `backend/src/main/resources/application.yml` 与本地服务一致：

- MySQL：`localhost:3306`、库名 `mortise_and_tenon_joint_library`、账号密码与你本机一致
- Redis：`localhost:6379`
- MinIO：`endpoint: http://localhost:9000`、`access-key/secret-key` 与你启动参数一致

MinIO 桶（`models`、`thumbnails`）会由后端服务在运行时自动检查并创建，无需手动建桶。

**5) 启动项目（本地开发）**

```powershell
# 终端 1
cd frontend
npm install
npm run dev

# 终端 2
cd backend
mvn spring-boot:run
```

若需要开机自启动，请将 MySQL / Memurai 服务设为自动，并把 MinIO 配置为 Windows 服务（可使用 NSSM 等工具托管）。

##### Windows：一键检查清单（PowerShell）

> 用法：以管理员身份打开 PowerShell，复制整段执行。脚本不会修改系统配置，仅做检查并输出结果。

```powershell
$ErrorActionPreference = "SilentlyContinue"

Write-Host "=== TimberSage Windows 本地环境检查 ===" -ForegroundColor Cyan

function Show-Check($name, $ok, $detail) {
  if ($ok) {
    Write-Host ("[PASS] {0} - {1}" -f $name, $detail) -ForegroundColor Green
  } else {
    Write-Host ("[FAIL] {0} - {1}" -f $name, $detail) -ForegroundColor Red
  }
}

Write-Host "`n[1/8] 检查命令可用性..." -ForegroundColor Yellow
$mysqlCmd = Get-Command mysql
$redisCli = Get-Command redis-cli
$minioCmd = Get-Command minio
Show-Check "mysql 命令" ($null -ne $mysqlCmd) ($(if($mysqlCmd){$mysqlCmd.Source}else{"未找到，请确认 MySQL 已安装并加入 PATH"}))
Show-Check "redis-cli 命令" ($null -ne $redisCli) ($(if($redisCli){$redisCli.Source}else{"未找到，可重开终端或使用 Memurai 安装目录内客户端"}))
Show-Check "minio 命令(可选)" ($null -ne $minioCmd) ($(if($minioCmd){$minioCmd.Source}else{"PATH 未找到，若你使用 C:\minio\bin\minio.exe 可忽略"}))

Write-Host "`n[2/8] 检查服务状态（MySQL / Redis）..." -ForegroundColor Yellow
$mysqlSvc = Get-Service -Name "MySQL80"
$redisSvc = Get-Service | Where-Object { $_.Name -match "Memurai|Redis" } | Select-Object -First 1
Show-Check "MySQL80 服务" ($null -ne $mysqlSvc) ($(if($mysqlSvc){"状态: $($mysqlSvc.Status)"}else{"未找到服务 MySQL80（请按你的实际服务名确认）"}))
Show-Check "Redis/Memurai 服务" ($null -ne $redisSvc) ($(if($redisSvc){"$($redisSvc.Name) / 状态: $($redisSvc.Status)"}else{"未发现 Redis/Memurai 服务"}))

Write-Host "`n[3/8] 检查端口监听（3306 / 6379 / 9000 / 9001 / 8080 / 5173）..." -ForegroundColor Yellow
$ports = 3306,6379,9000,9001,8080,5173
foreach($p in $ports){
  $listen = Get-NetTCPConnection -LocalPort $p -State Listen | Select-Object -First 1
  Show-Check "端口 $p" ($null -ne $listen) ($(if($listen){"LISTENING (PID $($listen.OwningProcess))"}else{"未监听"}))
}

Write-Host "`n[4/8] 检查 MySQL 连通性与库..." -ForegroundColor Yellow
$mysqlVersion = (& mysql --version)
Show-Check "MySQL 版本" (![string]::IsNullOrWhiteSpace($mysqlVersion)) ($(if($mysqlVersion){$mysqlVersion}else{"无法执行 mysql --version"}))

$dbCheck = (& mysql -u root -p123456 -N -e "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='mortise_and_tenon_joint_library';")
Show-Check "数据库 mortise_and_tenon_joint_library" ($dbCheck -match "mortise_and_tenon_joint_library") ($(if($dbCheck){"已存在"}else{"未找到；请创建数据库或核对 root 密码"}))

Write-Host "`n[5/8] 检查 Redis 连通性..." -ForegroundColor Yellow
$redisPing = (& redis-cli -p 6379 ping)
Show-Check "Redis PING" ($redisPing -match "PONG") ($(if($redisPing){$redisPing}else{"无响应"}))

Write-Host "`n[6/8] 检查 MinIO HTTP 端点..." -ForegroundColor Yellow
$minioApi = try { (Invoke-WebRequest "http://localhost:9000/minio/health/live" -UseBasicParsing -TimeoutSec 3).StatusCode } catch { $null }
$minioConsole = try { (Invoke-WebRequest "http://localhost:9001" -UseBasicParsing -TimeoutSec 3).StatusCode } catch { $null }
Show-Check "MinIO API (9000)" ($minioApi -eq 200) ($(if($minioApi){"HTTP $minioApi"}else{"不可访问"}))
Show-Check "MinIO Console (9001)" ($minioConsole -ge 200 -and $minioConsole -lt 500) ($(if($minioConsole){"HTTP $minioConsole"}else{"不可访问"}))

Write-Host "`n[7/8] 检查后端健康与 Swagger..." -ForegroundColor Yellow
$backendApi = try { (Invoke-WebRequest "http://localhost:8080/api/auth/me" -UseBasicParsing -TimeoutSec 3).StatusCode } catch { $_.Exception.Response.StatusCode.value__ }
$swagger = try { (Invoke-WebRequest "http://localhost:8080/swagger-ui/index.html" -UseBasicParsing -TimeoutSec 3).StatusCode } catch { $null }
Show-Check "后端 API 可达(8080)" ($backendApi -in 200,401,403) ($(if($backendApi){"HTTP $backendApi（200/401/403 均表示服务已响应）"}else{"不可访问"}))
Show-Check "Swagger 可达" ($swagger -eq 200) ($(if($swagger){"HTTP $swagger"}else{"不可访问"}))

Write-Host "`n[8/8] 检查前端 Vite 页面..." -ForegroundColor Yellow
$frontend = try { (Invoke-WebRequest "http://localhost:5173" -UseBasicParsing -TimeoutSec 3).StatusCode } catch { $null }
Show-Check "前端(5173)" ($frontend -eq 200) ($(if($frontend){"HTTP $frontend"}else{"不可访问（若未启动前端可忽略）"}))

Write-Host "`n=== 检查完成 ===" -ForegroundColor Cyan
Write-Host "提示：若 MySQL 密码不是 123456，请把脚本中的 -p123456 改为你的密码后重试。" -ForegroundColor DarkYellow
```

#### 方式 B：Docker Compose

```bash
docker compose up -d --build --pull always
```

> 说明：**开发模式**（5173 + 8080）与 **Compose 模式**（见下表）端口不同，请以当前运行方式为准。

### 6. 访问地址

#### Docker Compose（默认端口映射）

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost:18088 | Nginx 托管静态资源 + 反代 `/api` 与 `/minio/` |
| 后端 API | http://localhost:8180/api | 容器内仍监听 8080，映射为宿主机 8180 |
| OpenAPI / Swagger UI | http://localhost:8180/swagger-ui/index.html | springdoc-openapi |
| MinIO 控制台 | http://localhost:19101 | 默认账号密码见 compose 环境变量 |
| MySQL | localhost:13307 | 映射到容器内 3306；root 密码见 `docker-compose.yml` |
| Redis | localhost:6394 | 映射到容器内 6379 |

#### 本地前后端开发（默认）

| 服务 | 地址 |
|------|------|
| 前端 (Vite) | http://localhost:5173 |
| 后端 | http://localhost:8080/api/... |

### 7. 功能模块说明

#### 7.1 地图导览模块

- 入口：侧栏 **地图导览**（路由：`/map-explorer`）
- 能力要点：顶部搜索并高亮点位、点击点位弹出区域模型卡片、卡片内展示 AI 生成的讲解要点与外链、`了解更多` 新窗口打开、返回与过渡动效
- 模型新增后地图点位会通过轮询/焦点刷新更新，无需重启前端
- 合规：页脚展示地图来源、审图号与年份；若未配置有效审图号，页面会提示不可正式发布

#### 7.2 首页首屏

- 背景资源：`frontend/src/assets/images/bg.jpg`
- 视觉：背景轻呼吸、玻璃态与流光等动效；替换同名图片后重新构建/刷新即可

### 8. 默认账号

- **管理员**：`admin` / `123456`（以后端与安全配置为准）
- **普通用户**：通过注册页创建

## API 文档

- 本地/容器后端：http://localhost:8180/swagger-ui/index.html（Compose）或 http://localhost:8080/swagger-ui/index.html（`mvn spring-boot:run`）

地图相关示例：

- `GET /api/map/points`
- `GET /api/map/regions/{regionCode}/models`
- `POST /api/map/insight`

## 多端适配（PC / Android / iOS）

- `frontend/index.html` 使用 `viewport-fit=cover` 以适配安全区
- 窄屏下侧栏、顶栏与搜索区自动折行；列表筛选在移动端单列展示
- 聊天浮窗随视口缩放并避开底部安全区

## 数据库说明

- **库名**：`mortise_and_tenon_joint_library`
- **结构**：JPA `ddl-auto: update`（开发便利；生产请使用迁移策略并关闭随意更新）

## 注意事项

### 敏感信息

1. **JWT**：生产环境必须更换 `jwt.secret`
2. **DeepSeek Key**：通过环境变量注入并定期轮换
3. **数据库与 MinIO 密码**：禁止使用弱口令上线

### 服务与网络

1. **MinIO 与 3D 资源**：生产路径下资源通过前端 **同源** 地址访问（`FRONTEND_PUBLIC_BASE_URL` + `/minio/`），避免浏览器跨域阻塞模型加载。
2. **SSE**：`nginx.conf` 对 `/api/chat/stream` 关闭代理缓冲，保证流式输出稳定。
3. **CORS**：公网访问时同步配置 `app.cors.allowed-origin-patterns`（本地 `application.yml`）或容器环境变量 `APP_CORS_ALLOWED_ORIGIN_PATTERNS` / `CORS_ALLOWED_ORIGIN_PATTERNS`（以你当前部署脚本为准）。

### Docker 数据卷

Compose 使用 **外部具名卷** 持久化数据；若启动报错提示卷不存在，请先执行本文「Docker Compose：首次初始化」中的 `docker volume create` 命令。

## 故障排查

1. **Compose 启动失败**：检查 Docker 是否运行、`docker compose ps`、各服务日志 `docker logs <容器名>`、宿主机端口占用。
2. **卷不存在**：按 README 创建 `mysql_data` / `redis_data` / `minio_data`（或你在环境中自定义的名称）。
3. **数据库连不上**：核对 `SPRING_DATASOURCE_*` 与 MySQL 是否健康、端口是否为 **13307**（Compose）或本地实际端口。
4. **前端无法调 API**：核对 `VITE_APP_API_BASE_URL`、是否需配置 CORS、Nginx `/api` 反代是否正常。
5. **模型加载跨域**：确认 MinIO 公网基地址是否为前端同源 `/minio` 反代路径。
6. **地图审图号提示**：检查 `app.map.compliance.review-*`；修改后端配置后重建/重启后端并强刷浏览器。

## 许可证

MIT License
