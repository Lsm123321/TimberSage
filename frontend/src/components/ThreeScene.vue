<template>
  <div ref="container" class="three-scene-container"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader'
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { OutlinePass } from 'three/examples/jsm/postprocessing/OutlinePass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import { SceneEnvironment, SCENE_PRESETS } from '@/composables/useSceneEnvironment'
import { createCameraFlight, createOrbitFlight } from '@/composables/useCameraFlight'

const props = defineProps({
  modelUrl: {
    type: String,
    default: ''
  },
  defaultCameraPosition: {
    type: Object,
    default: () => ({ x: 0, y: 2, z: 5 })
  },
  defaultCameraTarget: {
    type: Object,
    default: () => ({ x: 0, y: 0, z: 0 })
  }
})

const emit = defineEmits(['loaded', 'error', 'parts', 'part-click'])

const container = ref(null)
let scene, camera, renderer, controls, mixer
let model = null
let animationId = null
let clock = new THREE.Clock()
/** 画布容器尺寸变化（含退出全屏）时同步 WebGL，避免 canvas 残留全屏像素宽度撑破布局 */
let resizeObserver = null

/** 爆炸展开进度 0~1 */
let explodeProgress = 0
let explodeTarget = 0
let explodeStrength = 0.4
const partListCache = []
let highlightedMesh = null
let highlightBackup = null
const raycaster = new THREE.Raycaster()
const pointerNdc = new THREE.Vector2()
let canvasClickBound = null

/** 选中构件时使用 OutlinePass 后处理（无选中时走普通 render 以省性能） */
let effectComposer = null
let outlinePassInst = null
let outlineRenderActive = false

/** 四季场景环境管理器（天空/雾/地面/粒子） */
let sceneEnv = null

/** 相机飞行控制器（AI 导览运镜用），initScene 后创建 */
let cameraFlight = null
let orbitFlight = null

const disposeOutlinePipeline = () => {
  outlineRenderActive = false
  if (outlinePassInst) {
    outlinePassInst.selectedObjects = []
  }
  if (effectComposer) {
    effectComposer.dispose()
    effectComposer = null
    outlinePassInst = null
  }
}

const resizeOutlineComposer = () => {
  if (!effectComposer || !renderer || !container.value) return
  const w = container.value.clientWidth
  const h = container.value.clientHeight
  effectComposer.setPixelRatio(renderer.getPixelRatio())
  effectComposer.setSize(w, h)
  outlinePassInst?.setSize(w, h)
}

const ensureOutlinePipeline = () => {
  if (effectComposer || !renderer || !scene || !camera || !container.value) return
  const w = container.value.clientWidth
  const h = container.value.clientHeight
  effectComposer = new EffectComposer(renderer)
  effectComposer.setPixelRatio(renderer.getPixelRatio())
  effectComposer.setSize(w, h)

  const renderPass = new RenderPass(scene, camera)
  outlinePassInst = new OutlinePass(new THREE.Vector2(w, h), scene, camera)
  outlinePassInst.edgeStrength = 4
  outlinePassInst.edgeGlow = 0.35
  outlinePassInst.edgeThickness = 1.3
  outlinePassInst.visibleEdgeColor.set('#ff6b00')
  outlinePassInst.hiddenEdgeColor.set('#1a0a04')
  outlinePassInst.pulsePeriod = 3.2

  const outputPass = new OutputPass()
  effectComposer.addPass(renderPass)
  effectComposer.addPass(outlinePassInst)
  effectComposer.addPass(outputPass)
}

