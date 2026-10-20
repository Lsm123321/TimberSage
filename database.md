# 智筑云览 - 数据库设计文档

## 概述

本数据库为「智筑云览」后端的数据存储，面向 **MySQL 8.x**。当前 **`21` 张业务表**（与 `backend/src/main/java/com/arch3d/entity` 下 `@Entity` 一一对应）。

**权威来源**：以实体类字段、`@Table` / `@Column` 定义为准；本地开发默认通过 Spring Data JPA + Hibernate（`spring.jpa.hibernate.ddl-auto`，见 `application.yml`）与数据库对齐。

**数据库名称**: `mortise_and_tenon_joint_library`  
**推荐字符集**: `utf8mb4`  
**推荐排序规则**: `utf8mb4_unicode_ci`  
**存储引擎**: InnoDB  

---

## 数据表详细说明

### 1. 用户表 (user)

**表作用**: 系统用户、认证与角色。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| username | VARCHAR(64) | NOT NULL | - | 登录名，唯一 |
| password_hash | VARCHAR(255) | NOT NULL | - | BCrypt 密码哈希 |
| email | VARCHAR(128) | NULL | - | 邮箱，唯一 |
| avatar | VARCHAR(255) | NULL | - | 头像 URL |
| role | VARCHAR(16) | NOT NULL | - | 角色，如 admin / user / contributor |
| nickname | VARCHAR(64) | NULL | - | 昵称 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

**索引**: `PRIMARY KEY (id)`；`username`、`email` 唯一（由实体 `unique` 约束体现）。

**关系**: 被模型、评论、申请、反馈、日志等多表引用（逻辑外键，以实体 `@JoinColumn` 为准）。

---

### 2. 建筑分类表 (building_category)

**表作用**: 模型/内容分类。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| category_code | VARCHAR(32) | NOT NULL | - | 分类代码 |
| category_name | VARCHAR(64) | NOT NULL | - | 分类名称 |
| icon | VARCHAR(255) | NULL | - | 图标 URL |
| sort_order | INT | NULL | - | 排序 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

**索引**: `PRIMARY KEY (id)`。

---

### 3. 古建筑表 (ancient_buildings)

**表作用**: 古建筑档案与展示（与「用户上传的 building_model」并存的一套数据）。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | BIGINT | NOT NULL | AUTO_INCREMENT | 主键 |
| name | VARCHAR(100) | NOT NULL | - | 名称（中文） |
| name_en | VARCHAR(100) | NULL | - | 英文名称 |
| description | TEXT | NULL | - | 描述 |
| dynasty | VARCHAR(50) | NULL | - | 朝代 |
| location | VARCHAR(100) | NULL | - | 地理位置 |
| cover_image | VARCHAR(255) | NULL | - | 封面图 |
| model_url | VARCHAR(255) | NULL | - | 模型 URL |
| model_type | VARCHAR(20) | NULL | - | 模型格式 |
| specifications | TEXT | NULL | - | 规格 |
| historical_significance | TEXT | NULL | - | 历史意义 |
| view_count | INT | NULL | 0 | 浏览次数 |
| favorite_count | INT | NULL | 0 | 收藏次数 |
| create_time | DATETIME | NULL | - | 创建时间 |
| update_time | DATETIME | NULL | - | 更新时间 |

**索引**: `PRIMARY KEY (id)`。

---

### 4. 建筑模型表 (building_model)

**表作用**: 用户上传的建筑 3D 模型、审核状态与统计。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| category_id | INT | NULL | - | 分类 ID → building_category |
| model_name | VARCHAR(128) | NOT NULL | - | 模型名称 |
| model_file_path | VARCHAR(512) | NULL | - | 模型文件存储路径（可为空，视业务写入时机） |
| thumbnail_path | VARCHAR(512) | NULL | - | 缩略图路径 |
| dynasty | VARCHAR(64) | NULL | - | 朝代 |
| location | VARCHAR(255) | NULL | - | 位置 |
| description | TEXT | NULL | - | 模型说明 |
| tags | JSON | NULL | - | 标签 |
| view_count | INT | NOT NULL | 0 | 浏览次数 |
| like_count | INT | NOT NULL | 0 | 点赞次数 |
| download_count | INT | NOT NULL | 0 | 下载次数 |
| download_permission | INT | NOT NULL | 1 | 0 禁止下载，1 允许 |
| default_camera_position | JSON | NULL | - | 默认相机位置 |
| default_camera_target | JSON | NULL | - | 默认相机目标 |
| status | INT | NOT NULL | 0 | 0 待审，1 通过，2 拒绝 |
| reject_reason | VARCHAR(255) | NULL | - | 拒绝原因 |
| created_by | INT | NOT NULL | - | 创建人 → user.id |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

