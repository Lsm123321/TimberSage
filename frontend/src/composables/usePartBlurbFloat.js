/** 与后端 PartBlurbResponse.disclaimer 语义对齐（缓存降级时使用） */
export const PART_FLOAT_AI_DISCLAIMER_FALLBACK =
  '以上内容为智能生成：基于本平台模型库字段与构件节点信息整理，仅供参观与学习参考，不构成考古定论、修缮依据或学术论文引用。'

/** 未调用 AI / 使用界面默认文案时的简要提示 */
export const PART_FLOAT_FALLBACK_HINT =
  '以上为系统默认说明（未生成个性化介绍）；登录后可生成带生成时间与依据标注的介绍。'

export function formatPartBlurbGeneratedAt(iso) {
  if (!iso || typeof iso !== 'string') return ''
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'Asia/Shanghai'
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

/** @param {unknown} data API data 字段 */
export function normalizePartBlurbPayload(data) {
  if (!data || typeof data !== 'object') return null
  const blurb = data.blurb
  if (typeof blurb !== 'string' || !blurb.trim()) return null
  const sourcesRaw = Array.isArray(data.sources) ? data.sources : []
  const sources = sourcesRaw
    .filter((s) => s && typeof s === 'object')
    .map((s) => ({
      label: typeof s.label === 'string' ? s.label : '',
      detail: typeof s.detail === 'string' ? s.detail : ''
    }))
    .filter((s) => s.label || s.detail)

  return {
    blurb: blurb.trim(),
    generatedAt: typeof data.generatedAt === 'string' ? data.generatedAt : '',
    sources,
    disclaimer: typeof data.disclaimer === 'string' ? data.disclaimer : ''
  }
}
