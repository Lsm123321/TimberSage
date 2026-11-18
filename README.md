# 智筑云览 TimberSage

基于 Three.js 的中国古建筑 3D 展示与智能讲解平台。66 座古建筑模型可在线浏览、拆解、导览，并配有可持续维护的 AI 问答知识库。

---

## 一、功能概览

### 展示与交互
- **3D 模型浏览**：glTF/GLB 加载、轨道控制、构件高亮、爆炸分解、材质切换
- **AI 语音导览**：自动规划 6~8 章讲解脚本，含镜头运动与模型动作，可 TTS 朗读
- **地图导览**：按省份聚合模型分布，点击定位
- **构件讲解**：点击任意网格，AI 给出结构/材料/文化角度的说明（带免责声明）

### AI 问答（知识库增强）
- **两级回答策略**：先查知识库，命中直接回放；未命中才调用大模型
- **来源标注**：命中时展示「来自知识库 · 类型 · 被问次数」，与大模型生成区分开
- **越界拦截**：造价、抗震等级、模型面数等档案覆盖不了的问题，明确告知而不是编造
- **兜底卡片**：给出该模型可问的 3 个建议问题，可点击直发

### 知识库运营
- **管理后台**：知识条目增删改查、按模型/类型/状态/关键词筛选、批量审核
- **归一化预览**：编辑问题时就地显示它会被匹配成什么形式，降低「改了问不上」的困惑
- **知识图谱**：分类 → 模型 → 知识类型三层力导向图，悬停高亮邻接、点击展开
- **命中统计**：记录每条知识被问次数与累计节省的 token

### 其他后台能力
模型审核与发布、用户管理、权限晋级审批、评论管理、纠错与建议处理、预设问题、数据统计、音色管理

---

## 二、技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.4 · Vite 5 · Element Plus 2.4 · Three.js 0.160 · ECharts 5.6 · Pinia |
| 后端 | Spring Boot 3.2 · Java 17 · Spring Data JPA · Spring Security(JWT) |
| 存储 | MySQL 8 · Redis 7 · MinIO（模型与图片对象存储） |
| AI | 智谱 `glm-4-flash`（主）/ DeepSeek `deepseek-chat`（备），OpenAI 兼容协议 |
| 部署 | Docker Compose · Nginx · Caddy（HTTPS 与 SSE 反代） |

---

## 三、目录结构

```
TimberSage/
├── backend/                  Spring Boot 服务
│   ├── src/main/java/com/arch3d/
│   │   ├── controller/       REST 接口（19 个）
│   │   ├── service/          业务逻辑（含知识库检索 KnowledgeService）
│   │   ├── client/           DeepSeekClient（大模型主备通道）
│   │   ├── entity/           JPA 实体（含 KbEntry 知识条目）
│   │   └── repository/       数据访问
│   └── config/               本地开发凭据（gitignore，勿提交）
├── frontend/                 Vue 3 前端
│   └── src/
│       ├── views/            页面（含 admin/ 管理后台）
│       ├── components/       ThreeScene / ChatFloat / FloatingUiControls
│       ├── composables/      主题与多语言（useUiPreferences）
│       └── assets/styles/    主题变量（theme-v2.scss）
├── docker-compose.yml        本地与生产共用的编排文件
├── Caddyfile.timbersage      生产反代配置
└── docs/                     导览协议等说明
```

---

## 四、快速开始（本地）

### 1. 准备环境变量

```bash
cp .env.example .env
```

必填项：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MINIO_ROOT_PASSWORD` | MinIO 密钥（默认 minioadmin123） |
| `JWT_SECRET` | JWT 签名密钥，**至少 32 字节**，不能是示例值，否则后端拒绝启动 |
| `ZHIPU_API_KEY` | 智谱 API Key（对话主通道 + TTS） |
| `SPRING_DEEPSEEK_API_KEY` | DeepSeek Key（降级通道，可留空） |
| `FRONTEND_PUBLIC_BASE_URL` | 前端访问地址，用于拼接 MinIO 资源 URL |

生成随机密钥：

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

### 2. 启动依赖服务

```bash
docker compose up -d mysql redis minio
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

首次启动会自动建表（`ddl-auto: update`）。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 5. 访问

| 服务 | 地址 |
|---|---|
| 前端 | http://localhost:5173 |
| 后端 | http://localhost:8080 |
| MySQL | localhost:13307 |
| Redis | localhost:6394 |
| MinIO 控制台 | http://localhost:19101 |