onMounted(() => {
  initScene()
  if (props.modelUrl) {
    loadModel(props.modelUrl)
  }
  window.addEventListener('resize', onWindowResize)
  if (typeof ResizeObserver !== 'undefined' && container.value) {
    resizeObserver = new ResizeObserver(() => {
      resizeRenderer()
    })
    resizeObserver.observe(container.value)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  dispose()
})

watch(() => props.modelUrl, (newUrl) => {
  if (newUrl) {
    loadModel(newUrl)
  }
})

const initScene = () => {
  const width = container.value.clientWidth
  const height = container.value.clientHeight

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0xf5f5f5)

  camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000)
  camera.position.set(
    props.defaultCameraPosition.x,
    props.defaultCameraPosition.y,
    props.defaultCameraPosition.z
  )

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  container.value.appendChild(renderer.domElement)

  canvasClickBound = (event) => {
    if (!model) return
    const rect = renderer.domElement.getBoundingClientRect()
    pointerNdc.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    pointerNdc.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
    raycaster.setFromCamera(pointerNdc, camera)
    const hits = raycaster.intersectObject(model, true)
    const meshHit = hits.find((h) => h.object && h.object.isMesh)
    if (!meshHit) {
      emit('part-click', null)
      return
    }
    const mesh = meshHit.object
    emit('part-click', {
      id: mesh.uuid,
      name: mesh.name && mesh.name.trim() ? mesh.name.trim() : ''
    })
  }
  renderer.domElement.addEventListener('click', canvasClickBound)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05
  controls.target.set(
    props.defaultCameraTarget.x,
    props.defaultCameraTarget.y,
    props.defaultCameraTarget.z
  )

  const ambientLight = new THREE.AmbientLight(0xffffff, 0.6)
  scene.add(ambientLight)

  const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8)
  directionalLight.position.set(5, 10, 7)
  directionalLight.castShadow = true
  directionalLight.shadow.mapSize.width = 2048
  directionalLight.shadow.mapSize.height = 2048
  scene.add(directionalLight)

  const fillLight = new THREE.DirectionalLight(0xffffff, 0.3)
  fillLight.position.set(-5, 5, -5)
  scene.add(fillLight)

  cameraFlight = createCameraFlight(camera, controls)
  orbitFlight = createOrbitFlight(camera, controls)

  animate()
}

const loadModel = (url) => {
  const loader = new GLTFLoader()

  // Draco 压缩的 glb 也要能加载，解码器用谷歌官方 CDN
  const dracoLoader = new DRACOLoader()
  dracoLoader.setDecoderPath('https://www.gstatic.com/draco/versioned/decoders/1.5.6/')
  loader.setDRACOLoader(dracoLoader)

  loader.load(
    url,
    (gltf) => {
      // 换模型前把旧的资源清干净，防止 WebGL 内存泄漏
      if (model) {
        disposeOutlinePipeline()
        clearPartHighlight()
        scene.remove(model)
        model.traverse((child) => {
          if (child.isMesh) {
            child.geometry.dispose()
            if (child.material.isMaterial) {
              child.material.dispose()
            }
          }
        })
      }

      model = gltf.scene
      model.traverse((child) => {
        if (child.isMesh) {
          child.castShadow = true
          child.receiveShadow = true
        }
      })

      // 归一化：居中放到原点，最大边缩放到 3。后面 AI 导览的机位坐标都是按这个约定给的
      const box = new THREE.Box3().setFromObject(model)
      const center = box.getCenter(new THREE.Vector3())
      const size = box.getSize(new THREE.Vector3())
      const maxDim = Math.max(size.x, size.y, size.z)
      const scale = 3 / maxDim
      model.scale.setScalar(scale)
      model.position.sub(center.multiplyScalar(scale))

      scene.add(model)
      controls.target.set(0, 0, 0)
      controls.update()

      explodeProgress = 0
      explodeTarget = 0
      // 爆炸距离跟模型尺寸挂钩，再小也给个底，不然看不出效果
      explodeStrength = Math.max(0.12, maxDim * scale * 0.18)
      rebuildExplodeMetadata(model)

      if (gltf.animations.length > 0) {
        mixer = new THREE.AnimationMixer(model)
        gltf.animations.forEach((clip) => {
          mixer.clipAction(clip).play()
        })
      }

      emit('loaded', model)
      emit('parts', partListCache.slice())
    },
    undefined,
    (error) => {
      console.error('[ThreeScene] 模型加载失败', error)
      emit('error', error)
    }
  )
}

