import { ref } from 'vue'
import request from '@/utils/request'
import { speak, stopSpeaking } from '@/composables/useSpeech'

/**
 * AI 语音导览状态机。
 *
 * 流程：取脚本（后端 DeepSeek 生成，带构件清单）→ 逐章播放：
 *   飞行镜头（时长与旁白估算值取平衡）→ 高亮构件（可选）→ TTS 朗读 → 章末停顿 → 下一章。
 * 支持暂停/继续/跳章/停止；导览中用户手动拖拽镜头会中断当前章节的飞行但继续朗读。
 *
 * @param {import('vue').Ref<Object>} threeSceneRef ThreeScene 组件 ref（需暴露 flyToCamera/highlightPart/clearPartHighlight）
 */
export function useAiTour(threeSceneRef) {
  const status = ref('idle') // idle | loading | playing | paused | done
  const chapters = ref([])
  const currentIndex = ref(-1)
  const currentNarration = ref('')
  /** 当前章的讲解动作（explode/assemble/zoomIn/zoomOut/orbit），播放条据此展示动作徽标 */
  const currentAction = ref('')
  const usingFallback = ref(false) // 脚本是否来自内置模板（LLM 失败）

  let paused = false
  let stopped = true
  /** 章节间停顿与旁白兜底计时的句柄，stop 时全部清理 */
  let timers = []

  const clearTimers = () => {
    timers.forEach(t => clearTimeout(t))
    timers = []
  }

  const later = (fn, ms) => timers.push(setTimeout(fn, ms))

  /**
   * 估算旁白朗读时长（秒）：中文语速约 4.5 字/秒，加 1 秒缓冲。
   * TTS 实际完成以 onEnd 为准，这里只用于飞行时长对齐与兜底计时。
   */
  const estimateSpeechSeconds = (text) => Math.max(4, text.length / 4.5 + 1)

  /**
   * 缩放校正：把机位沿"注视点→机位"方向缩放 scale 倍。
   * zoomIn(0.6) 推近、zoomOut(1.6) 拉远，同时限制在安全范围内。
   */
  const applyZoom = (vp, scale) => {
    const t = vp.target || [0, 0, 0]
    const p = vp.pos || [5, 3, 5]
    const np = [
      t[0] + (p[0] - t[0]) * scale,
      Math.max(0.3, t[1] + (p[1] - t[1]) * scale),
      t[2] + (p[2] - t[2]) * scale
    ]
    return clampViewpoint({ pos: np, target: t })
  }

  /** 按构件名模糊匹配 mesh：精确名 → 包含 → 小写包含 */
  const findPartIdByName = (name, parts) => {
    if (!name || !parts) return null
    const hit =
      parts.find(p => p.name === name) ||
      parts.find(p => p.name && p.name.includes(name)) ||
      parts.find(p => p.name && name.includes(p.name)) ||
      parts.find(p => p.name && p.name.toLowerCase().includes(name.toLowerCase()))
    return hit ? hit.id : null
  }

  /**
   * 校正 LLM 给出的机位：限制在归一化模型空间的安全范围内，
   * 防止穿模（太近）或飞到模型底下（太低）。
   */
  const clampViewpoint = (vp) => {
    const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v))
    const pos = vp.pos || [5, 3, 5]
    const target = vp.target || [0, 0, 0]
    const len = Math.hypot(pos[0], pos[2]) || 1
    const safe = { ...vp }
    // 水平距离约束 3.5~10；高度 0.8~5（避免钻到模型底面之下）
    const scale = Math.min(1, Math.max(3.5 / len, Math.min(10 / len, 1)))
    safe.pos = [
      pos[0] * (len >= 3.5 && len <= 10 ? 1 : scale),
      clamp(pos[1], 0.8, 5),
      pos[2] * (len >= 3.5 && len <= 10 ? 1 : scale)
    ]
    safe.target = [clamp(target[0], -4, 4), clamp(target[1], -0.5, 3), clamp(target[2], -4, 4)]
    return safe
  }

  const fetchScript = async (modelId, partNames) => {
    const resp = await request({
      url: `/models/${modelId}/tour-script`,
      method: 'post',
      data: { partNames: partNames || [] }
    })
    const data = resp.data?.data ?? resp.data
    if (data?.chapters?.length) {
      return { chapters: data.chapters, fallback: false }
    }
    throw new Error('empty script')
  }

  /** 开始导览 */
  const start = async (modelId, opts = {}) => {
    if (status.value === 'playing' || status.value === 'loading') return
    stopped = false
    paused = false
    status.value = 'loading'
    chapters.value = []
    currentIndex.value = -1
    currentNarration.value = ''
    usingFallback.value = false
    if (opts.voiceId !== undefined) {
      // 调用方可能传 ref 也可能直接传字符串，都接住
      voiceIdRef.value = typeof opts.voiceId === 'object' ? (opts.voiceId.value || '') : opts.voiceId
    }

    let script
    try {
      const parts = threeSceneRef.value?.getPartsList?.() || []
      script = await fetchScript(modelId, parts.map(p => p.name))
    } catch (e) {
      // 脚本没拿到就走内置的兜底章节，功能不能整个挂掉
      script = { chapters: buildLocalFallback(modelName || ''), fallback: true }
    }
    if (script.fallback) usingFallback.value = true
    if (stopped) return

    chapters.value = script.chapters
    status.value = 'playing'
    playChapter(0)
  }

  let modelName = ''
  const setModelName = (name) => { modelName = name || '' }

  /** 朗读音色设置（由 start 的 opts 传入；音色下拉切换时同步更新） */
  let voiceIdRef = ref('')
  const setVoiceId = (id) => { voiceIdRef.value = id || '' }

  /** LLM 不可用时的本地兜底四章 */
  const buildLocalFallback = (name) => ([
    {
      chapter: '整体概览',
      narration: '欢迎来到智筑云览。让我们环绕这座建筑一周，从整体感受古人的营造智慧。',
      cameraPos: [6, 3, 6], cameraTarget: [0, 0, 0], duration: 10
    },
    {
      chapter: '正立面',
      narration: '来到建筑正面。中轴对称、层次分明的布局，与屋顶柔和的曲线，正是东方美学的精髓。',
      cameraPos: [0, 2, 7], cameraTarget: [0, 0.5, 0], duration: 9
    },
    {
      chapter: '细部观察',
      narration: '靠近观察细部构造。梁枋与构件以榫卯相连，不施一钉，却能屹立数百年。',
      cameraPos: [3, 1.5, 4], cameraTarget: [0, 0.3, 0], duration: 10
    },
    {
      chapter: '总结',
      narration: '一座建筑就是一部凝固的历史。感谢您的聆听，您可以继续自由探索这座模型。',
      cameraPos: [-5, 2.5, 5], cameraTarget: [0, 0, 0], duration: 9
    }
  ])

  const playChapter = (index) => {
    if (stopped) return
    if (index >= chapters.value.length) {
      finish()
      return
    }
    currentIndex.value = index
    const ch = chapters.value[index]
    currentNarration.value = ch.narration
    currentAction.value = ch.action || ''

    // 爆炸/收回动作先做，跟镜头飞行同时进行，画面更连贯
    const scene = threeSceneRef.value
    if (ch.action === 'explode') {
      scene?.setExploded?.(true)
    } else if (ch.action === 'assemble') {
      scene?.setExploded?.(false)
    }

    // 飞行时长取 LLM 建议值和旁白估算值里较小的那个，让镜头先到位等语音
    const speechSec = estimateSpeechSeconds(ch.narration)
    const flySec = Math.min(ch.duration || 8, speechSec)
    let vp = clampViewpoint({ pos: ch.cameraPos, target: ch.cameraTarget })
    // zoomIn/zoomOut 在 LLM 给的坐标上按语义再校正一下推拉距离
    if (ch.action === 'zoomIn') {
      vp = applyZoom(vp, 0.6)
    } else if (ch.action === 'zoomOut') {
      vp = applyZoom(vp, 1.6)
    } else if (ch.action === 'orbit') {
      // 环绕的机位由轨道函数在飞行中生成，这里只要给个起点
      vp = clampViewpoint({ pos: vp.pos, target: vp.target })
    }
    let flightDone = false
    const proceedWhenReady = () => {
      if (stopped || paused) return
      if (flightDone) return
      flightDone = true
    }
    if (scene?.flyToCamera) {
      if (ch.action === 'orbit') {
        scene.flyOrbit({ target: vp.target, radius: Math.hypot(vp.pos[0], vp.pos[2]), height: vp.pos[1], degrees: 360, durationSec: flySec,
          callbacks: { onInterrupt: proceedWhenReady, onDone: proceedWhenReady } })
      } else {
        scene.flyToCamera({ pos: vp.pos, target: vp.target }, flySec, {
          onInterrupt: () => { proceedWhenReady() },
          onDone: () => { proceedWhenReady() }
        })
      }
    } else {
      flightDone = true
    }

    // 本章要高亮的构件（可选）
    scene?.clearPartHighlight?.()
    if (ch.partName) {
      const parts = scene?.getPartsList?.() || []
      const pid = findPartIdByName(ch.partName, parts)
      if (pid) scene.highlightPart(pid)
    }

    // TTS 朗读，读完（或语音挂了走计时兜底）进下一章。
    // advancing 用来挡住 onEnd 和兜底计时双触发，不然会跳章
    let advancing = false
    const nextAfterSpeech = () => {
      if (stopped || paused || advancing) return
      advancing = true
      // 章末停半秒，节奏舒服点
      later(() => playChapter(index + 1), 600)
    }
    speak(ch.narration, {
      voiceId: voiceIdRef.value || undefined,
      onEnd: nextAfterSpeech,
      onError: () => {
        // 语音合成失败（含浏览器不支持）：按估算时长兜底推进
        later(nextAfterSpeech, speechSec * 1000)
      }
    })
    // 再兜一层：有的浏览器静音策略下 onEnd 不会来，估算时长 +2s 强制切章。
    // 前提是没被手动跳章（currentIndex 没变）且还没在推进
    later(() => {
      if (!stopped && !paused && !advancing && currentIndex.value === index) {
        advancing = true
        playChapter(index + 1)
      }
    }, (speechSec + 2.5) * 1000)
  }

  const pause = () => {
    if (status.value !== 'playing') return
    paused = true
    status.value = 'paused'
    stopSpeaking()
    threeSceneRef.value?.cancelCameraFlight?.()
  }

  const resume = () => {
    if (status.value !== 'paused') return
    paused = false
    status.value = 'playing'
    // 从当前章重播（旁白从头朗读，镜头重新飞向该章机位）
    if (currentIndex.value >= 0) {
      playChapter(currentIndex.value)
    }
  }

  /** 跳到下一章（朗读停止，镜头立即飞向下一章机位） */
  const next = () => {
    if (status.value !== 'playing' && status.value !== 'paused') return
    paused = false
    status.value = 'playing'
    stopSpeaking()
    clearTimers()
    playChapter(currentIndex.value + 1)
  }

  const stop = () => {
    stopped = true
    paused = false
    status.value = 'idle'
    stopSpeaking()
    clearTimers()
    threeSceneRef.value?.cancelCameraFlight?.()
    threeSceneRef.value?.clearPartHighlight?.()
    currentNarration.value = ''
    currentIndex.value = -1
  }

  const finish = () => {
    status.value = 'done'
    stopSpeaking()
    threeSceneRef.value?.clearPartHighlight?.()
    currentNarration.value = ''
    // 数秒后回到 idle，让"已完成"状态可感知又不阻塞再次播放
    later(() => {
      if (status.value === 'done') status.value = 'idle'
    }, 3000)
  }

  return {
    status, chapters, currentIndex, currentNarration, currentAction, usingFallback,
    start, pause, resume, next, stop, setModelName, setVoiceId
  }
}
