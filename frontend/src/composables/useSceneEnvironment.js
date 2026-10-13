import * as THREE from 'three'

/**
 * 四季场景环境：天空球渐变、日月光晕、软云漂移、贴地雾带、程序化地面纹理，
 * 粒子带阵风/旋转/落地堆积，每季还有各自的动物点缀（蝶/蜻蜓/雁/鸦）。
 * 所有贴图都用 Canvas 现画，不依赖外部图片资源。
 */

// 用 2D Canvas 画一张竖向渐变纹理，作为天空球的贴图
const makeGradientTexture = (stops) => {
  const canvas = document.createElement('canvas')
  canvas.width = 16
  canvas.height = 256
  const ctx = canvas.getContext('2d')
  const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height)
  stops.forEach(([offset, color]) => gradient.addColorStop(offset, color))
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

// 程序化地面纹理：底色 + 大量随机色斑（春草斑/夏深浅草纹/秋落叶/冬雪堆阴影）
const makeGroundTexture = (base, spots, spotCount = 260) => {
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 512
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = base
  ctx.fillRect(0, 0, 512, 512)
  for (let i = 0; i < spotCount; i++) {
    const x = Math.random() * 512
    const y = Math.random() * 512
    const r = 3 + Math.random() * 14
    ctx.fillStyle = spots[Math.floor(Math.random() * spots.length)]
    ctx.globalAlpha = 0.12 + Math.random() * 0.25
    ctx.beginPath()
    ctx.ellipse(x, y, r, r * (0.5 + Math.random() * 0.6), Math.random() * Math.PI, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.globalAlpha = 1
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = THREE.RepeatWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.repeat.set(6, 6)
  return texture
}

// 软云 sprite：多团高斯模糊椭圆叠出一朵云
const makeCloudTexture = (tint = 'rgba(255,255,255,1)', shade = 'rgba(210,220,235,0.9)') => {
  const w = 256
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = w * 0.6
  const ctx = canvas.getContext('2d')
  // 云底阴影
  for (let i = 0; i < 5; i++) {
    const x = w * (0.25 + Math.random() * 0.5)
    const y = canvas.height * (0.55 + Math.random() * 0.25)
    const r = 30 + Math.random() * 40
    const g = ctx.createRadialGradient(x, y, 2, x, y, r)
    g.addColorStop(0, shade)
    g.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fill()
  }
  // 云顶亮部
  for (let i = 0; i < 7; i++) {
    const x = w * (0.2 + Math.random() * 0.6)
    const y = canvas.height * (0.28 + Math.random() * 0.3)
    const r = 24 + Math.random() * 45
    const g = ctx.createRadialGradient(x, y, 2, x, y, r)
    g.addColorStop(0, tint)
    g.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fill()
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

// 发光圆盘（太阳/月亮）：中心亮核 + 光晕
const makeGlowTexture = (coreColor, haloColor) => {
  const size = 256
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  const g = ctx.createRadialGradient(size / 2, size / 2, 4, size / 2, size / 2, size / 2)
  g.addColorStop(0, coreColor)
  g.addColorStop(0.18, coreColor)
  g.addColorStop(0.35, haloColor)
  g.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, size, size)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

// 粒子 sprite 贴图（花瓣/落叶/雪花/光斑）
const makeParticleTexture = (color, shape = 'circle') => {
  const size = 64
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')

  if (shape === 'petal') {
    ctx.translate(size / 2, size / 2)
    ctx.rotate(Math.PI / 5)
    ctx.scale(0.6, 1)
    const g = ctx.createRadialGradient(0, 0, 2, 0, 0, size / 2)
    g.addColorStop(0, color)
    g.addColorStop(0.75, color)
    g.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.arc(0, 0, size / 2, 0, Math.PI * 2)
    ctx.fill()
  } else if (shape === 'leaf') {
    ctx.translate(size / 2, size / 2)
    ctx.rotate(Math.PI / 6)
    const g = ctx.createLinearGradient(0, -size / 2, 0, size / 2)
    g.addColorStop(0, 'rgba(255,255,255,0)')
    g.addColorStop(0.5, color)
    g.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.ellipse(0, 0, size / 6, size / 2.2, 0, 0, Math.PI * 2)
    ctx.fill()
  } else {
    const g = ctx.createRadialGradient(size / 2, size / 2, 1, size / 2, size / 2, size / 2)
    g.addColorStop(0, '#ffffff')
    g.addColorStop(0.4, color)
    g.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = g
    ctx.fillRect(0, 0, size, size)
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

/**
 * 场景预设 2.0
 * 新增字段：
 * sky.stops:        三段渐变（天顶/地平线/地平线下），层次更真实
 * sun:              发光天体 {pos, core, halo, size}——春东升、夏正午、秋西沉、冬苍白高悬
 * clouds:           云层配置 {count, y, speed, scale, tint, shade, opacity}
 * windGusts:        是否启用阵风（粒子随风强周期起伏）
 * particles.spin:   粒子是否旋转（落叶翻滚/雪花飘摆/花瓣打转）
 * creatures:        生物元素 {type: 'butterfly'|'dragonfly'|'geese'|'crows', count}——各季标志性动物
 * mistBands:        贴地雾带（半透明长条 sprite 缓慢流动，春雨雾/冬雪雾）
 */
export const SCENE_PRESETS = {
  default: {
    key: 'default',
    label: '默认',
    icon: '🖥',
    sky: { stops: [[0, '#eef1f4'], [1, '#f5f5f5']] },
    background: '#f5f5f5',
    fog: null,
    lights: { ambient: 0xffffff, ambientIntensity: 0.6, main: 0xffffff, mainIntensity: 0.8, fill: 0xffffff, fillIntensity: 0.3 },
    ground: null,
    sun: null,
    clouds: null,
    particles: null
  },
  spring: {
    key: 'spring',
    label: '春·樱雨',
    icon: '🌸',
    sky: { stops: [[0, '#d9b8cc'], [0.62, '#f3dce6'], [1, '#fdf0f4']] },
    fog: { color: '#eddbe4', near: 9, far: 46 },
    lights: { ambient: 0xf3e3ec, ambientIntensity: 0.85, main: 0xffe8f0, mainIntensity: 0.62, fill: 0xe8d4e0, fillIntensity: 0.5 },
    ground: { base: '#aecb92', spots: ['#c3dda4', '#9ab87e', '#d8ecc0'] },
    // 春：太阳在东侧低角度，柔粉光晕（清晨感）
    sun: { pos: [60, 14, -35], core: 'rgba(255,240,246,1)', halo: 'rgba(255,190,220,0.55)', size: 42 },
    // 高天絮状薄云，随风缓移
    clouds: { count: 7, y: 26, speed: 0.5, scale: 22, tint: 'rgba(255,250,252,0.95)', shade: 'rgba(225,200,215,0.75)', opacity: 0.75 },
    // 贴地薄雾带缓慢流动（烟雨江南的湿润感）
    mistBands: { count: 5, y: -1.2, color: 'rgba(245,230,240,0.55)', width: 30, height: 2.2, speed: 0.35 },
    // 粉蝶双飞：3 只蝴蝶绕模型盘旋
    creatures: { type: 'butterfly', count: 3 },
    particles: { texture: { color: 'rgba(255,150,190,0.95)', shape: 'petal' }, count: 240, size: 0.3, color: 0xff96be, fallSpeed: 0.9, sway: 1.6, opacity: 0.95, spin: true, windGusts: true, groundLife: 2.5 }
  },
  summer: {
    key: 'summer',
    label: '夏·晴空',
    icon: '☀️',
    sky: { stops: [[0, '#1a55ac'], [0.6, '#5fa3e0'], [1, '#aadcf5']] },
    fog: null,
    lights: { ambient: 0xf2f8ff, ambientIntensity: 0.65, main: 0xfff8e8, mainIntensity: 1.35, fill: 0xbfe0ff, fillIntensity: 0.3 },
    ground: { base: '#4f8f3e', spots: ['#61a24a', '#3d7231', '#74b257'] },
    // 夏：太阳正午高悬，亮白光晕刺眼
    sun: { pos: [8, 75, 12], core: 'rgba(255,255,250,1)', halo: 'rgba(255,240,190,0.65)', size: 55 },
    // 大朵积云，移动稍快，边缘亮白
    clouds: { count: 9, y: 32, speed: 1.1, scale: 30, tint: 'rgba(255,255,255,1)', shade: 'rgba(190,205,225,0.85)', opacity: 0.92 },
    // 蜻蜓点水：4 只蜻蜓低空疾飞
    creatures: { type: 'dragonfly', count: 4 },
    particles: { texture: { color: 'rgba(255,240,180,0.8)', shape: 'circle' }, count: 55, size: 0.1, color: 0xfff0b4, fallSpeed: 0.12, sway: 0.35, opacity: 0.55, spin: false, windGusts: true, groundLife: 0 }
  },
  autumn: {
    key: 'autumn',
    label: '秋·斜阳',
    icon: '🍂',
    sky: { stops: [[0, '#b26a3b'], [0.55, '#e8a960'], [1, '#f9e2b8']] },
    fog: { color: '#eccf9f', near: 16, far: 66 },
    lights: { ambient: 0xffe4c0, ambientIntensity: 0.7, main: 0xffab5e, mainIntensity: 1.0, fill: 0xffd2a0, fillIntensity: 0.4 },
    ground: { base: '#c29a52', spots: ['#a87f3e', '#d9b470', '#8f6a33', '#b5442e'] },
    // 秋：太阳西沉贴地平线，橙红大光晕（斜阳感）
    sun: { pos: [-70, 6, 25], core: 'rgba(255,220,160,1)', halo: 'rgba(255,140,60,0.6)', size: 60 },
    // 高天薄霞，染上暖色
    clouds: { count: 6, y: 24, speed: 0.7, scale: 26, tint: 'rgba(255,220,180,0.9)', shade: 'rgba(190,130,90,0.6)', opacity: 0.7 },
    // 远处薄暮雾带
    mistBands: { count: 4, y: -1.0, color: 'rgba(250,220,180,0.4)', width: 36, height: 2.6, speed: 0.25 },
    // 南飞雁阵：一队大雁排成人字掠过高空
    creatures: { type: 'geese', count: 7 },
    particles: { texture: { color: 'rgba(230,120,40,0.95)', shape: 'leaf' }, count: 130, size: 0.28, color: 0xe67828, fallSpeed: 0.7, sway: 2.0, opacity: 0.95, spin: true, windGusts: true, groundLife: 3 }
  },
  winter: {
    key: 'winter',
    label: '冬·落雪',
    icon: '❄️',
    sky: { stops: [[0, '#7c93ab'], [0.6, '#b9c7d4'], [1, '#e8edf2']] },
    fog: { color: '#dfe5ea', near: 10, far: 48 },
    lights: { ambient: 0xe4ecf4, ambientIntensity: 0.85, main: 0xf0f6ff, mainIntensity: 0.6, fill: 0xdce8f4, fillIntensity: 0.5 },
    ground: { base: '#eef2f6', spots: ['#dde5ec', '#ffffff', '#cdd8e2'] },
    // 冬：太阳苍白高悬，几乎无光晕（阴冷感）
    sun: { pos: [30, 55, -25], core: 'rgba(245,248,252,0.95)', halo: 'rgba(220,230,240,0.4)', size: 34 },
    // 低垂灰云，移动缓慢，压迫感
    clouds: { count: 8, y: 20, speed: 0.35, scale: 34, tint: 'rgba(240,244,248,0.95)', shade: 'rgba(160,175,190,0.8)', opacity: 0.85 },
    // 贴地雪雾流动（风雪交加）
    mistBands: { count: 6, y: -1.3, color: 'rgba(235,242,248,0.6)', width: 34, height: 2.4, speed: 0.6 },
    // 寒鸦掠过：2 只乌鸦剪影盘旋
    creatures: { type: 'crows', count: 2 },
    particles: { texture: { color: 'rgba(255,255,255,0.95)', shape: 'circle' }, count: 320, size: 0.2, color: 0xffffff, fallSpeed: 1.4, sway: 0.9, opacity: 0.95, spin: true, windGusts: true, groundLife: 4 }
  }
}

export const SCENE_KEYS = Object.keys(SCENE_PRESETS)

/** 供 UI 使用的场景选项列表（不含实现细节） */
export const getSceneOptions = () =>
  SCENE_KEYS.map((key) => ({ key, label: SCENE_PRESETS[key].label, icon: SCENE_PRESETS[key].icon }))

/**
 * 场景环境管理器：管理天空球、日月光晕、云层、地面、粒子等全部元素的构建与销毁
 */
export class SceneEnvironment {
  constructor(scene) {
    this.scene = scene
    this.currentKey = 'default'
    this.skyMesh = null
    this.sunSprite = null
    this.cloudSprites = []
    this.cloudMeta = null
    this.groundMesh = null
    this.particlePoints = null
    this.particleMeta = null
    this.mistSprites = []
    this.mistMeta = null
    this.creatureGroup = null
    this.creatureMeta = null
    this._fadeState = null
    this._time = 0
    // 阵风：风强随时间正弦起伏，叠加随机抖动
    this._windPhase = Math.random() * 10
    this._groundLife = [] // 落地粒子的剩余停留时间
  }

  _buildSky(preset) {
    const texture = makeGradientTexture(preset.sky.stops)
    const geometry = new THREE.SphereGeometry(400, 24, 16)
    const material = new THREE.MeshBasicMaterial({
      map: texture,
      side: THREE.BackSide,
      depthWrite: false,
      fog: false
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.renderOrder = -10
    return mesh
  }

  _buildSun(preset) {
    const texture = makeGlowTexture(preset.sun.core, preset.sun.halo)
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthWrite: false,
      fog: false
    })
    const sprite = new THREE.Sprite(material)
    sprite.scale.setScalar(preset.sun.size)
    sprite.position.set(...preset.sun.pos)
    sprite.renderOrder = -9
    return sprite
  }

  _buildClouds(preset) {
    const cfg = preset.clouds
    const group = []
    const texture = makeCloudTexture(cfg.tint, cfg.shade)
    for (let i = 0; i < cfg.count; i++) {
      const material = new THREE.SpriteMaterial({
        map: texture,
        transparent: true,
        opacity: cfg.opacity * (0.75 + Math.random() * 0.35),
        depthWrite: false,
        fog: false
      })
      const sprite = new THREE.Sprite(material)
      const s = cfg.scale * (0.7 + Math.random() * 0.7)
      sprite.scale.set(s, s * 0.55, 1)
      // 绕场景均匀分布
      const angle = (i / cfg.count) * Math.PI * 2 + Math.random() * 0.6
      const radius = 90 + Math.random() * 120
      sprite.position.set(Math.cos(angle) * radius, cfg.y + Math.random() * 10, Math.sin(angle) * radius)
      sprite.renderOrder = -8
      sprite.userData.driftAngle = Math.random() * Math.PI * 2
      group.push(sprite)
    }
    return group
  }

  _buildGround(preset) {
    const texture = makeGroundTexture(preset.ground.base, preset.ground.spots)
    const geometry = new THREE.CircleGeometry(60, 48)
    const material = new THREE.MeshStandardMaterial({
      map: texture,
      roughness: 0.95,
      metalness: 0
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.rotation.x = -Math.PI / 2
    mesh.position.y = -1.6
    mesh.receiveShadow = true
    return mesh
  }

  _buildParticles(preset) {
    const cfg = preset.particles
    const count = cfg.count
    const positions = new Float32Array(count * 3)
    const radius = 26
    const top = 16
    const bottom = -1.5
    for (let i = 0; i < count; i++) {
      positions[i * 3] = (Math.random() - 0.5) * radius * 2
      positions[i * 3 + 1] = bottom + Math.random() * (top - bottom)
      positions[i * 3 + 2] = (Math.random() - 0.5) * radius * 2
    }
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

    const material = new THREE.PointsMaterial({
      size: cfg.size,
      map: makeParticleTexture(cfg.texture.color, cfg.texture.shape),
      transparent: true,
      opacity: cfg.opacity,
      depthWrite: false,
      color: cfg.color,
      sizeAttenuation: true
    })
    const points = new THREE.Points(geometry, material)
    points.userData.area = { radius, top, bottom }
    // groundLife > 0：粒子落地后停留一段时间再重生（雪覆盖/花瓣铺地效果）
    points.userData.landed = cfg.groundLife ? new Float32Array(count) : null
    return points
  }

  /** 贴地雾带：半透明长条 sprite 贴着地面缓慢流动 */
  _buildMist(preset) {
    const cfg = preset.mistBands
    const group = []
    // 雾带用横向拉长的柔和高斯纹理
    const texture = makeCloudTexture(cfg.color, cfg.color)
    for (let i = 0; i < cfg.count; i++) {
      const material = new THREE.SpriteMaterial({
        map: texture,
        transparent: true,
        opacity: 0.35 + Math.random() * 0.3,
        depthWrite: false
      })
      const sprite = new THREE.Sprite(material)
      sprite.scale.set(cfg.width * (0.7 + Math.random() * 0.6), cfg.height, 1)
      const angle = (i / cfg.count) * Math.PI * 2 + Math.random()
      const radius = 14 + Math.random() * 26
      sprite.position.set(Math.cos(angle) * radius, cfg.y, Math.sin(angle) * radius)
      sprite.userData.flowAngle = Math.random() * Math.PI * 2
      group.push(sprite)
    }
    return group
  }

  /**
   * 生物元素：每季标志性动物，全部程序化生成
   * - butterfly：粉蝶（双翼 canvas 贴图，扑翼=scale 抖动，绕花丛盘旋）
   * - dragonfly：蜻蜓（细长体+透明翼，低空直线疾飞+急转）
   * - geese：南飞雁阵（v 字队列的黑点队列，缓慢横越高空）
   * - crows：寒鸦（黑色鸟形剪影，大幅盘旋）
   */
  _buildCreatures(preset) {
    const cfg = preset.creatures
    const group = new THREE.Group()
    const meta = { type: cfg.type, items: [] }

    const makeBirdTexture = (color) => {
      // 简易鸟形：两翼 V 字 + 身体
      const c = document.createElement('canvas')
      c.width = 64; c.height = 32
      const ctx = c.getContext('2d')
      ctx.strokeStyle = color
      ctx.lineWidth = 4
      ctx.lineCap = 'round'
      ctx.beginPath()
      ctx.moveTo(4, 6); ctx.quadraticCurveTo(24, 20, 32, 16)
      ctx.quadraticCurveTo(40, 20, 60, 6)
      ctx.stroke()
      ctx.beginPath()
      ctx.ellipse(32, 18, 7, 4, 0, 0, Math.PI * 2)
      ctx.fillStyle = color
      ctx.fill()
      return new THREE.CanvasTexture(c)
    }

    const makeButterflyTexture = () => {
      const c = document.createElement('canvas')
      c.width = 64; c.height = 64
      const ctx = c.getContext('2d')
      // 四片粉翅
      ctx.fillStyle = 'rgba(255,170,210,0.95)'
      ctx.beginPath(); ctx.ellipse(22, 22, 16, 20, -0.4, 0, Math.PI * 2); ctx.fill()
      ctx.beginPath(); ctx.ellipse(42, 22, 16, 20, 0.4, 0, Math.PI * 2); ctx.fill()
      ctx.fillStyle = 'rgba(255,140,190,0.95)'
      ctx.beginPath(); ctx.ellipse(24, 44, 12, 14, 0.3, 0, Math.PI * 2); ctx.fill()
      ctx.beginPath(); ctx.ellipse(40, 44, 12, 14, -0.3, 0, Math.PI * 2); ctx.fill()
      ctx.fillStyle = '#6b4a5a'
      ctx.beginPath(); ctx.ellipse(32, 32, 3, 12, 0, 0, Math.PI * 2); ctx.fill()
      return new THREE.CanvasTexture(c)
    }

    const makeDragonflyTexture = () => {
      const c = document.createElement('canvas')
      c.width = 64; c.height = 64
      const ctx = c.getContext('2d')
      // 细长青色身体
      ctx.strokeStyle = '#3a9a8c'
      ctx.lineWidth = 3
      ctx.beginPath(); ctx.moveTo(32, 12); ctx.lineTo(32, 52); ctx.stroke()
      // 两对透明翼
      ctx.fillStyle = 'rgba(200,240,235,0.7)'
      ctx.beginPath(); ctx.ellipse(18, 22, 16, 5, -0.2, 0, Math.PI * 2); ctx.fill()
      ctx.beginPath(); ctx.ellipse(46, 22, 16, 5, 0.2, 0, Math.PI * 2); ctx.fill()
      ctx.beginPath(); ctx.ellipse(18, 30, 16, 5, 0.2, 0, Math.PI * 2); ctx.fill()
      ctx.beginPath(); ctx.ellipse(46, 30, 16, 5, -0.2, 0, Math.PI * 2); ctx.fill()
      return new THREE.CanvasTexture(c)
    }

    if (cfg.type === 'butterfly') {
      const tex = makeButterflyTexture()
      for (let i = 0; i < cfg.count; i++) {
        const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false })
        const s = new THREE.Sprite(mat)
        s.scale.set(0.5, 0.5, 1)
        s.userData = {
          phase: Math.random() * Math.PI * 2,
          radius: 3 + Math.random() * 4,
          height: 0.5 + Math.random() * 1.6,
          speed: 0.5 + Math.random() * 0.4
        }
        group.add(s)
        meta.items.push(s)
      }
    } else if (cfg.type === 'dragonfly') {
      const tex = makeDragonflyTexture()
      for (let i = 0; i < cfg.count; i++) {
        const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false })
        const s = new THREE.Sprite(mat)
        s.scale.set(0.42, 0.42, 1)
        s.userData = {
          phase: Math.random() * Math.PI * 2,
          radius: 5 + Math.random() * 6,
          height: 0.3 + Math.random() * 1.2,
          speed: 1.1 + Math.random() * 0.5
        }
        group.add(s)
        meta.items.push(s)
      }
    } else if (cfg.type === 'geese') {
      // 雁阵：一只领头 + 后面两列斜排，整队横越高空
      const tex = makeBirdTexture('rgba(50,45,50,0.9)')
      for (let i = 0; i < cfg.count; i++) {
        const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false, opacity: 0.9 })
        const s = new THREE.Sprite(mat)
        s.scale.set(0.7, 0.35, 1)
        // v 字队列偏移：第 i 只在领头的侧后方
        const row = Math.ceil(i / 2)
        const side = i % 2 === 0 ? 1 : -1
        s.userData = {
          slotOffset: { x: side * row * 1.2, y: -row * 0.5 },
          headPhase: Math.random() * 10,
          index: i
        }
        group.add(s)
        meta.items.push(s)
      }
      // 雁队整体的位置由 update 里按时间推进
      meta.formation = { x: -60, y: 24, z: -30, dirX: 1, dirZ: 0.15, speed: 6 }
    } else if (cfg.type === 'crows') {
      const tex = makeBirdTexture('rgba(20,20,25,0.95)')
      for (let i = 0; i < cfg.count; i++) {
        const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false })
        const s = new THREE.Sprite(mat)
        s.scale.set(0.8, 0.4, 1)
        s.userData = {
          phase: Math.random() * Math.PI * 2,
          radius: 18 + Math.random() * 14,
          height: 12 + Math.random() * 6,
          speed: 0.25 + Math.random() * 0.15
        }
        group.add(s)
        meta.items.push(s)
      }
    }

    return { group, meta }
  }

  apply(key) {
    const preset = SCENE_PRESETS[key] || SCENE_PRESETS.default
    this.currentKey = preset.key

    // --- 天空 ---
    if (!this.skyMesh) {
      this.skyMesh = this._buildSky(preset)
      this.scene.add(this.skyMesh)
    } else {
      const old = this.skyMesh.material.map
      this.skyMesh.material.map = makeGradientTexture(preset.sky.stops)
      this.skyMesh.material.needsUpdate = true
      if (old) old.dispose()
    }

    // --- 太阳/月亮 ---
    if (this.sunSprite) {
      this.scene.remove(this.sunSprite)
      this.sunSprite.material.map?.dispose()
      this.sunSprite.material.dispose()
      this.sunSprite = null
    }
    if (preset.sun) {
      this.sunSprite = this._buildSun(preset)
      this.scene.add(this.sunSprite)
    }

    // --- 云层 ---
    this.cloudSprites.forEach(s => {
      this.scene.remove(s)
      s.material.dispose()
    })
    this.cloudSprites = []
    if (preset.clouds) {
      this.cloudMeta = preset.clouds
      this.cloudSprites = this._buildClouds(preset)
      this.cloudSprites.forEach(s => this.scene.add(s))
    } else {
      this.cloudMeta = null
    }

    // --- 背景 & 雾 ---
    this.scene.background = new THREE.Color(preset.sky.stops[preset.sky.stops.length - 1][1])
    if (preset.fog) {
      if (!this.scene.fog) {
        this.scene.fog = new THREE.Fog(preset.fog.color, preset.fog.near, preset.fog.far)
      }
    } else {
      this.scene.fog = null
    }

    // --- 地面 ---
    if (preset.ground) {
      if (this.groundMesh) {
        // 换纹理而非重建（保留 mesh）
        const old = this.groundMesh.material.map
        this.groundMesh.material.map = makeGroundTexture(preset.ground.base, preset.ground.spots)
        this.groundMesh.material.needsUpdate = true
        if (old) old.dispose()
      } else {
        this.groundMesh = this._buildGround(preset)
        this.scene.add(this.groundMesh)
      }
    } else if (this.groundMesh) {
      this.scene.remove(this.groundMesh)
      this.groundMesh.geometry.dispose()
      this.groundMesh.material.map?.dispose()
      this.groundMesh.material.dispose()
      this.groundMesh = null
    }

    // --- 粒子 ---
    if (this.particlePoints) {
      this.scene.remove(this.particlePoints)
      this.particlePoints.geometry.dispose()
      this.particlePoints.material.map?.dispose()
      this.particlePoints.material.dispose()
      this.particlePoints = null
    }
    this._groundLife = []
    if (preset.particles) {
      this.particleMeta = preset.particles
      this.particlePoints = this._buildParticles(preset)
      this.scene.add(this.particlePoints)
    }

    // --- 贴地雾带 ---
    this.mistSprites.forEach(s => {
      this.scene.remove(s)
      s.material.map?.dispose()
      s.material.dispose()
    })
    this.mistSprites = []
    if (preset.mistBands) {
      this.mistMeta = preset.mistBands
      this.mistSprites = this._buildMist(preset)
      this.mistSprites.forEach(s => this.scene.add(s))
    } else {
      this.mistMeta = null
    }

    // --- 生物元素 ---
    if (this.creatureGroup) {
      this.scene.remove(this.creatureGroup)
      this.creatureGroup.traverse(o => {
        if (o.material) {
          o.material.map?.dispose()
          o.material.dispose()
        }
      })
      this.creatureGroup = null
      this.creatureMeta = null
    }
    if (preset.creatures) {
      const { group, meta } = this._buildCreatures(preset)
      this.creatureGroup = group
      this.creatureMeta = meta
      this.scene.add(group)
    }

    this._startFade(preset)
    return preset
  }

  _startFade(preset) {
    this._fadeState = {
      t: 0,
      from: {
        bg: this.scene.background.clone(),
        fogColor: this.scene.fog ? this.scene.fog.color.clone() : null,
        ground: this.groundMesh ? this.groundMesh.material.color.clone() : null
      },
      to: preset
    }
  }

  /** 由 ThreeScene 的 animate 循环调用：dt 为帧间隔秒数 */
  update(dt) {
    this._time += dt

    // --- 云层漂移：绕场景中心缓慢公转 + 轻微上下浮动 ---
    if (this.cloudMeta && this.cloudSprites.length) {
      const { speed } = this.cloudMeta
      this.cloudSprites.forEach((s, i) => {
        s.userData.driftAngle += speed * dt * 0.03
        const radius = Math.hypot(s.position.x, s.position.z)
        s.position.x = Math.cos(s.userData.driftAngle) * radius
        s.position.z = Math.sin(s.userData.driftAngle) * radius
        s.position.y += Math.sin(this._time * 0.4 + i * 1.7) * dt * 0.25
      })
    }

    // --- 贴地雾带流动：绕场缓移 + 呼吸式透明度起伏 ---
    if (this.mistMeta && this.mistSprites.length) {
      this.mistSprites.forEach((s, i) => {
        s.userData.flowAngle += this.mistMeta.speed * dt * 0.05
        const radius = Math.hypot(s.position.x, s.position.z)
        s.position.x = Math.cos(s.userData.flowAngle) * radius
        s.position.z = Math.sin(s.userData.flowAngle) * radius
        s.material.opacity = 0.3 + 0.25 * (0.5 + 0.5 * Math.sin(this._time * 0.5 + i * 2.1))
      })
    }

    // --- 生物元素动画 ---
    if (this.creatureMeta && this.creatureGroup) {
      const { type, items } = this.creatureMeta
      if (type === 'butterfly') {
        // 粉蝶：绕中心螺旋盘旋 + 上下起伏 + 扑翼（scale 抖动）
        items.forEach((s, i) => {
          const u = s.userData
          u.phase += u.speed * dt
          s.position.set(
            Math.cos(u.phase) * u.radius,
            u.height + Math.sin(this._time * 2.2 + i * 2) * 0.35,
            Math.sin(u.phase * 0.8) * u.radius * 0.7
          )
          const flap = 0.75 + 0.25 * Math.sin(this._time * 14 + i * 3)
          s.scale.set(0.5 * flap, 0.5, 1)
        })
      } else if (type === 'dragonfly') {
        // 蜻蜓：低空大半径疾飞 + 急转起伏 + 悬停感（速度忽快忽慢）
        items.forEach((s, i) => {
          const u = s.userData
          u.phase += u.speed * dt * (0.6 + 0.4 * Math.sin(this._time * 1.3 + i * 2.4))
          s.position.set(
            Math.cos(u.phase) * u.radius,
            u.height + Math.sin(this._time * 3.1 + i) * 0.22,
            Math.sin(u.phase * 1.15) * u.radius * 0.8
          )
          const flap = 0.8 + 0.2 * Math.sin(this._time * 30 + i)
          s.scale.set(0.42 * flap, 0.42, 1)
        })
      } else if (type === 'geese') {
        // 雁阵：整队沿方向横越高空，飞出边界后从另一侧回归；v 字队列 + 扑翼
        const f = this.creatureMeta.formation
        f.x += f.dirX * f.speed * dt
        f.z += f.dirZ * f.speed * dt
        if (f.x > 80) { f.x = -80; f.z = -30 }
        items.forEach((s, i) => {
          const u = s.userData
          s.position.set(
            f.x + u.slotOffset.x,
            f.y + u.slotOffset.y + Math.sin(this._time * 0.8 + i) * 0.3,
            f.z + u.slotOffset.x * 0.35
          )
          const flap = 0.55 + 0.45 * Math.sin(this._time * 5 + i * 1.2)
          s.scale.set(0.7, 0.35 * flap, 1)
        })
      } else if (type === 'crows') {
        // 寒鸦：高空大半径盘旋 + 缓慢扑翼
        items.forEach((s, i) => {
          const u = s.userData
          u.phase += u.speed * dt
          s.position.set(
            Math.cos(u.phase) * u.radius,
            u.height + Math.sin(this._time * 0.6 + i * 2.5) * 1.2,
            Math.sin(u.phase) * u.radius
          )
          const flap = 0.6 + 0.4 * Math.sin(this._time * 4.5 + i * 1.8)
          s.scale.set(0.8, 0.4 * flap, 1)
        })
      }
    }

    // --- 粒子：阵风 + 旋转 + 落地停留 ---
    if (this.particlePoints && this.particleMeta) {
      const posAttr = this.particlePoints.geometry.attributes.position
      const arr = posAttr.array
      const { top, bottom, radius } = this.particlePoints.userData.area
      const meta = this.particleMeta
      const count = arr.length / 3

      // 阵风：主风 + 两个不同频率的次级波动，风强在 0.3~1.8 间起伏
      let wind = 1
      if (meta.windGusts) {
        wind = 1
          + 0.5 * Math.sin(this._time * 0.7 + this._windPhase)
          + 0.3 * Math.sin(this._time * 0.23 + this._windPhase * 2)
      }

      for (let i = 0; i < count; i++) {
        const ix = i * 3
        // 已落地的粒子：停留 groundLife 秒后重生
        if (this.particlePoints.userData.landed && this.particlePoints.userData.landed[i] > 0) {
          this.particlePoints.userData.landed[i] -= dt
          if (this.particlePoints.userData.landed[i] <= 0) {
            arr[ix + 1] = top
            arr[ix] = (Math.random() - 0.5) * radius * 2
            arr[ix + 2] = (Math.random() - 0.5) * radius * 2
          }
          continue
        }

        // 主下落 + 风致水平漂移（阵风越强漂得越远）
        arr[ix + 1] -= meta.fallSpeed * dt * (0.85 + 0.3 * Math.sin(this._time * 2 + i))
        arr[ix] += (Math.sin(this._time * 1.4 + i * 0.7) * meta.sway + wind * meta.sway * 0.8) * dt * 0.5
        arr[ix + 2] += Math.cos(this._time * 1.1 + i * 0.9) * meta.sway * dt * 0.3

        if (arr[ix + 1] < bottom) {
          if (this.particlePoints.userData.landed) {
            // 落地：停留在地面上，进入"堆积"状态
            arr[ix + 1] = bottom
            this.particlePoints.userData.landed[i] = meta.groundLife * (0.6 + Math.random() * 0.8)
          } else {
            arr[ix + 1] = top
            arr[ix] = (Math.random() - 0.5) * radius * 2
            arr[ix + 2] = (Math.random() - 0.5) * radius * 2
          }
        }
      }
      posAttr.needsUpdate = true
    }

    // --- 颜色缓动（约 0.8 秒完成）---
    if (this._fadeState) {
      const s = this._fadeState
      s.t = Math.min(1, s.t + dt / 0.8)
      const k = s.t < 0.5 ? 2 * s.t * s.t : 1 - Math.pow(-2 * s.t + 2, 2) / 2
      const to = s.to
      if (this.scene.background && s.from.bg) this.scene.background.lerpColors(s.from.bg, new THREE.Color(to.sky.stops[to.sky.stops.length - 1][1]), k)
      if (this.groundMesh && to.ground) {
        this.groundMesh.material.color.lerpColors(s.from.ground || new THREE.Color('#ffffff'), new THREE.Color('#ffffff'), k)
      }
      if (this.scene.fog && to.fog) {
        this.scene.fog.color.lerpColors(s.from.fogColor || new THREE.Color(to.fog.color), new THREE.Color(to.fog.color), k)
        this.scene.fog.near = THREE.MathUtils.lerp(14, to.fog.near, k)
        this.scene.fog.far = THREE.MathUtils.lerp(80, to.fog.far, k)
      }
      if (s.t >= 1) this._fadeState = null
    }
  }

  /** 销毁全部环境资源（组件卸载时调用） */
  dispose() {
    if (this.skyMesh) {
      this.scene.remove(this.skyMesh)
      this.skyMesh.geometry.dispose()
      this.skyMesh.material.map?.dispose()
      this.skyMesh.material.dispose()
      this.skyMesh = null
    }
    if (this.sunSprite) {
      this.scene.remove(this.sunSprite)
      this.sunSprite.material.map?.dispose()
      this.sunSprite.material.dispose()
      this.sunSprite = null
    }
    this.cloudSprites.forEach(s => {
      this.scene.remove(s)
      s.material.dispose()
    })
    this.cloudSprites = []
    if (this.groundMesh) {
      this.scene.remove(this.groundMesh)
      this.groundMesh.geometry.dispose()
      this.groundMesh.material.map?.dispose()
      this.groundMesh.material.dispose()
      this.groundMesh = null
    }
    if (this.particlePoints) {
      this.scene.remove(this.particlePoints)
      this.particlePoints.geometry.dispose()
      this.particlePoints.material.map?.dispose()
      this.particlePoints.material.dispose()
      this.particlePoints = null
    }
    this.mistSprites.forEach(s => {
      this.scene.remove(s)
      s.material.map?.dispose()
      s.material.dispose()
    })
    this.mistSprites = []
    if (this.creatureGroup) {
      this.scene.remove(this.creatureGroup)
      this.creatureGroup.traverse(o => {
        if (o.material) {
          o.material.map?.dispose()
          o.material.dispose()
        }
      })
      this.creatureGroup = null
      this.creatureMeta = null
    }
    this.scene.fog = null
    this._fadeState = null
  }
}
