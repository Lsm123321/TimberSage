# 智筑云览 TimberSage

一个古建筑 3D 展示与智能导览平台。核心玩法是：把木构建筑模型放进 Three.js 场景里，配合 AI 生成的导览脚本和语音，边转模型边讲解榫卯、斗拱这些构造细节。

名字取自 "Timber"（木构）和 "Sage"（讲述者）。

---

## 技术选型

| 层 | 技术 | 说明 |
|---|---|---|
| 前端 | Vue 3.4 + Vite 5 + Element Plus + Pinia | 组合式 API，`<script setup>` 写到底 |
| 3D | Three.js 0.160 | 程序化四季环境，没有用现成场景库 |
| 图表 | ECharts 5.4 | 地图导览（中国地图 geo）+ 后台统计 |
| 后端 | Spring Boot 3.2 / Java 17 | 单体应用，分层为 controller / service / repository |
| 鉴权 | Spring Security + JJWT | 无状态 JWT，前后端分离不做 session |
| 存储 | MySQL 8.4 / Redis 7.4 / MinIO | 业务数据 / 缓存 / 模型文件与图片 |
| AI | DeepSeek Chat + 智谱 CogTTS | 前者生成讲解文案，后者做语音合成与音色克隆 |

选 Java 17 + Spring Boot 3.x 是为了用上 record、switch 表达式这些语法糖；Three.js 版本停在 0.160 是因为 0.16x 之后 `GLTFLoader` 的一个行为变动会让现有的模型加载代码需要改。

---

## 目录结构

```
TimberSage/
├── backend/
│   ├── src/main/java/com/arch3d/
│   │   ├── controller/      # 19 个 REST 控制器
│   │   ├── service/         # 业务逻辑，impl 下是实现
│   │   ├── repository/      # Spring Data JPA
│   │   ├── entity/          # 实体，@Table 上直接挂索引声明
│   │   ├── client/          # DeepSeekClient 等外部 API 封装
│   │   ├── config/          # Security / Minio / Redis / 定时任务
│   │   └── security/        # JWT 过滤器与 UserDetails
│   ├── config/              # 本地凭据覆盖（不进版本库）
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── views/           # 14 个页面 + admin/ 管理后台
│   │   ├── components/      # ThreeScene 等
│   │   ├── composables/     # 四季场景、导览状态机、TTS 抽象
│   │   ├── stores/          # Pinia
│   │   └── api/             # 按模块拆的请求封装
│   ├── public/              # favicon 等静态资源
│   ├── package.json
│   └── Dockerfile
├── docs/                    # 设计说明
├── img/                     # 文档配图
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 本地跑起来

### 前置

- Docker Desktop（中间件全走容器，本机不用装 MySQL/Redis/MinIO）
- JDK 17、Maven 3.9
- Node.js 20（跟 Dockerfile 里的构建镜像保持一致）

### 1. 配置环境变量

```bash
cp .env.example .env
```

然后改 `.env` 里这几项：

- `MYSQL_ROOT_PASSWORD` / `MINIO_ROOT_PASSWORD`：设成你自己的密码
- `SPRING_DEEPSEEK_API_KEY`：DeepSeek 的 key，不填 AI 导览会报错，但其余功能正常
- `ZHIPU_API_KEY`：智谱的 key，不填前端降级到浏览器自带朗读

后端本地开发还需要一份凭据文件（`application.yml` 里 `profiles.active` 默认读 `local`）：

```yaml
# backend/config/application-local.yml —— 这个文件被 .gitignore 排除
spring:
  datasource:
    password: MySQL密码
  minio:
    secret-key: MinIO密码
deepseek:
  api-key: DeepSeek Key
zhipu:
  api-key: 智谱Key