**索引**: 建议存在 `category_id`、`created_by` 上的查询索引（由历史库或 Hibernate 生成）。

**删除语义**: 部分子表对 `building_model` 使用 `@OnDelete(CASCADE)`（见下文各表），删除模型会级联删除相关评论点赞、下载日志、反馈、预设等关联行（以数据库中外键是否创建为准，逻辑上实体已声明）。

---

### 5. 收藏表 (favorites)

**表作用**: 用户对 **古建筑 (ancient_buildings)** 的收藏。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | BIGINT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | - | 用户（类型与古建筑侧 ID 宽度一致） |
| building_id | BIGINT | NOT NULL | - | 古建筑 ID |
| create_time | DATETIME | NULL | - | 收藏时间 |

**索引**: `PRIMARY KEY (id)`；常见查询可按 `user_id`、`building_id` 建索引。

---

### 6. 聊天历史表 (chat_history)

**表作用**: 与古建筑相关的简易问答记录。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | BIGINT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | - | 用户 ID |
| building_id | BIGINT | NULL | - | 古建筑 ID，可空 |
| question | TEXT | NOT NULL | - | 问题 |
| answer | TEXT | NOT NULL | - | 回答 |
| create_time | DATETIME | NULL | - | 创建时间 |

**索引**: `PRIMARY KEY (id)`。

---

### 7. AI 聊天历史表 (ai_chat_history)

**表作用**: 基于 **用户上传模型 (building_model)** 的 AI 多轮会话与明细。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(64) | NOT NULL | - | 会话 ID |
| user_id | INT | NOT NULL | - | 用户 → user |
| model_id | INT | NULL | - | 模型 → building_model，可空 |
| user_question | TEXT | NOT NULL | - | 用户问题 |
| ai_answer | TEXT | NOT NULL | - | AI 回答 |
| ai_model | VARCHAR(64) | NULL | - | 使用的模型名 |
| tokens_used | INT | NULL | - | Token 消耗 |
| helpful | BOOLEAN / TINYINT(1) | NULL | - | 用户是否标记有帮助 |
| chat_note | TEXT | NULL | - | 会话备注 |
| created_at | DATETIME | NULL | - | 创建时间 |

**索引**: `PRIMARY KEY (id)`；建议 `session_id`、`user_id`、`model_id`。

**注意**: 实体对 `building_model` 配置了 `@OnDelete(CASCADE)`：删除模型时，关联的 AI 历史行会被级联删除（若库中外键生效）。

---

### 8. 模型评论表 (model_comment)

**表作用**: 对 `building_model` 的评论与二级回复。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| model_id | INT | NOT NULL | - | 模型 |
| user_id | INT | NOT NULL | - | 用户 |
| parent_id | INT | NULL | - | 父评论 ID |
| content | TEXT | NOT NULL | - | 内容 |
| like_count | INT | NOT NULL | 0 | 点赞数 |
| status | INT | NOT NULL | 1 | 0 隐藏，1 正常，2 删除（与业务约定一致） |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

**索引**: `PRIMARY KEY (id)`；`model_id`、`user_id`、`parent_id`。

**删除语义**: `model_id` 指向的模型删除时级联删除评论（`@OnDelete(CASCADE)`）。

---

### 9. 评论点赞表 (comment_like)

**表作用**: 用户对评论点赞，防重复。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| comment_id | INT | NOT NULL | - | 评论 |
| user_id | INT | NOT NULL | - | 用户 |
| created_at | DATETIME | NULL | - | 时间 |

**约束**: `UNIQUE (comment_id, user_id)`。

**删除语义**: 评论删除时级联删除点赞（`@OnDelete` 在 `comment` 侧）。

---

### 10. 投稿申请表 (contribution_apply)

**表作用**: 申请成为 contributor 等流程。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NOT NULL | - | 申请人 |
| description | TEXT | NOT NULL | - | 申请说明 |
| status | INT | NOT NULL | 0 | 0 待审，1 通过，2 拒绝 |
| reviewed_by | INT | NULL | - | 审核人 |
| review_remark | VARCHAR(255) | NULL | - | 审核备注 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

