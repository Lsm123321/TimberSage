import * as THREE from 'three'

/**
 * 相机飞行：把 camera.position 和 controls.target 从当前值平滑插到目标值。
 * ThreeScene.flyToCamera 调用；用户中途一拖拽（OrbitControls start 事件）就放弃本次飞行。
 *
 * easeInOutQuad 缓动，起停都柔和；每帧直接写 target 再 update，
 * 不然 damping 会把镜头往回拉。
 */
export function createCameraFlight(camera, controls) {
  let rafId = null
  let active = false
  let userInterrupted = false

  const abort = () => {
    userInterrupted = true
  }

  /**
   * @param {{pos:[number,number,number], target:[number,number,number]}} to 目标机位
   * @param {number} durationSec 飞行时长（秒）
   * @param {{onDone?:Function, onInterrupt?:Function}} cb 回调
   */
  const flyTo = (to, durationSec = 8, cb = {}) => {
    cancel()
    active = true
    userInterrupted = false

    const fromPos = camera.position.clone()
    const toPos = new THREE.Vector3(to.pos[0], to.pos[1], to.pos[2])
    const fromTarget = controls.target.clone()
    const toTarget = new THREE.Vector3(to.target[0], to.target[1], to.target[2])

    // 起终点几乎重合就不用飞了，直接算完成
    if (fromPos.distanceTo(toPos) < 0.01 && fromTarget.distanceTo(toTarget) < 0.01) {
      active = false
      cb.onDone?.()
      return
    }

    // 监听用户拖拽：一旦用户手动操作，立即放弃本次飞行
    controls.addEventListener('start', abort)
    const startTime = performance.now()
    const durMs = Math.max(800, durationSec * 1000)

    const tick = () => {
      if (userInterrupted) {
        controls.removeEventListener('start', abort)
        active = false
        rafId = null
        cb.onInterrupt?.()
        return
      }
      const t = Math.min(1, (performance.now() - startTime) / durMs)
      // easeInOutQuad 缓动
      const k = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2

      camera.position.lerpVectors(fromPos, toPos, k)
      controls.target.lerpVectors(fromTarget, toTarget, k)
      controls.update()

      if (t < 1) {
        rafId = requestAnimationFrame(tick)
      } else {
        controls.removeEventListener('start', abort)
        active = false
        rafId = null
        cb.onDone?.()
      }
    }
    rafId = requestAnimationFrame(tick)
  }

  /** 立即终止当前飞行（不带用户中断语义） */
  const cancel = () => {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    if (active) {
      controls.removeEventListener('start', abort)
      active = false
    }
  }

  const isActive = () => active

  return { flyTo, cancel, isActive }
}

/**
 * 环绕运镜：相机绕注视点公转指定角度（AI 导览的 orbit 动作用）。
 * 与 flyTo 同款中断/回调语义；radius/height 由调用方给出。
 */
export function createOrbitFlight(camera, controls) {
  let rafId = null
  let active = false
  let userInterrupted = false

  const abort = () => { userInterrupted = true }

  /**
   * @param {{target:number[], radius:number, height:number, degrees:number}} opts
   * @param {number} durationSec
   * @param {{onDone?:Function, onInterrupt?:Function}} cb
   */
  const flyOrbit = (opts, durationSec = 10, cb = {}) => {
    cancel()
    active = true
    userInterrupted = false

    const target = new THREE.Vector3(...(opts.target || [0, 0, 0]))
    const radius = Math.max(3, opts.radius || 6)
    const height = opts.height ?? 2.5
    const totalDeg = opts.degrees ?? 360

    // 起始角从当前机位反推，不然环绕一开始镜头会跳一下
    const startPos = camera.position.clone()
    const startAngle = Math.atan2(startPos.z - target.z, startPos.x - target.x)

    controls.addEventListener('start', abort)
    controls.target.copy(target)
    const startTime = performance.now()
    const durMs = Math.max(1000, durationSec * 1000)

    const tick = () => {
      if (userInterrupted) {
        controls.removeEventListener('start', abort)
        active = false
        rafId = null
        cb.onInterrupt?.()
        return
      }
      const t = Math.min(1, (performance.now() - startTime) / durMs)
      const k = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2
      const angle = startAngle + (totalDeg * Math.PI / 180) * k

      camera.position.set(
        target.x + Math.cos(angle) * radius,
        // 绕行中段稍微抬高一点，轨迹有个起伏不那么死板
        height + (startPos.y - height) * (1 - Math.abs(0.5 - k) * 0.4),
        target.z + Math.sin(angle) * radius
      )
      controls.target.copy(target)
      controls.update()

      if (t < 1) {
        rafId = requestAnimationFrame(tick)
      } else {
        controls.removeEventListener('start', abort)
        active = false
        rafId = null
        cb.onDone?.()
      }
    }
    rafId = requestAnimationFrame(tick)
  }

  const cancel = () => {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    if (active) {
      controls.removeEventListener('start', abort)
      active = false
    }
  }

  return { flyOrbit, cancel }
}