// 给每个 mesh 预计算爆炸方向：以模型中心指向该 mesh 中心的方向为"炸开"方向。
// 方向是世界的，要转回 mesh 自己的局部坐标系存起来，后面 position 才加得对
const rebuildExplodeMetadata = (root) => {
  partListCache.length = 0
  root.updateMatrixWorld(true)
  let meshIndex = 0
  root.traverse((child) => {
    if (!child.isMesh) return
    delete child.userData._explodeBase
    delete child.userData._explodeDir
    const box = new THREE.Box3().setFromObject(child)
    const wc = box.getCenter(new THREE.Vector3())
    // 恰好在原点上的 mesh 没方向可言，默认往上炸
    const worldDir = wc.lengthSq() > 1e-10
      ? wc.clone().normalize()
      : new THREE.Vector3(0, 1, 0)
    const invParent = new THREE.Matrix4().copy(child.parent.matrixWorld).invert()
    const localDir = worldDir.clone().transformDirection(invParent)
    child.userData._explodeBase = child.position.clone()
    child.userData._explodeDir = localDir
    const name = (child.name && child.name.trim()) ? child.name.trim() : `mesh_${meshIndex}`
    partListCache.push({ id: child.uuid, name })
    meshIndex += 1
  })
}

// 按当前爆炸进度把每个 mesh 从原位沿预存方向推开
const applyExplodePositions = () => {
  if (!model) return
  model.traverse((child) => {
    if (!child.isMesh || !child.userData._explodeBase || !child.userData._explodeDir) return
    child.position.copy(child.userData._explodeBase)
    child.position.addScaledVector(child.userData._explodeDir, explodeStrength * explodeProgress)
  })
}

const animate = () => {
  animationId = requestAnimationFrame(animate)
  const delta = clock.getDelta()

  if (mixer) {
    mixer.update(delta)
  }

  // 爆炸进度做缓动逼近目标值，展开/收回看着不生硬
  const k = Math.min(1, delta * 5)
  explodeProgress += (explodeTarget - explodeProgress) * k
  if (model) {
    applyExplodePositions()
  }

  // 四季环境动画（粒子、云、动物等）
  if (sceneEnv) {
    sceneEnv.update(delta)
  }

  controls.update()
  // 有选中构件才走 OutlinePass 后处理管线，平时普通渲染省性能
  if (outlineRenderActive && effectComposer) {
    effectComposer.render()
  } else {
    renderer.render(scene, camera)
  }
}

const resizeRenderer = () => {
  if (!container.value || !camera || !renderer) return
  const width = container.value.clientWidth
  const height = container.value.clientHeight
  if (width <= 0 || height <= 0) return

  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
  resizeOutlineComposer()
}

const onWindowResize = () => {
  resizeRenderer()
}

const resetCamera = () => {
  if (!camera || !controls) return
  camera.position.set(
    props.defaultCameraPosition.x,
    props.defaultCameraPosition.y,
    props.defaultCameraPosition.z
  )
  controls.target.set(
    props.defaultCameraTarget.x,
    props.defaultCameraTarget.y,
    props.defaultCameraTarget.z
  )
  controls.update()
}

const dispose = () => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  if (renderer && canvasClickBound) {
    renderer.domElement.removeEventListener('click', canvasClickBound)
    canvasClickBound = null
  }
  disposeOutlinePipeline()
  clearPartHighlight()
  // 场景环境（天空/雾/地面/粒子）一并释放，避免 WebGL 资源泄漏
  if (sceneEnv) {
    sceneEnv.dispose()
    sceneEnv = null
  }
  if (renderer) {
    renderer.dispose()
  }
  if (controls) {
    controls.dispose()
  }
  if (model) {
    scene.remove(model)
    model.traverse((child) => {
      if (child.isMesh) {
        child.geometry.dispose()
        if (child.material.isMaterial) {
          child.material.dispose()
        }
      }
    })
  }
}

const setWireframe = (enabled) => {
  if (!model) return
  model.traverse((child) => {
    if (child.isMesh && child.material) {
      if (Array.isArray(child.material)) {
        child.material.forEach(mat => {
          if (mat.isMaterial) {
            mat.wireframe = enabled
          }
        })
      } else if (child.material.isMaterial) {
        child.material.wireframe = enabled
      }
    }
  })
}

