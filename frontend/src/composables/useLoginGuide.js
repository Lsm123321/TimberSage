import { reactive } from 'vue'

/**
 * 登录引导弹窗的全局状态。
 *
 * 用途：项目允许未登录用户浏览（只读），但点赞、收藏、评论、下载、改资料这些写操作必须登录。
 * 以前各个页面遇到未登录都是各自 ElMessage.warning('请先登录') 就结束，
 * 用户不知道下一步该去哪。现在统一弹一个带「去登录 / 先看看」两个按钮的弹窗。
 *
 * 用法：
 *   const { openLoginGuide } = useLoginGuide()
 *   if (!token) { openLoginGuide('点赞'); return }
 */
const state = reactive({
  visible: false,
  action: '',
  redirect: ''
})

const ACTION_HINTS = {
  点赞: '登录后才能点赞，你的点赞记录会保存在个人中心。',
  收藏: '登录后才能收藏，收藏的模型会出现在「个人中心 → 我的收藏」。',
  评论: '登录后才能发表评论，也可以回复其他人的留言。',
  下载: '登录后才能下载模型文件。',
  上传: '登录后才能上传模型，需要先申请成为贡献者。',
  纠错: '登录后才能提交纠错反馈，审核通过后会公开展示。',
  修改资料: '登录后才能修改昵称、邮箱与密码。',
  default: '登录后即可使用完整功能，浏览内容不需要登录。'
}

/** 打开登录引导弹窗 */
export const openLoginGuide = (action = '') => {
  state.action = action
  state.visible = true
}

export const closeLoginGuide = () => {
  state.visible = false
}

/** 弹窗里展示的说明文案，按动作给出更具体的理由 */
export const getLoginHint = (action) => {
  return ACTION_HINTS[action] || ACTION_HINTS.default
}

export const useLoginGuide = () => ({
  state,
  openLoginGuide,
  closeLoginGuide,
  getLoginHint
})