---

### 11. 角色升级申请表 (role_upgrade_apply)

**表作用**: 用户角色变更申请（如升级为 contributor），与 `contribution_apply` 分工不同，以业务代码为准。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NOT NULL | - | 申请人 |
| from_role | VARCHAR(16) | NOT NULL | - | 原角色 |
| to_role | VARCHAR(16) | NOT NULL | - | 目标角色 |
| description | TEXT | NOT NULL | - | 说明 |
| status | INT | NOT NULL | 0 | 0 待审，1 通过，2 驳回 |
| review_remark | VARCHAR(255) | NULL | - | 审核备注 |
| reviewed_by | INT | NULL | - | 审核人 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

---

### 12. 下载日志表 (download_log)

**表作用**: 模型下载审计与统计。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | BIGINT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NULL | - | 用户，可空（匿名） |
| model_id | INT | NOT NULL | - | 模型 |
| ip_address | VARCHAR(45) | NULL | - | IP |
| download_time | DATETIME | NULL | - | 下载时间 |

**删除语义**: 实体对 `building_model` 为 `@OnDelete(CASCADE)`。

---

### 13. 帮助反馈表 (help_feedback)

**表作用**: 帮助中心/通用问题反馈。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NULL | - | 用户，可空 |
| feedback_type | VARCHAR(32) | NOT NULL | - | 类型，如 bug / feature 等 |
| content | TEXT | NOT NULL | - | 内容 |
| contact | VARCHAR(128) | NULL | - | 联系方式 |
| attachments | JSON | NULL | - | 附件元数据 |
| status | INT | NOT NULL | 0 | 处理状态 |
| admin_remark | VARCHAR(255) | NULL | - | 管理员备注 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

---

### 14. 站点反馈表 (site_feedback)

**表作用**: 全站意见/留言类反馈（标题 + 正文），与 `help_feedback` 并存。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NULL | - | 用户，可空 |
| title | VARCHAR(128) | NOT NULL | - | 标题 |
| content | TEXT | NOT NULL | - | 正文 |
| contact | VARCHAR(128) | NULL | - | 联系方式 |
| status | INT | NOT NULL | 0 | 0 未处理，1 已处理，2 已忽略 |
| admin_reply | TEXT | NULL | - | 管理员回复 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

---

### 15. 热门搜索表 (hot_search)

**表作用**: 按日期聚合的搜索热词。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| keyword | VARCHAR(128) | NOT NULL | - | 关键词 |
| search_count | INT | NOT NULL | 0 | 次数 |
| stat_date | DATE | NOT NULL | - | 统计日期 |
| created_at | DATETIME | NULL | - | 创建时间 |

**索引**: `PRIMARY KEY (id)`；按 `stat_date`、`keyword` 查询。

---

### 16. 模型反馈表 (model_feedback)

**表作用**: 针对模型质量/错误的反馈。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| model_id | INT | NOT NULL | - | 模型 |
| user_id | INT | NOT NULL | - | 反馈人 |
| error_type | VARCHAR(64) | NOT NULL | - | 错误类型 |
| description | TEXT | NOT NULL | - | 描述 |
| attachments | JSON | NULL | - | 附件 |
| status | INT | NOT NULL | 0 | 处理状态 |
| reviewed_by | INT | NULL | - | 处理人 |
| admin_remark | VARCHAR(255) | NULL | - | 管理员备注 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

**删除语义**: `building_model` 删除时级联删除（`@OnDelete(CASCADE)`）。

---

### 17. 预设问题表 (preset_question)

**表作用**: 预设问答（可按分类 / 模型挂载）。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| category_id | INT | NULL | - | 分类 |
| model_id | INT | NULL | - | 模型 |
| question_text | VARCHAR(255) | NOT NULL | - | 问题 |
| answer_text | TEXT | NOT NULL | - | 答案 |
| sort_order | INT | NULL | - | 排序 |
| click_count | INT | NOT NULL | 0 | 点击统计 |
| enabled | INT | NOT NULL | 1 | 0 禁用，1 启用 |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

**删除语义**: `model_id` 侧 `@OnDelete(CASCADE)`。

---

### 18. 搜索日志表 (search_log)