const setMaterialProperty = (property, value) => {
  if (!model) return
  model.traverse((child) => {
    if (child.isMesh && child.material) {
      if (Array.isArray(child.material)) {
        child.material.forEach(mat => {
          if (mat.isMaterial) {
            mat[property] = value
          }
        })
      } else if (child.material.isMaterial) {
        child.material[property] = value
      }
    }
  })
}

const setMaterialColor = (color) => {
  if (!model) return
  model.traverse((child) => {
    if (child.isMesh && child.material) {
      if (Array.isArray(child.material)) {
        child.material.forEach(mat => {
          if (mat.isMaterial) {
            mat.color.set(color)
          }
        })
      } else if (child.material.isMaterial) {
        child.material.color.set(color)
      }
    }
  })
}

const applyPreset = (preset) => {
  if (!model) return
  model.traverse((child) => {
    if (child.isMesh && child.material) {
      const materials = Array.isArray(child.material) ? child.material : [child.material]
      materials.forEach(mat => {
        if (mat.isMaterial) {
          if (preset.color) {
            mat.color.set(preset.color)
          }
          if (preset.metalness !== undefined) {
            mat.metalness = preset.metalness
          }
          if (preset.roughness !== undefined) {
            mat.roughness = preset.roughness
          }
          if (preset.opacity !== undefined) {
            mat.opacity = preset.opacity
            mat.transparent = preset.opacity < 1
          }
          if (preset.emissive) {
            mat.emissive.set(preset.emissive)
          }
          if (preset.emissiveIntensity !== undefined) {
            mat.emissiveIntensity = preset.emissiveIntensity
          }
        }
      })
    }
  })
}

const resetMaterial = () => {
  if (!model) return
  model.traverse((child) => {
    if (child.isMesh && child.material) {
      const materials = Array.isArray(child.material) ? child.material : [child.material]
      materials.forEach(mat => {
        if (mat.isMaterial) {
          mat.color.set(0x8B5A2B)
          mat.metalness = 0.3
          mat.roughness = 0.7
          mat.opacity = 1
          mat.transparent = false
          mat.emissive.set(0x000000)
          mat.emissiveIntensity = 0
          mat.wireframe = false
        }
      })
    }
  })
}

const setExploded = (expanded) => {
  explodeTarget = expanded ? 1 : 0
  applyExplodePositions()
}

/**
 * 切换四季场景环境（default/spring/summer/autumn/winter）
 * 返回当前预设，供页面展示选中态；未识别的 key 回退到默认场景。
 */
const setEnvironment = (sceneKey) => {
  const key = SCENE_PRESETS[sceneKey] ? sceneKey : 'default'
  if (!sceneEnv) {
    sceneEnv = new SceneEnvironment(scene)
  }
  return sceneEnv.apply(key)
}

/**
 * AI 导览运镜：平滑飞行到指定机位。
 * @param {{pos:number[], target:number[]}} viewpoint 目标机位
 * @param {number} durationSec 飞行时长（秒）
 * @param {{onDone?:Function, onInterrupt?:Function}} callbacks 完成/被打断回调
 */
const flyToCamera = (viewpoint, durationSec = 8, callbacks = {}) => {
  if (!cameraFlight) return false
  cameraFlight.flyTo(
    { pos: viewpoint.pos || [0, 2, 5], target: viewpoint.target || [0, 0, 0] },
    durationSec,
    callbacks
  )
  return true
}

/** 终止飞行（导览停止/暂停时调用） */
const cancelCameraFlight = () => {
  cameraFlight?.cancel()
  orbitFlight?.cancel()
}

/**
 * 环绕运镜（AI 导览 orbit 动作）：相机绕注视点公转指定角度。
 * @param {{target:number[], radius:number, height:number, degrees:number, durationSec:number}} opts
 */
const flyOrbit = (opts) => {
  if (!orbitFlight) return false
  orbitFlight.flyOrbit(opts, opts.durationSec || 10, opts.callbacks || {})
  return true
}

