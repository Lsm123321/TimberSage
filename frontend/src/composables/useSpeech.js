import request from '@/utils/request'
import { ElMessage } from 'element-plus'

/**
 * 语音合成封装。
 * 首选后端 /api/tts 代理（智谱 CogTTS）；后端 503/code=529 或挂了就静默降到
 * 浏览器内置 speechSynthesis（离线可用），不打断导览。
 * speak() 调用后立即返回，朗读结束通过 onEnd 回调通知，导览状态机靠它推进下一章。
 */

// 走智谱接口的预设音色；本地合成音色的 id 都带 local: 前缀
const REMOTE_VOICES = [
  { id: 'tongtong', name: '桐桐 · 温柔女声', source: 'cogtts' },
  { id: 'xiaochen', name: '小陈 · 阳光男声', source: 'cogtts' },
  { id: 'langlang', name: '朗朗 · 沉稳播音', source: 'cogtts' }
]

/** 管理员克隆的音色，登录后从后端拉，只拉一次存着 */
let clonedVoices = null
export async function loadClonedVoices() {
  if (clonedVoices) return clonedVoices
  try {
    const resp = await request({ url: '/tts/voices', method: 'get' })
    const list = resp.data?.data ?? resp.data ?? []
    // 只留克隆音色，预设的那几个 REMOTE_VOICES 里已经有了
    const cloned = list.filter(v => v.source === 'cloned')
    clonedVoices = cloned
    return cloned
  } catch (e) {
    clonedVoices = []
    return []
  }
}

let audioEl = null
let currentSource = null // 'remote' | 'local' | null
let degraded = false // 一旦后端 TTS 不可用，本次会话内不再反复尝试

const ensureAudioEl = () => {
  if (!audioEl) {
    audioEl = new Audio()
    audioEl.preload = 'auto'
  }
  return audioEl
}

/**
 * 获取音色列表。合并：预设智谱音色 + 管理员克隆音色 + 系统内置音色（过滤中文）。
 */
export function getVoiceOptions() {
  const localVoices = []
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    const seen = new Set()
    window.speechSynthesis.getVoices()
      .filter(v => v.lang && v.lang.toLowerCase().startsWith('zh'))
      .forEach(v => {
        if (seen.has(v.name)) return
        seen.add(v.name)
        localVoices.push({ id: 'local:' + v.name, name: v.name + ' · 本地', source: 'local' })
      })
  }
  // 系统音色读取是异步的，首次可能为空；调用方可在 voiceschanged 后重取
  return [...REMOTE_VOICES, ...(clonedVoices || []), ...localVoices]
}

export function onVoicesChanged(cb) {
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.addEventListener('voiceschanged', cb)
  }
}

/** 停止当前朗读（远程音频与本地合成都要停） */
export function stopSpeaking() {
  if (audioEl) {
    audioEl.pause()
    audioEl.src = ''
  }
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.cancel()
  }
  currentSource = null
}

/**
 * 朗读一段文本。
 * @param {string} text 要朗读的文本
 * @param {{voiceId?: string, onEnd?: Function, onError?: Function}} opts
 */
export async function speak(text, opts = {}) {
  stopSpeaking()
  const voiceId = opts.voiceId || 'tongtong'

  const tryRemote = async () => {
    // local: 开头的走本地合成，其他（含 custom: 克隆音色）都请求后端
    if (degraded) return false
    if (voiceId.startsWith('local:')) return false
    try {
      const resp = await request({
        url: '/tts',
        method: 'post',
        data: { text, voice: voiceId.replace('local:', '') },
        responseType: 'arraybuffer',
        // 403/503 自己处理，别让 request.js 的全局弹窗跳出来
        validateStatus: null
      })
      // 返回的是 JSON 说明出错了（正常应该是音频流）
      const ct = resp.headers?.['content-type'] || ''
      if (ct.includes('application/json')) {
        const text = new TextDecoder().decode(resp.data)
        const json = JSON.parse(text)
        if (json.code === 529) {
          degraded = true
          return false
        }
        throw new Error(json.message || 'TTS 失败')
      }
      const blob = new Blob([resp.data], { type: 'audio/wav' })
      const url = URL.createObjectURL(blob)
      const audio = ensureAudioEl()
      audio.src = url
      currentSource = 'remote'
      audio.onended = () => {
        URL.revokeObjectURL(url)
        currentSource = null
        opts.onEnd?.()
      }
      audio.onerror = () => {
        currentSource = null
        opts.onError?.()
      }
      await audio.play()
      return true
    } catch (e) {
      // 后端 503 等失败都算降级信号，这次会话不再试远程
      degraded = true
      return false
    }
  }

  const speakLocal = () => {
    if (typeof window === 'undefined' || !window.speechSynthesis) {
      opts.onError?.()
      return
    }
    const synth = window.speechSynthesis
    const utter = new SpeechSynthesisUtterance(text)
    utter.lang = 'zh-CN'
    if (voiceId.startsWith('local:')) {
      const name = voiceId.slice(6)
      const v = synth.getVoices().find(v => v.name === name)
      if (v) utter.voice = v
    } else {
      // 远程音色不可用，本地挑一个中文音色：优先 Huihui（女声），否则第一个中文
      const zh = synth.getVoices().filter(v => v.lang && v.lang.toLowerCase().startsWith('zh'))
      const pick = zh.find(v => /huihui|xiaoxiao/i.test(v.name)) || zh[0]
      if (pick) utter.voice = pick
    }
    utter.rate = 1.0
    utter.onend = () => {
      currentSource = null
      opts.onEnd?.()
    }
    utter.onerror = () => {
      currentSource = null
      opts.onError?.()
    }
    currentSource = 'local'
    synth.speak(utter)
  }

  const remoteOk = await tryRemote()
  if (!remoteOk && currentSource !== 'remote') {
    speakLocal()
  }
}

/** 是否正在朗读 */
export function isSpeaking() {
  return currentSource !== null
}