**表作用**: 单次搜索行为明细，供聚合 `hot_search` 使用。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | BIGINT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NULL | - | 用户 |
| keyword | VARCHAR(128) | NULL | - | 关键词 |
| category_filter | VARCHAR(64) | NULL | - | 分类筛选 |
| dynasty_filter | VARCHAR(64) | NULL | - | 朝代筛选 |
| result_count | INT | NOT NULL | - | 结果条数 |
| created_at | DATETIME | NULL | - | 搜索时间 |

---

### 19. 用户材质预设表 (user_material_preset)

**表作用**: 每用户每模型多套材质参数快照。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NOT NULL | - | 用户 |
| model_id | INT | NOT NULL | - | 模型 |
| preset_name | VARCHAR(64) | NOT NULL | - | 预设名 |
| material_params | JSON | NOT NULL | - | 材质 JSON |
| created_at | DATETIME | NULL | - | 创建时间 |
| updated_at | DATETIME | NULL | - | 更新时间 |

**约束**: `UNIQUE (user_id, model_id, preset_name)`。

**删除语义**: 模型删除时级联删除（`@OnDelete(CASCADE)`）。

---

### 20. 用户模型点赞表 (user_model_like)

**表作用**: 用户对 `building_model` 点赞，防重复。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NOT NULL | - | 用户 |
| model_id | INT | NOT NULL | - | 模型 |
| created_at | DATETIME | NULL | - | 时间 |

**约束**: `UNIQUE (user_id, model_id)`。

**删除语义**: 模型删除时级联删除。

---

### 21. 用户模型收藏表 (user_model_favorite)

**表作用**: 用户对 `building_model` 的收藏。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | INT | NOT NULL | - | 用户 |
| model_id | INT | NOT NULL | - | 模型 |
| created_at | DATETIME | NULL | - | 时间 |

**约束**: `UNIQUE (user_id, model_id)`。

**删除语义**: 模型删除时级联删除。

---

### 22. 克隆音色表 (custom_voice)

**表作用**: 管理员通过智谱 GLM-TTS-Clone 声音克隆创建的自定义朗读音色。启用的音色会出现在 AI 语音导览播放条的音色下拉中。

| 字段名 | 数据类型 | 是否为空 | 默认值 | 字段说明 |
|--------|----------|----------|--------|----------|
| id | INT | NOT NULL | AUTO_INCREMENT | 主键 |
| voice_name | VARCHAR(64) | NOT NULL | - | 展示名（如"管理员声音"） |
| voice_id | VARCHAR(128) | NOT NULL | - | 智谱返回的音色 ID（TTS 的 voice 参数值） |
| clone_model | VARCHAR(64) | NULL | - | 克隆所用模型（glm-tts-clone），便于升级 |
| preview_text | VARCHAR(255) | NULL | - | 克隆时的试听文本 |
| status | VARCHAR(16) | NOT NULL | enabled | enabled / disabled |
| created_by | INT | NULL | - | 操作管理员 |
| created_at | DATETIME(6) | NULL | - | 创建时间 |

**相关接口**: `POST /api/admin/voices/upload`（上传样本）、`POST /api/admin/voices/clone`（克隆）、`GET /api/admin/voices`、`PUT /api/admin/voices/{id}/status`、`DELETE /api/admin/voices/{id}`、`POST /api/admin/voices/{id}/preview`（试听）。

---

## 数据表关系图（简化）

```
用户与权限
├── user
│   ├── building_model.created_by
│   ├── ai_chat_history.user_id
│   ├── model_comment.user_id
│   ├── comment_like.user_id
│   ├── contribution_apply (user_id / reviewed_by)
│   ├── role_upgrade_apply (user_id / reviewed_by)
│   ├── help_feedback.user_id
│   ├── site_feedback.user_id
│   ├── model_feedback (user_id / reviewed_by)
│   ├── search_log.user_id
│   ├── user_material_preset.user_id
│   ├── user_model_like.user_id
│   └── user_model_favorite.user_id
│
├── building_category
│   ├── building_model.category_id
│   └── preset_question.category_id
│
└── building_model
    ├── ai_chat_history.model_id
    ├── model_comment.model_id
    ├── download_log.model_id
    ├── model_feedback.model_id
    ├── preset_question.model_id
    ├── user_material_preset.model_id
    ├── user_model_like.model_id
    └── user_model_favorite.model_id

古建筑线
├── ancient_buildings
├── favorites (user_id + building_id)
└── chat_history (user_id + building_id)

统计与反馈
├── search_log →（定时聚合）→ hot_search
├── help_feedback
├── site_feedback
└── model_feedback
```

