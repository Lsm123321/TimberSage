/** 浮窗与画布上沿 / 锚点间隙（与 CSS transform 中 -100% - gap 一致） */
export const PART_FLOAT_ANCHOR_GAP = 14
/** 浮窗与可视区域边缘的最小留白 */
export const PART_FLOAT_VIEW_PAD = 8

/**
 * 根据锚点上下可用空间，选择浮窗在锚点「上方」或「下方」展开。
 * @param {number} anchorY 锚点 Y（相对画布包裹层）
 * @param {number} wrapHeight 画布包裹层高度
 * @param {number} cardHeight 浮窗高度（估算或 offsetHeight）
 * @returns {'above' | 'below'}
 */
export function choosePartFloatPlacement(anchorY, wrapHeight, cardHeight) {
  if (wrapHeight <= 0) return 'above'
  const h = Math.max(cardHeight, 120)
  const need = h + PART_FLOAT_ANCHOR_GAP
  const spaceAbove = anchorY - PART_FLOAT_VIEW_PAD
  const spaceBelow = wrapHeight - anchorY - PART_FLOAT_VIEW_PAD
  if (spaceAbove >= need) return 'above'
  if (spaceBelow >= need) return 'below'
  return spaceBelow >= spaceAbove ? 'below' : 'above'
}