默认管理员：`admin / 123456`（首次启动时自动创建，**上线前务必修改**）

---

## 五、Docker 部署

### 本地一键起全栈

```bash
docker compose up -d --build
```

| 服务 | 端口 |
|---|---|
| 前端 | http://localhost:18088 |
| 后端 | http://localhost:8180 |
| MySQL | 13307 |
| Redis | 6394 |
| MinIO | 19100 / 控制台 19101 |

### 生产部署要点

1. **改掉默认密码**：MySQL、MinIO、管理员账号
2. **注入 `JWT_SECRET`**：生产必须用随机值；更换后所有用户需重新登录
3. **端口只绑内网**：`127.0.0.1:8180:8080`，由 Caddy/Nginx 对外
4. **Caddy 需为 SSE 关缓冲**，否则 AI 逐字输出会卡成一坨：

```caddyfile
timbersage.example.com {
    encode gzip
    reverse_proxy /api/chat/stream/* timbersage-frontend-1:80 {
        flush_interval -1
    }
    reverse_proxy /api/* timbersage-frontend-1:80
    reverse_proxy timbersage-frontend-1:80
}
```

### 更新代码后重新部署

```bash
# 1) 本地构建镜像
docker compose build frontend backend

# 2) 导出并传输（服务器不适合重新构建时）
docker save timbersage-frontend:latest | gzip > fe.tar.gz
docker save timbersage-backend:latest  | gzip > be.tar.gz
scp fe.tar.gz be.tar.gz root@<server>:/root/

# 3) 服务器加载并重建（保留数据卷）
ssh root@<server> "
  gunzip -c /root/fe.tar.gz | docker load
  gunzip -c /root/be.tar.gz | docker load
  cd /root/TimberSage && docker compose up -d --force-recreate frontend backend
"
```

> 只重建 `frontend` / `backend`。**不要 `down -v`**，否则 mysql/redis/minio 数据卷会被删除。

---

## 六、知识库说明

### 数据模型

`kb_entry` 表存标准问答，关键字段：

| 字段 | 作用 |
|---|---|
| `question` / `answer` | 问题与审核后的答案 |
| `normalized_question` | 归一化后的问题，匹配用（入库时算好） |
| `question_type` | structure / material / history / culture / aesthetic / compare |
| `status` | 0 候选 · 1 通过 · 2 驳回 · 3 归档，**只有 1 参与检索** |
| `hit_count` | 命中次数，管理端按它排序找高频问题 |
| `knowledge_version` | 编辑时 +1，用于失效缓存 |

### 检索流程

```
用户提问
  → 越界拦截（造价/抗震/面数…）→ 直接告知答不了
  → 通识拦截（"介绍一下榫卯工艺"）→ 交给大模型
  → L1 Redis 精确命中 → 回放
  → L2 归一化精确匹配 → 回放
  → L3 意图分类（结构/材料/…）→ 命中该类型档案
  → 全部未命中 → 调大模型
```

命中时按 30 字 / 45ms 切片回放，前端观感与流式生成一致，但延迟约 300ms。

### 维护建议

- 新增知识默认落 `status=0`，人工审核后才上线
- 答案务必核对事实（材质、年代、主持者最容易写错）
- 高频问题优先维护：管理端按 `hit_count` 降序看

---

## 七、常见问题

**后端起不来，报「JWT 密钥未配置」**
`.env` 里补 `JWT_SECRET`（≥32 字节、不含 `change-this` / `your-jwt`），并确认 `docker-compose.yml` 的 `backend.environment` 传了该变量。

**模型加载不出来，控制台报跨域**
`SPRING_MINIO_PUBLIC_ENDPOINT` 必须是浏览器可访问的地址，且与前端同源（通常走 Nginx `/minio/` 反代）。

**AI 回答不逐字输出，一次性蹦出来**
反向代理开了缓冲。SSE 路径必须 `proxy_buffering off`（Nginx）或 `flush_interval -1`（Caddy）。

**知识库管理页显示 0 条**
确认 `kb_entry` 表有数据且 `status=1`；新部署环境默认是空表，需要导入或从后台录入。

**改了主题/导航后页面没变化**
浏览器缓存了旧资源。强刷（Ctrl+Shift+R），并确认镜像已重新构建、容器 `--force-recreate`。

---

## 八、许可与致谢

课程/毕业设计作品，仅供学习交流。模型素材与史料引用请遵循原始出处授权。