---

## 关键业务逻辑说明

### 1. 用户角色

- **admin**: 管理与审核  
- **user**: 普通用户  
- **contributor**: 可上传模型等扩展权限（具体以接口 `@PreAuthorize` 与业务为准）  

角色相关申请见 `contribution_apply`、`role_upgrade_apply`。

### 2. 模型审核

1. 上传后 `building_model.status = 0`（待审核）  
2. 通过 `1` / 拒绝 `2`，拒绝时可写 `reject_reason`  
3. 仅通过模型按业务规则对外展示  

### 3. 热门搜索

1. 搜索写入 `search_log`  
2. `ScheduledTasks` 中定时任务 **每 10 分钟** 调用 `SearchLogService.aggregateHotSearches()` 聚合到 `hot_search`（详见 `backend/.../config/ScheduledTasks.java`）  
3. 接口侧按最新 `stat_date` 或缓存策略返回热词  

### 4. 点赞 / 收藏防重

- `comment_like`、`user_model_like`、`user_model_favorite` 使用唯一约束避免重复  

### 5. AI 会话

- `ai_chat_history.session_id` 关联多轮对话  
- `helpful`、`chat_note` 用于反馈与备注  

### 6. AI 语音导览与声音克隆

- **导览脚本**：`TourScriptService` 以模型元数据 + 前端上报的构件名清单生成导演级分章脚本（含 `action` 动作编排：环绕/爆炸分解/推近/拉远），Redis 缓存 key `tour:script:{modelName}`（24h）；协议与接口详见 `docs/AI_TOUR.md`  
- **语音合成**：`TtsService` 代理智谱 CogTTS；音色为 `custom:{id}` 时查 `custom_voice` 表映射为智谱 voice ID；服务不可用返回 code=529 由前端降级浏览器内置朗读  
- **声音克隆**：管理员在音色管理页上传人声样本（智谱 `/v4/files`，purpose=voice-clone-input）→ `/v4/voice/clone`（glm-tts-clone）→ 结果存 `custom_voice`；启用的音色出现在导览播放条音色下拉  

### 7. 模型编辑与缩略图替换

- `PUT /api/models/{id}` 支持可选 `thumbnailFile`（multipart）；替换流程为**先上传新图成功后再删 MinIO 旧图**，旧图清理失败仅记录日志不阻断更新  

---

## 默认数据说明

### 建筑分类 (building_category)

应用启动时 `DataInitializer` 会 **upsert** 以下分类（含排序）：

1. `bridge` — 古桥梁  
2. `palace` — 宫殿建筑  
3. `temple` — 寺庙建筑  
4. `garden` — 园林建筑  
5. `dwelling` — 民居建筑  
6. `tower` — 塔楼建筑  

### 内置用户 (user)

由 `DataInitializer` 在启动时创建或 **重置密码哈希**（开发环境默认；生产请修改密码并评估是否关闭自动重置逻辑）：

| 用户名 | 默认密码 | 角色 | 邮箱 |
|--------|----------|------|------|
| admin | 123456 | admin | admin@arch3d.com |
| testuser | 123456 | user | testuser@arch3d.com |

---

## 数据库维护建议

1. **清理**: 可按保留策略归档或删除历史 `search_log`；已结单的反馈类表按需归档。  
2. **性能**: 高频计数字段可考虑缓存；热词可配合 Redis（若已实现）。  
3. **安全**: JWT 密钥、数据库口令、第三方 API Key 仅放配置或环境变量，勿写入对外文档；定期轮换。  

---

## 相关文件

- **实体定义（schema 权威）**: `backend/src/main/java/com/arch3d/entity/`  
- **初始化数据**: `backend/src/main/java/com/arch3d/config/DataInitializer.java`  
- **定时聚合**: `backend/src/main/java/com/arch3d/config/ScheduledTasks.java`  
- **数据源与 JPA**: `backend/src/main/resources/application.yml`  

---

*文档最后更新: 2026-05-06*  
*与仓库内 JPA 实体同步；若实体变更请以代码为准并更新本文档。*