const getPartsList = () => partListCache.map((p) => ({ ...p }))

const clearPartHighlight = () => {
  outlineRenderActive = false
  if (outlinePassInst) {
    outlinePassInst.selectedObjects = []
  }
  // 恢复之前改掉的自发光参数
  if (highlightedMesh && highlightBackup) {
    const { materials, emissive, intensity } = highlightBackup
    materials.forEach((mat, i) => {
      if (mat && mat.emissive) {
        mat.emissive.copy(emissive[i])
        mat.emissiveIntensity = intensity[i]
      }
    })
  }
  highlightedMesh = null
  highlightBackup = null
}

const highlightPart = (partId) => {
  clearPartHighlight()
  if (!model || !partId) return
  let found = null
  model.traverse((child) => {
    if (child.isMesh && child.uuid === partId) {
      found = child
    }
  })
  if (!found) return
  highlightedMesh = found
  // 双保险高亮：先备份自发光再改颜色；OutlinePass 画描边，自发光让构件本体也亮起来
  const mats = Array.isArray(found.material) ? found.material : [found.material]
  const emissive = []
  const intensity = []
  mats.forEach((mat) => {
    if (mat && mat.emissive) {
      emissive.push(mat.emissive.clone())
      intensity.push(mat.emissiveIntensity)
      mat.emissive.set(0xff9933)
      mat.emissiveIntensity = 0.58
    } else {
      emissive.push(new THREE.Color(0))
      intensity.push(0)
    }
  })
  highlightBackup = { materials: mats, emissive, intensity }

  ensureOutlinePipeline()
  if (outlinePassInst) {
    outlinePassInst.selectedObjects = [found]
    outlineRenderActive = true
  }
}

/**
 * 构件包围盒中心投到画布上的像素坐标（浮窗跟着它定位）。
 * visible 标记是否在视锥内（稍微放宽 15% 余量，贴边时浮窗不至于乱跳）。
 */
const getPartScreenPosition = (partId) => {
  if (!container.value || !camera || !model || !partId) return null
  let mesh = null
  model.traverse((child) => {
    if (child.isMesh && child.uuid === partId) {
      mesh = child
    }
  })
  if (!mesh) return null

  mesh.updateWorldMatrix(true, true)
  const box = new THREE.Box3().setFromObject(mesh)
  const center = box.getCenter(new THREE.Vector3())
  center.project(camera)

  const w = container.value.clientWidth
  const h = container.value.clientHeight
  const x = (center.x * 0.5 + 0.5) * w
  const y = (-center.y * 0.5 + 0.5) * h
  const inFrustum =
    center.z < 1 &&
    center.z > -1 &&
    Math.abs(center.x) <= 1.15 &&
    Math.abs(center.y) <= 1.15

  return { x, y, visible: inFrustum, w, h }
}

const getMaterialParams = () => {
  if (!model) return null
  let color = '#8B5A2B'
  let metalness = 0.3
  let roughness = 0.7
  let opacity = 1
  let emissive = '#000000'
  let emissiveIntensity = 0

  model.traverse((child) => {
    if (child.isMesh && child.material) {
      const mat = Array.isArray(child.material) ? child.material[0] : child.material
      if (mat.isMaterial) {
        color = '#' + mat.color.getHexString()
        metalness = mat.metalness
        roughness = mat.roughness
        opacity = mat.opacity
        emissive = '#' + mat.emissive.getHexString()
        emissiveIntensity = mat.emissiveIntensity
      }
    }
  })

  return { color, metalness, roughness, opacity, emissive, emissiveIntensity }
}

defineExpose({
  resetCamera,
  resize: resizeRenderer,
  setWireframe,
  setMaterialProperty,
  setMaterialColor,
  applyPreset,
  resetMaterial,
  getMaterialParams,
  setExploded,
  setEnvironment,
  flyToCamera,
  flyOrbit,
  cancelCameraFlight,
  getPartsList,
  highlightPart,
  clearPartHighlight,
  getPartScreenPosition,
  getSceneContainerEl: () => container.value
})
</script>

<style scoped>
.three-scene-container {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 500px;
}
</style>
