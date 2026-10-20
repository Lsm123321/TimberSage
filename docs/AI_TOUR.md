# AI 语音导览系统

> 智筑云览 的 AI 语音导览：DeepSeek 生成导演级分章脚本，前端执行镜头运镜、模型动作与构件高亮，配合 TTS 朗读。

## 一、整体流程

```
用户点击「AI 导览」（模型详情页底部工具栏）
   │
   ├─ ① POST /api/models/{id}/tour-script
   │     后端组装 prompt（模型元数据 + 前端上报的构件名清单）
   │     → DeepSeek 非流式生成结构化脚本 JSON
   │     → 解析校验（失败回退内置四章模板）
   │     → Redis 缓存 24h（key: tour:script:{modelName}）
   │
   ├─ ② 前端 useAiTour 状态机逐章播放：
   │     镜头飞行/环绕（useCameraFlight）→ 讲解动作（爆炸/缩放）
   │     → 构件高亮（OutlinePass 橙色描边）→ TTS 朗读 → 章末停顿 → 下一章
   │
   └─ ③ TTS：POST /api/tts（智谱 CogTTS / 克隆音色）
         失败自动降级浏览器 speechSynthesis
```

## 二、脚本协议（TourChapter）

每章一个 JSON 对象，由 LLM 产出：

| 字段 | 类型 | 说明 |
|---|---|---|
| `chapter` | string | 章节标题（≤8 字） |
| `narration` | string | 旁白文案（60~140 字，口语化，TTS 朗读 + 字幕） |
| `partName` | string? | 本章高亮的构件名（必须来自前端上报的构件清单） |
| `cameraPos` | number[3] | 相机位置（模型归一化空间：最大边≈3、中心在原点） |
| `cameraTarget` | number[3] | 注视点 |
| `duration` | number | 建议飞行时长（秒），前端与旁白估算时长取平衡 |
| `action` | string | 讲解动作，见下表 |

### 动作编排（action）

| action | 前端行为 | 典型章节 |
|---|---|---|
| `orbit` | `flyOrbit` 环绕模型一周（360°，easeInOut） | 开场概览、总结 |
| `explode` | `setExploded(true)` 爆炸分解 + 机位拉远 | 构件组合关系、榫卯结构 |
| `assemble` | `setExploded(false)` 收回爆炸 | 分解讲完恢复整体 |
| `zoomIn` | 机位沿注视方向推近 0.6 倍 + 构件高亮 | 细部特写 |
| `zoomOut` | 机位拉远 1.6 倍 | 过渡回全景 |

解析容错：剥除 markdown 围栏、截取最外层 JSON 数组、逐字段宽容解析；LLM 失败时回退内置四章模板（概览/正立面/细部/总结），保证功能可用。

## 三、接口

### 1. 生成导览脚本

```
POST /api/models/{id}/tour-script        需登录
Body: { "partNames": ["桥拱", "栏杆", ...] }   可选；前端从 ThreeScene.getPartsList() 上报
Resp: ApiResponse<TourScriptResponse>
      { modelName, chapters: TourChapter[] }
```

缓存：Redis `tour:script:{modelName}`，TTL 24h。模型信息更新后可手动删除该 key 重新生成。

### 2. 语音合成

```
POST /api/tts        需登录
Body: { "text": "...", "voice": "tongtong" | "custom:<id>" }
Resp: audio/wav 二进制
      智谱不可用 → HTTP 503 + { code: 529 }，前端降级浏览器朗读
```

音色来源：
- 预设：`tongtong`（温柔女声）、`xiaochen`（阳光男声）、`langlang`（沉稳播音）
- 克隆：`custom:<custom_voice.id>`，后端查库映射为智谱 voice ID
- `local:*` 前缀由前端本地 speechSynthesis 处理，不发给后端

```
GET /api/tts/voices     需登录
Resp: [{ id, name, desc, source: 'preset'|'cloned'|'local' }]
```

### 3. 声音克隆（管理员）

```
POST /api/admin/voices/upload     multipart file=音频样本（wav/mp3/m4a，≤10MB）
                                  → 智谱 /v4/files (purpose=voice-clone-input) → file_id
POST /api/admin/voices/clone      { voiceName, fileId, previewText }
                                  → 智谱 /v4/voice/clone (model=glm-tts-clone) → 落库 custom_voice
GET    /api/admin/voices          列表
PUT    /api/admin/voices/{id}/status   { status: 'enabled'|'disabled' }
DELETE /api/admin/voices/{id}
POST   /api/admin/voices/{id}/preview   试听（body 可选 { text }）→ audio/wav
```

样本要求：3~30 秒清晰人声；智谱侧需开通声音复刻且账号有余额，否则 clone 返回 5xx 由前端提示。

## 四、前端结构

| 文件 | 职责 |
|---|---|
| `composables/useAiTour.js` | 导览状态机（idle/loading/playing/paused/done），逐章调度动作+运镜+高亮+朗读，防重入推进 |
| `composables/useCameraFlight.js` | `createCameraFlight`（点到点飞行）、`createOrbitFlight`（环绕运镜）；用户拖拽自动中断 |
| `composables/useSpeech.js` | TTS 抽象层：后端代理优先 → 本地 speechSynthesis 降级；音色清单合并（预设+克隆+系统） |
| `components/ThreeScene.vue` | `flyToCamera` / `flyOrbit` / `setExploded` / `highlightPart` / `getPartsList` 供导览调用 |

机位安全：LLM 输出的坐标经 `clampViewpoint` 校正（水平距离 3.5~10、高度 0.8~5），防止穿模或钻到模型底下。

## 五、播放条 UI

- 章节进度点（已讲/当前呼吸光晕/未讲三态）+ `n / m` 计数
- 章节名 + **动作徽标**（环绕展示/分解演示/特写放大/拉远全景/恢复组装），徽标颜色区分镜头类型
- 旁白字幕打字机逐字浮现（每字 28ms 交错动画）
- 控制：暂停/继续、下一章、音色下拉、退出
- 布局：底部居中悬浮，位于统一工具栏（视图操作 | 季节场景 | AI 导览按钮）上方

## 六、配置

`application.yml`：

```yaml
spring:
  deepseek:
    api-key: ${SPRING_DEEPSEEK_API_KEY}      # 导览脚本/构件讲解
  zhipu:
    api-key: ${ZHIPU_API_KEY}                # CogTTS + 声音克隆
    tts-url: https://open.bigmodel.cn/api/paas/v4/audio/speech
    tts-model: cogtts
    clone-url: https://open.bigmodel.cn/api/paas/v4/voice/clone
    clone-model: glm-tts-clone
```