```

### 2. 起中间件

三个数据卷声明为 `external: true`，首次跑要先手工创建：

```bash
docker volume create timbersage_mysql_data
docker volume create timbersage_redis_data
docker volume create timbersage_minio_data
docker compose up -d mysql redis minio
```

### 3. 起后端

```bash
cd backend
mvn spring-boot:run
```

跑在 8080。首次启动 Hibernate 会自动建表（`ddl-auto: update`），MinIO 的 `models` / `thumbnails` / `avatars` 三个桶也会自动创建。

### 4. 起前端

```bash
cd frontend
npm install
npm run dev
```

Vite 默认 5173，`/api` 和 `/minio` 都配了代理到 8080 和 19100，不用另外处理跨域。

默认账号 `admin` / `123456`，由 `DataInitializer` 在启动时写入。**这个密码每次启动都会被重置回 123456**，上生产前务必改掉这段逻辑。

### 5. 整套走 Docker

```bash
docker compose up -d --build
```

访问 http://localhost:18088 （前端）、http://localhost:8180 （后端直连）、http://localhost:19101 （MinIO 控制台）。

端口特意都选了非常规值（13307 / 6394 / 19100 / 8180 / 18088），避免和本机已有的 MySQL、Redis 撞车。

---

## 创新实现

### 四季动态场景

`useSceneEnvironment` 里把春樱、夏晴、秋阳、冬雪四套环境抽象成参数集，切换时不是换贴图，而是实时改光照色温、雾密度、粒子类型。太阳位置按季节走不同弧线，冬天低角度所以阴影更长。

### AI 导览脚本

DeepSeek 返回的是结构化分章脚本（旁白 + 镜头动作 + 构件高亮目标），不是一段散文。前端 `useAiTour` 拿这个结构驱动相机飞行和模型爆炸分解，字幕按字打出。这样做的原因是纯文本讲解没法跟 3D 场景联动，用户看不出"现在讲的到底是哪根梁"。

DeepSeek 在高峰期会堵到几分钟不出首字，所以加了双通道：首 token 超过 45 秒或 token 间隔超过 20 秒就切到智谱的 GLM-4-Flash 兜底。这两个阈值是实际被堵过几次之后调出来的。

### 地图导览

中国地图用 ECharts 的 geo 组件 + 本地 GeoJSON（不走在线瓦片，避免依赖第三方服务）。点位按省聚合，同省多个模型会做偏移避免完全重叠。省份归属优先按 location 字段里的地名匹配，匹配不到才调 AI 推断——因为一次地图加载可能涉及几十个模型，全走 AI 会非常慢。

### 头像上传

图片压到 512px、转 JPEG 后再存 MinIO，数据库只存 `/minio/avatars/xxx.jpg` 这种相对路径。存相对路径是因为开发和生产的 MinIO 地址不同，存绝对 URL 会导致一份数据在另一个环境里全部 404。

### 下载统计的分页

这个页面之前有个性能坑：接口一次返回全部下载记录（7600 条 / 1.66MB），前端表格又不分页，直接把 5.3 万个单元格塞进 DOM，浏览器能卡到失去响应。改成服务端分页 + `JOIN FETCH` 带出关联后，单页响应降到 4KB。

---

## 数据模型

核心几张表：

- `user` —— 用户，`role` 区分 user / contributor / admin
- `building_model` —— 模型主表，含 status（0 待审 / 1 已发布 / 2 驳回）
- `building_category` —— 分类（古桥梁 / 宫殿 / 寺庙 / 园林 / 民居 / 塔楼）
- `download_log` —— 下载记录，`download_time` 上有索引
- `ai_chat_history` / `chat_history` —— AI 问答记录
- `model_comment` / `comment_like` —— 评论与点赞
- `custom_voice` —— 克隆音色

表结构没有单独的 migration 文件，靠 JPA 注解 + `ddl-auto: update` 维护。这套方式适合单人开发，多人协作的话建议换成 Flyway。

---

## 接口

启动后可以看 Swagger：http://localhost:8080/swagger-ui.html

接口统一返回 `{ code, message, data }`，`code` 为 200 表示成功。有两类例外：

- `/models` 系列直接返回 Spring Data 的 `Page` 结构
- `/api/chat/stream` 是 SSE，没有外层包装

鉴权走 `Authorization: Bearer <token>`。放行的只有 `/api/auth/**`、`/api/public/**` 和 Swagger。

---

## 已知问题

- `DataInitializer` 每次启动重置 admin 密码，演示方便但不适合生产
- 前端 `MapExplorer` 打包后接近 600KB（ECharts + Three.js 都在这一块），首次加载偏慢
- 「仅未归类」筛选只能在本页内生效，后端没有对应的 SQL 条件
- 没有任何自动化测试，回归靠手工点

---

## 开发约定

- 前端状态跨页面共享走 Pinia，不要用事件总线
- 新增接口记得在 `SecurityConfig` 里确认权限，默认是 `authenticated()`
- 改完前端跑一次 `npm run build`，有些模板语法错误只在构建时暴露

---

## 许可

课程/毕业/比赛设计用途。第三方模型素材版权归原作者，仓库里未包含。
