<template>
  <div class="model-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>{{ t('modelManage') }}</span>
          <div class="header-actions">
            <el-select v-model="status" :placeholder="t('reviewStatus')" clearable @change="loadModels">
              <el-option :label="t('all')" value="" />
              <el-option :label="t('pendingReview')" :value="0" />
              <el-option :label="t('approved')" :value="1" />
              <el-option :label="t('rejected')" :value="2" />
            </el-select>
            <el-button type="primary" @click="openUploadDialog">{{ t('addNewModel') }}</el-button>
          </div>
        </div>
      </template>

      <el-table :data="models" class="admin-model-table" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="modelName" :label="t('modelName')" min-width="150" />
        <el-table-column prop="dynasty" :label="t('dynasty')" width="100" />
        <el-table-column prop="creatorUsername" :label="t('uploader')" width="120" />
        <el-table-column prop="viewCount" :label="t('views')" width="100" />
        <el-table-column prop="status" :label="t('status')" width="100">
          <template #default="scope">
            <el-tag class="status-tag" :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('createdAtLabel')" width="180" />
        <el-table-column :label="t('actions')" width="330" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="previewModelWithUrl(scope.row)">{{ t('preview') }}</el-button>
            <el-button size="small" @click="openEditDialog(scope.row)">{{ t('edit') }}</el-button>
            <el-button size="small" type="danger" @click="deleteModel(scope.row)">{{ t('delete') }}</el-button>
            <el-button
              v-if="scope.row.status === 0"
              size="small"
              type="success"
              @click="approveModel(scope.row)"
            >
              {{ t('approve') }}
            </el-button>
            <el-button
              v-if="scope.row.status === 0"
              size="small"
              type="danger"
              @click="rejectModel(scope.row)"
            >
              {{ t('reject') }}
            </el-button>
            <el-button
              v-if="scope.row.status === 2"
              size="small"
              type="warning"
              @click="cleanupRejectedFiles(scope.row)"
            >
              {{ t('cleanupFiles') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadModels"
        style="margin-top: 20px; justify-content: center;"
      />
    </el-card>

    <el-dialog
      v-model="previewVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="t('modelPreview')"
      width="80%"
      top="5vh"
    >
      <div class="preview-container">
        <div class="preview-3d">
          <div v-if="previewLoading" class="preview-overlay">{{ t('modelLoading') }}</div>
          <div v-else-if="previewError" class="preview-overlay error">{{ previewError }}</div>
          <div ref="previewContainerRef" class="preview-canvas"></div>
        </div>
        <div class="preview-info" v-if="currentModel">
          <h3>{{ currentModel.modelName }}</h3>
          <p><strong>{{ t('dynasty') }}：</strong>{{ currentModel.dynasty }}</p>
          <p><strong>{{ t('uploader') }}：</strong>{{ currentModel.creatorUsername }}</p>
          <p><strong>{{ t('description') }}：</strong>{{ currentModel.description || t('none') }}</p>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="rejectDialogVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="t('rejectReason')"
      width="400px"
    >
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        :placeholder="t('enterRejectReason')"
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" @click="confirmReject">{{ t('confirmReject') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="uploadDialogVisible"
      class="model-upload-dialog"
      modal-class="model-upload-overlay"
      :title="t('add3dModelStructured')"
      width="760px"
    >
      <el-form :model="uploadForm" label-width="120px">
        <el-form-item :label="t('modelName')" required>
          <el-input v-model="uploadForm.modelName" :placeholder="t('fieldModelNameRequired')" />
        </el-form-item>
        <el-form-item :label="t('modelCategory')">
          <el-select v-model="uploadForm.categoryId" :placeholder="t('fieldCategoryId')" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dynasty')">
          <el-input v-model="uploadForm.dynasty" :placeholder="t('fieldDynastyExample')" />
        </el-form-item>
        <el-form-item :label="t('location')">
          <el-input v-model="uploadForm.location" :placeholder="t('fieldLocationExample')" />
        </el-form-item>
        <el-form-item :label="t('modelDescription')">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('fieldDescriptionTip')"
          />
        </el-form-item>
        <el-form-item :label="t('tagKeywords')">
          <div class="tag-editor">
            <div class="tag-list" v-if="uploadForm.tags.length > 0">
              <el-tag
                v-for="(tag, index) in uploadForm.tags"
                :key="`${tag}-${index}`"
                closable
                @close="removeTag(index)"
              >
                {{ tag }}
              </el-tag>
            </div>
            <el-input
              v-model="tagInput"
              :placeholder="t('tagInputPlaceholder')"
              @keyup.enter="addTag"
            />
            <div class="upload-tip">{{ t('fieldTagsTip') }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="t('downloadPermission')">
          <el-radio-group v-model="uploadForm.downloadPermission">
            <el-radio :value="1">{{ t('allowDownloadField') }}</el-radio>
            <el-radio :value="0">{{ t('denyDownloadField') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('publishMode')">
          <el-radio-group v-model="uploadForm.status">
            <el-radio :value="1">{{ t('publishDirectlyAfterUpload') }}</el-radio>
            <el-radio :value="0">{{ t('uploadPendingReview') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('defaultCameraPosition')">
          <div class="xyz-inputs">
            <el-input-number v-model="uploadForm.cameraPositionX" :step="0.1" :precision="2" controls-position="right" placeholder="X" />
            <el-input-number v-model="uploadForm.cameraPositionY" :step="0.1" :precision="2" controls-position="right" placeholder="Y" />
            <el-input-number v-model="uploadForm.cameraPositionZ" :step="0.1" :precision="2" controls-position="right" placeholder="Z" />
          </div>
          <div class="upload-tip">{{ t('fieldDefaultCameraPositionTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('defaultCameraTarget')">
          <div class="xyz-inputs">
            <el-input-number v-model="uploadForm.cameraTargetX" :step="0.1" :precision="2" controls-position="right" placeholder="X" />
            <el-input-number v-model="uploadForm.cameraTargetY" :step="0.1" :precision="2" controls-position="right" placeholder="Y" />
            <el-input-number v-model="uploadForm.cameraTargetZ" :step="0.1" :precision="2" controls-position="right" placeholder="Z" />
          </div>
          <div class="upload-tip">{{ t('fieldDefaultCameraTargetTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('cameraQuickActions')">
          <el-button @click="resetToRecommendedCamera">{{ t('resetRecommendedCamera') }}</el-button>
        </el-form-item>
        <el-form-item :label="t('modelFile')" required>
          <el-upload
            class="upload-field"
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleModelFileChange"
            :on-remove="handleModelFileRemove"
            accept=".glb"
          >
            <el-button class="upload-trigger" type="primary" plain>{{ t('selectModelFile') }}</el-button>
          </el-upload>
          <div class="upload-tip">{{ t('modelFileTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('thumbnail')">
          <el-upload
            class="upload-field"
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleThumbnailFileChange"
            :on-remove="handleThumbnailFileRemove"
            accept=".png,.jpg,.jpeg,.webp"
          >
            <el-button class="upload-trigger" type="primary" plain>{{ t('selectThumbnailOptional') }}</el-button>
          </el-upload>
          <div class="upload-tip">{{ t('fieldThumbnailTip') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">{{ t('upload') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editDialogVisible"
      class="model-upload-dialog"
      modal-class="model-upload-overlay"
      :title="t('editModel')"
      width="680px"
    >
      <el-form :model="editForm" label-width="120px">
        <el-form-item :label="t('modelName')" required>
          <el-input v-model="editForm.modelName" :placeholder="t('enterModelName')" />
        </el-form-item>
        <el-form-item label="缩略图">
          <div class="thumbnail-editor">
            <div class="thumbnail-preview">
              <img
                v-if="editThumbnailPreviewUrl"
                :src="editThumbnailPreviewUrl"
                alt="缩略图预览"
              />
              <div v-else class="thumbnail-preview__empty">暂无缩略图</div>
            </div>
            <div class="thumbnail-editor__actions">
              <el-upload
                accept="image/png,image/jpeg,image/webp"
                :auto-upload="false"
                :limit="1"
                :on-change="onEditThumbnailChange"
                :on-remove="onEditThumbnailRemove"
                :file-list="editThumbnailFiles"
              >
                <el-button size="small" type="primary" plain>选择新图片</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持 PNG、JPG、WebP；不选择则保持原图</div>
                </template>
              </el-upload>
              <el-button
                v-if="editThumbnailFiles.length"
                size="small"
                text
                type="danger"
                @click="cancelEditThumbnail"
              >
                取消替换
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="t('modelCategory')">
          <el-select v-model="editForm.categoryId" :placeholder="t('selectModelCategory')" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dynasty')">
          <el-input v-model="editForm.dynasty" />
        </el-form-item>
        <el-form-item :label="t('location')">
          <el-input v-model="editForm.location" />
        </el-form-item>
        <el-form-item :label="t('modelDescription')">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('tagKeywords')">
          <div class="tag-editor">
            <div class="tag-list" v-if="editForm.tags.length > 0">
              <el-tag
                v-for="(tag, index) in editForm.tags"
                :key="`${tag}-${index}`"
                closable
                @close="removeEditTag(index)"
              >
                {{ tag }}
              </el-tag>
            </div>
            <el-input
              v-model="editTagInput"
              :placeholder="t('tagInputPlaceholder')"
              @keyup.enter="addEditTag"
            />
          </div>
        </el-form-item>
        <el-form-item :label="t('downloadPermission')">
          <el-radio-group v-model="editForm.downloadPermission">
            <el-radio :value="1">{{ t('allowDownload') }}</el-radio>
            <el-radio :value="0">{{ t('denyDownload') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('defaultCameraPosition')">
          <div class="xyz-inputs">
            <el-input-number v-model="editForm.cameraPositionX" :step="0.1" :precision="2" controls-position="right" placeholder="X" />
            <el-input-number v-model="editForm.cameraPositionY" :step="0.1" :precision="2" controls-position="right" placeholder="Y" />
            <el-input-number v-model="editForm.cameraPositionZ" :step="0.1" :precision="2" controls-position="right" placeholder="Z" />
          </div>
        </el-form-item>
        <el-form-item :label="t('defaultCameraTarget')">
          <div class="xyz-inputs">
            <el-input-number v-model="editForm.cameraTargetX" :step="0.1" :precision="2" controls-position="right" placeholder="X" />
            <el-input-number v-model="editForm.cameraTargetY" :step="0.1" :precision="2" controls-position="right" placeholder="Y" />
            <el-input-number v-model="editForm.cameraTargetZ" :step="0.1" :precision="2" controls-position="right" placeholder="Z" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" :loading="savingEdit" @click="submitEdit">{{ t('save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import request from '@/utils/request'
import { useUiPreferences } from '@/composables/useUiPreferences'

const { t } = useUiPreferences()

const loading = ref(false)
const models = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const status = ref('')
const route = useRoute()
const pendingPreviewModelId = ref(null)

const previewVisible = ref(false)
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const currentModel = ref(null)
const previewContainerRef = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const editDialogVisible = ref(false)
const savingEdit = ref(false)
const editTagInput = ref('')
// 编辑弹窗里替换缩略图用的新文件和预览 URL（ObjectURL 要手动释放）
const editThumbnailFiles = ref([])
const editThumbnailFile = ref(null)
const editThumbnailPreviewUrl = ref('')
const editForm = ref({
  id: null,
  modelName: '',
  categoryId: null,
  dynasty: '',
  location: '',
  description: '',
  tags: [],
  downloadPermission: 1,
  cameraPositionX: 0,
  cameraPositionY: 2,
  cameraPositionZ: 5,
  cameraTargetX: 0,
  cameraTargetY: 0,
  cameraTargetZ: 0
})
const modelFile = ref(null)
const thumbnailFile = ref(null)
const categoryOptions = ref([])
const tagInput = ref('')
const uploadForm = ref({
  modelName: '',
  categoryId: null,
  dynasty: '',
  location: '',
  description: '',
  tags: [],
  downloadPermission: 1,
  status: 1,
  cameraPositionX: 0,
  cameraPositionY: 2,
  cameraPositionZ: 5,
  cameraTargetX: 0,
  cameraTargetY: 0,
  cameraTargetZ: 0
})

let scene, camera, renderer, controls, animationId
let loadedObject3D = null

onMounted(() => {
  const presetStatus = route.query?.status
  if (presetStatus !== undefined && presetStatus !== null && `${presetStatus}` !== '') {
    const parsed = Number(presetStatus)
    if ([0, 1, 2].includes(parsed)) status.value = parsed
  }
  const presetPreviewModelId = route.query?.previewModelId
  if (presetPreviewModelId !== undefined && presetPreviewModelId !== null && `${presetPreviewModelId}` !== '') {
    const parsedModelId = Number(presetPreviewModelId)
    if (Number.isFinite(parsedModelId) && parsedModelId > 0) {
      pendingPreviewModelId.value = parsedModelId
    }
  }
  loadModels()
  loadCategories()
})

watch(previewVisible, (newVal) => {
  if (newVal) {
    nextTick(() => {
      initPreviewScene()
    })
  } else {
    cleanupPreview()
  }
})

const loadModels = async () => {
  loading.value = true
  try {
    const params = { page: currentPage.value, pageSize: pageSize.value }
    if (status.value !== '') {
      params.status = status.value
    }
    const response = await request.get('/models', { params })
    const payload = response.data?.data ?? response.data
    models.value = payload?.records || payload?.content || []
    total.value = payload?.total ?? payload?.totalElements ?? 0
    if (pendingPreviewModelId.value) {
      await openPreviewFromRoute(pendingPreviewModelId.value)
      pendingPreviewModelId.value = null
    }
  } catch (error) {
    console.error('[ModelManage] 加载模型列表失败', error)
  } finally {
    loading.value = false
  }
}

const openPreviewFromRoute = async (modelId) => {
  const matched = models.value.find(item => Number(item.id) === Number(modelId))
  if (matched) {
    await previewModelWithUrl(matched)
    return
  }

  try {
    const response = await request.get(`/models/${modelId}`)
    const payload = response.data?.data ?? response.data
    if (payload?.id) {
      await previewModelWithUrl(payload)
    }
  } catch (error) {
    console.error('[ModelManage] 打开路由预览失败', error)
  }
}

const getStatusType = (status) => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 0: return t('pendingReview')
    case 1: return t('approved')
    case 2: return t('rejected')
    default: return t('unknown')
  }
}

const initPreviewScene = () => {
  if (!previewContainerRef.value) return

  const container = previewContainerRef.value
  const width = container.clientWidth
  const height = container.clientHeight || 500

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0xf0f2f5)

  camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 1000)
  camera.position.set(5, 5, 5)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.shadowMap.enabled = true
  container.appendChild(renderer.domElement)

  const ambientLight = new THREE.AmbientLight(0xffffff, 0.6)
  scene.add(ambientLight)

  const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8)
  directionalLight.position.set(10, 10, 10)
  directionalLight.castShadow = true
  scene.add(directionalLight)

  const gridHelper = new THREE.GridHelper(10, 10, 0xcccccc, 0xe0e0e0)
  scene.add(gridHelper)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05

  const axisHelper = new THREE.AxesHelper(3)
  scene.add(axisHelper)

  animate()

  previewError.value = ''
  if (currentModel.value?.modelUrl) loadModel(currentModel.value.modelUrl)
}

const loadModel = (url) => {
  if (!url) return

  const loader = new GLTFLoader()
  previewLoading.value = true
  previewError.value = ''

  loader.load(
    url,
    (gltf) => {
      try {
        if (!scene) return

        if (loadedObject3D) {
          scene.remove(loadedObject3D)
          loadedObject3D = null
        }

        const model = gltf.scene || gltf.scenes?.[0]
        if (!model) {
          previewError.value = t('modelParseFailed')
          return
        }

        model.scale.set(1, 1, 1)

        // 按包围盒把模型居中并缩放到最大边 3 个单位，多大的原始模型都能正常看
        const box = new THREE.Box3().setFromObject(model)
        const size = box.getSize(new THREE.Vector3())
        const center = box.getCenter(new THREE.Vector3())
        const maxDim = Math.max(size.x, size.y, size.z)

        if (!Number.isFinite(maxDim) || maxDim <= 0) {
          previewError.value = t('modelBoundingBoxError')
          // 算不出包围盒就直接塞进场景，让用户自己滚轮缩放
          scene.add(model)
          loadedObject3D = model
          return
        }

        model.position.sub(center)
        const targetSize = 3
        const uniformScale = targetSize / maxDim
        model.scale.multiplyScalar(uniformScale)

        scene.add(model)
        loadedObject3D = model

        // 相机对准缩放后的模型，距离按 FOV 算出刚好能看全
        const scaledBox = new THREE.Box3().setFromObject(model)
        const scaledSize = scaledBox.getSize(new THREE.Vector3())
        const scaledCenter = scaledBox.getCenter(new THREE.Vector3())
        const scaledMaxDim = Math.max(scaledSize.x, scaledSize.y, scaledSize.z)
        const fitDist = scaledMaxDim / (2 * Math.tan((camera.fov * Math.PI) / 360))
        const dir = new THREE.Vector3(1, 0.8, 1).normalize()
        camera.near = Math.max(0.01, fitDist / 100)
        camera.far = Math.max(1000, fitDist * 100)
        camera.updateProjectionMatrix()
        camera.position.copy(scaledCenter.clone().add(dir.multiplyScalar(fitDist * 1.6)))
        controls.target.copy(scaledCenter)
        controls.update()
      } finally {
        previewLoading.value = false
      }
    },
    undefined,
    (error) => {
      console.error('[ModelManage] 预览模型加载失败', error)
      previewLoading.value = false
      previewError.value = (error && error.message) ? error.message : t('modelLoadFailedGlb')
      ElMessage.error(t('modelLoadConsoleError'))
    }
  )
}

const animate = () => {
  animationId = requestAnimationFrame(animate)
  controls.update()
  renderer.render(scene, camera)
}

const cleanupPreview = () => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  previewLoading.value = false
  previewError.value = ''
  if (renderer) {
    renderer.dispose()
    if (previewContainerRef.value && renderer.domElement) {
      previewContainerRef.value.removeChild(renderer.domElement)
    }
  }
  if (scene && loadedObject3D) {
    scene.remove(loadedObject3D)
    loadedObject3D = null
  }
  scene = null
  camera = null
  renderer = null
  controls = null
}

const preparePreviewUrl = async (modelId) => {
  try {
    const response = await request.get(`/models/${modelId}/file-url`)
    const payload = response.data?.data ?? response.data
    const downloadUrl = payload?.downloadUrl
    if (!downloadUrl) return null
    return downloadUrl
  } catch (error) {
    console.error('[ModelManage] 获取预览地址失败', error)
    return null
  }
}

const previewModelWithUrl = async (model) => {
  currentModel.value = model
  const url = await preparePreviewUrl(model.id)
  if (!url) {
    ElMessage.error(t('previewLinkFetchFailed'))
    return
  }
  currentModel.value = { ...model, modelUrl: url }
  previewVisible.value = true
}

const approveModel = async (model) => {
  try {
    await ElMessageBox.confirm(t('confirmApproveModel'), t('prompt'), {
      confirmButtonText: t('confirm'),
      cancelButtonText: t('cancel'),
      type: 'warning'
    })

    await request.put(`/models/${model.id}/status`, { status: 1 })
    ElMessage.success(t('approvedSuccess'))
    loadModels()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[ModelManage] 审核通过失败', error)
    }
  }
}

const rejectModel = (model) => {
  currentModel.value = model
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const confirmReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning(t('enterRejectReason'))
    return
  }

  try {
    await request.put(`/models/${currentModel.value.id}/status`, {
      status: 2,
      rejectReason: rejectReason.value
    })
    ElMessage.success(t('rejectedSuccess'))
    rejectDialogVisible.value = false
    loadModels()
  } catch (error) {
    console.error('[ModelManage] 驳回模型失败', error)
    ElMessage.error(t('rejectFailed'))
  }
}

const cleanupRejectedFiles = async (model) => {
  try {
    await ElMessageBox.confirm(
      t('cleanupRejectedFilesConfirm'),
      t('dangerOperationConfirm'),
      { confirmButtonText: t('confirmDelete'), cancelButtonText: t('cancel'), type: 'warning' }
    )
    await request.delete(`/admin/models/${model.id}/files`)
    ElMessage.success(t('filesCleaned'))
    loadModels()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[ModelManage] 清理文件失败', error)
      ElMessage.error(t('cleanupFailed'))
    }
  }
}

const openUploadDialog = () => {
  uploadForm.value = {
    modelName: '',
    categoryId: null,
    dynasty: '',
    location: '',
    description: '',
    tags: [],
    downloadPermission: 1,
    status: 1,
    cameraPositionX: 0,
    cameraPositionY: 2,
    cameraPositionZ: 5,
    cameraTargetX: 0,
    cameraTargetY: 0,
    cameraTargetZ: 0
  }
  modelFile.value = null
  thumbnailFile.value = null
  tagInput.value = ''
  uploadDialogVisible.value = true
}

const loadCategories = async () => {
  try {
    const response = await request.get('/categories')
    categoryOptions.value = response.data.data || []
  } catch (error) {
    console.error('[ModelManage] 加载分类失败', error)
  }
}

const handleModelFileChange = (file) => {
  const fileName = (file.name || '').toLowerCase()
  if (!fileName.endsWith('.glb')) {
    ElMessage.warning(t('modelFileTip'))
    modelFile.value = null
    return false
  }
  modelFile.value = file.raw
  return true
}

const handleModelFileRemove = () => {
  modelFile.value = null
}

const handleThumbnailFileChange = (file) => {
  const fileName = (file.name || '').toLowerCase()
  const isSupported = fileName.endsWith('.png') || fileName.endsWith('.jpg')
    || fileName.endsWith('.jpeg') || fileName.endsWith('.webp')
  if (!isSupported) {
    ElMessage.warning(t('thumbnailTip'))
    thumbnailFile.value = null
    return false
  }
  thumbnailFile.value = file.raw
  return true
}

const handleThumbnailFileRemove = () => {
  thumbnailFile.value = null
}

const hasAnyCameraValue = (values) => values.some(value => value !== null && value !== undefined)
const hasAllCameraValue = (values) => values.every(value => value !== null && value !== undefined)

const addTag = () => {
  const value = tagInput.value.trim()
  if (!value) return
  if (uploadForm.value.tags.includes(value)) {
    ElMessage.warning(t('tagAlreadyExists'))
    return
  }
  uploadForm.value.tags.push(value)
  tagInput.value = ''
}

const removeTag = (index) => {
  uploadForm.value.tags.splice(index, 1)
}

const resetToRecommendedCamera = () => {
  uploadForm.value.cameraPositionX = 0
  uploadForm.value.cameraPositionY = 2
  uploadForm.value.cameraPositionZ = 5
  uploadForm.value.cameraTargetX = 0
  uploadForm.value.cameraTargetY = 0
  uploadForm.value.cameraTargetZ = 0
  ElMessage.success(t('resetRecommendedCameraSuccess'))
}

const parseJsonArray = (value) => {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return String(value).split(',').map(item => item.trim()).filter(Boolean)
  }
}

const parseCamera = (value, fallback) => {
  if (!value) return fallback
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed) && parsed.length === 3) {
      return parsed.map((item, index) => (item ?? fallback[index]))
    }
  } catch (e) {
    // 历史数据可能存了不合法的 JSON，解析不了就用默认值
  }
  return fallback
}

const openEditDialog = async (model) => {
  try {
    const response = await request.get(`/models/${model.id}`)
    const detail = response.data?.data ?? response.data
    const cameraPosition = parseCamera(detail.defaultCameraPosition, [0, 2, 5])
    const cameraTarget = parseCamera(detail.defaultCameraTarget, [0, 0, 0])
    editForm.value = {
      id: model.id,
      modelName: detail.modelName || model.modelName || '',
      categoryId: detail.categoryId ?? model.categoryId ?? null,
      dynasty: detail.dynasty || '',
      location: detail.location || '',
      description: detail.description || '',
      tags: parseJsonArray(detail.tags),
      downloadPermission: detail.downloadPermission ?? 1,
      cameraPositionX: cameraPosition[0],
      cameraPositionY: cameraPosition[1],
      cameraPositionZ: cameraPosition[2],
      cameraTargetX: cameraTarget[0],
      cameraTargetY: cameraTarget[1],
      cameraTargetZ: cameraTarget[2]
    }
    editTagInput.value = ''
    // 缩略图先显示线上的，选了新文件才替换
    resetEditThumbnail()
    editThumbnailPreviewUrl.value = detail.thumbnailPath || ''
    editDialogVisible.value = true
  } catch (error) {
    console.error('[ModelManage] 加载模型详情失败', error)
    ElMessage.error(t('loadModelDetailFailed'))
  }
}

/** 选择了新缩略图：生成本地预览 */
const onEditThumbnailChange = (file) => {
  const raw = file?.raw
  if (!raw) return
  if (!/\.(png|jpe?g|webp)$/i.test(raw.name)) {
    ElMessage.warning('仅支持 PNG、JPG、WebP 图片')
    editThumbnailFiles.value = []
    return
  }
  if (raw.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 10MB')
    editThumbnailFiles.value = []
    return
  }
  editThumbnailFile.value = raw
  if (editThumbnailPreviewUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(editThumbnailPreviewUrl.value)
  }
  editThumbnailPreviewUrl.value = URL.createObjectURL(raw)
}

/** 移除所选新图：恢复显示线上原图 */
const onEditThumbnailRemove = () => {
  cancelEditThumbnail()
}

/** 取消替换：清空所选文件，预览回到原图 */
const cancelEditThumbnail = () => {
  editThumbnailFile.value = null
  editThumbnailFiles.value = []
  if (editThumbnailPreviewUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(editThumbnailPreviewUrl.value)
    editThumbnailPreviewUrl.value = ''
  }
}

const resetEditThumbnail = () => {
  if (editThumbnailPreviewUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(editThumbnailPreviewUrl.value)
  }
  editThumbnailFile.value = null
  editThumbnailFiles.value = []
  editThumbnailPreviewUrl.value = ''
}

const addEditTag = () => {
  const value = editTagInput.value.trim()
  if (!value) return
  if (editForm.value.tags.includes(value)) {
    ElMessage.warning(t('tagAlreadyExists'))
    return
  }
  editForm.value.tags.push(value)
  editTagInput.value = ''
}

const removeEditTag = (index) => {
  editForm.value.tags.splice(index, 1)
}

const submitEdit = async () => {
  if (!editForm.value.modelName.trim()) {
    ElMessage.warning(t('enterModelName'))
    return
  }
  const cameraPositionValues = [
    editForm.value.cameraPositionX,
    editForm.value.cameraPositionY,
    editForm.value.cameraPositionZ
  ]
  const cameraTargetValues = [
    editForm.value.cameraTargetX,
    editForm.value.cameraTargetY,
    editForm.value.cameraTargetZ
  ]
  if (hasAnyCameraValue(cameraPositionValues) && !hasAllCameraValue(cameraPositionValues)) {
    ElMessage.warning(t('cameraPositionCompleteOrEmpty'))
    return
  }
  if (hasAnyCameraValue(cameraTargetValues) && !hasAllCameraValue(cameraTargetValues)) {
    ElMessage.warning(t('cameraTargetCompleteOrEmpty'))
    return
  }

  const formData = new FormData()
  formData.append('modelName', editForm.value.modelName.trim())
  if (editForm.value.categoryId) formData.append('categoryId', String(editForm.value.categoryId))
  formData.append('dynasty', editForm.value.dynasty || '')
  formData.append('location', editForm.value.location || '')
  formData.append('description', editForm.value.description || '')
  formData.append('tags', JSON.stringify(editForm.value.tags || []))
  formData.append('downloadPermission', String(editForm.value.downloadPermission ?? 1))
  formData.append('defaultCameraPosition', JSON.stringify(cameraPositionValues))
  formData.append('defaultCameraTarget', JSON.stringify(cameraTargetValues))
  // 选了新缩略图才带上，不然空文件会把原图覆盖掉
  if (editThumbnailFile.value) {
    formData.append('thumbnailFile', editThumbnailFile.value)
  }

  savingEdit.value = true
  try {
    const response = await request.put(`/models/${editForm.value.id}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const payload = response.data?.data ?? response.data
    if (!payload?.id) {
      ElMessage.error(t('modelUpdateResponseInvalid'))
      return
    }
    ElMessage.success(t('modelUpdateSuccess'))
    editDialogVisible.value = false
    resetEditThumbnail()
    loadModels()
  } catch (error) {
    console.error('[ModelManage] 更新模型失败', error)
    const backendMessage = error?.response?.data?.message || error?.message
    ElMessage.error(backendMessage || t('modelUpdateFailed'))
  } finally {
    savingEdit.value = false
  }
}

const deleteModel = async (model) => {
  try {
    await ElMessageBox.confirm(
      `${t('confirmDeleteModelPrefix')}“${model.modelName}”？${t('operationIrreversible')}`,
      t('deleteConfirm'),
      { confirmButtonText: t('delete'), cancelButtonText: t('cancel'), type: 'warning' }
    )
    await request.delete(`/models/${model.id}`)
    ElMessage.success(t('modelDeleted'))
    loadModels()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[ModelManage] 删除模型失败', error)
      const backendMessage = error?.response?.data?.message || error?.message
      ElMessage.error(backendMessage || t('modelDeleteFailed'))
    }
  }
}

const submitUpload = async () => {
  if (!uploadForm.value.modelName.trim()) {
    ElMessage.warning(t('enterModelName'))
    return
  }
  if (!modelFile.value) {
    ElMessage.warning(t('selectGlbModelFile'))
    return
  }
  const cameraPositionValues = [
    uploadForm.value.cameraPositionX,
    uploadForm.value.cameraPositionY,
    uploadForm.value.cameraPositionZ
  ]
  if (hasAnyCameraValue(cameraPositionValues) && !hasAllCameraValue(cameraPositionValues)) {
    ElMessage.warning(t('cameraPositionCompleteOrEmpty'))
    return
  }
  const cameraTargetValues = [
    uploadForm.value.cameraTargetX,
    uploadForm.value.cameraTargetY,
    uploadForm.value.cameraTargetZ
  ]
  if (hasAnyCameraValue(cameraTargetValues) && !hasAllCameraValue(cameraTargetValues)) {
    ElMessage.warning(t('cameraTargetCompleteOrEmpty'))
    return
  }

  const formData = new FormData()
  formData.append('modelFile', modelFile.value)
  formData.append('modelName', uploadForm.value.modelName.trim())
  if (uploadForm.value.categoryId) formData.append('categoryId', String(uploadForm.value.categoryId))
  if (uploadForm.value.dynasty) formData.append('dynasty', uploadForm.value.dynasty)
  if (uploadForm.value.location) formData.append('location', uploadForm.value.location)
  if (uploadForm.value.description?.trim()) {
    formData.append('description', uploadForm.value.description.trim())
  }
  if (uploadForm.value.tags.length > 0) {
    formData.append('tags', JSON.stringify(uploadForm.value.tags))
  }
  formData.append('downloadPermission', String(uploadForm.value.downloadPermission))
  formData.append('status', String(uploadForm.value.status))
  if (hasAllCameraValue(cameraPositionValues)) {
    formData.append('defaultCameraPosition', JSON.stringify(cameraPositionValues))
  }
  if (hasAllCameraValue(cameraTargetValues)) {
    formData.append('defaultCameraTarget', JSON.stringify(cameraTargetValues))
  }
  if (thumbnailFile.value) {
    formData.append('thumbnailFile', thumbnailFile.value)
  }

  uploading.value = true
  try {
    const response = await request.post('/models', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const payload = response.data?.data ?? response.data
    const createdStatus = payload?.status
    if (createdStatus === 0) {
      ElMessage.success(t('modelUploadSuccessPending'))
    } else if (createdStatus === 1) {
      ElMessage.success(t('modelUploadSuccessPublished'))
    } else if (createdStatus === 2) {
      ElMessage.success(t('modelUploadSuccessRejected'))
    } else {
      ElMessage.success(t('modelUploadSuccess'))
    }
    uploadDialogVisible.value = false
    loadModels()
  } catch (error) {
    console.error('[ModelManage] 上传模型失败', error)
    const backendMessage = error?.response?.data?.message
      || error?.response?.data?.error
      || error?.message
    ElMessage.error(backendMessage || t('modelUploadFailed'))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.model-manage {
  /* 编辑弹窗：缩略图替换区 */
  .thumbnail-editor {
    display: flex;
    gap: 16px;
    align-items: flex-start;
    width: 100%;
  }

  .thumbnail-preview {
    width: 160px;
    height: 100px;
    flex-shrink: 0;
    border: 1px dashed #dcdfe6;
    border-radius: 8px;
    overflow: hidden;
    background: #fafafa;
    display: flex;
    align-items: center;
    justify-content: center;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    &__empty {
      font-size: 12px;
      color: #c0c4cc;
    }
  }

  .thumbnail-editor__actions {
    display: flex;
    flex-direction: column;
    gap: 6px;

    :deep(.el-upload-list) {
      display: none; /* 用自绘预览替代 upload 的文件列表 */
    }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }
  }

  .preview-container {
    display: flex;
    flex-direction: column;
    gap: 20px;

    .preview-3d {
      position: relative;
      width: 100%;
      height: 500px;
      background: #f5f5f5;
      border-radius: 8px;
      overflow: hidden;
    }

    .preview-canvas {
      width: 100%;
      height: 100%;
    }

    .preview-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      color: #666;
      background: rgba(245, 245, 245, 0.7);
      backdrop-filter: blur(2px);
      z-index: 2;
    }

    .preview-overlay.error {
      color: #d92d20;
      background: rgba(255, 245, 245, 0.85);
      padding: 0 20px;
      text-align: center;
      line-height: 1.6;
    }

    .preview-info {
      padding: 10px;
      background: #f9f9f9;
      border-radius: 8px;

      h3 {
        margin: 0 0 10px 0;
      }

      p {
        margin: 5px 0;
        color: #666;
      }
    }
  }

  .upload-tip {
    margin-top: 6px;
    color: #999;
    font-size: 12px;
  }

  :deep(.upload-field) {
    width: 100%;
  }

  :deep(.upload-trigger) {
    width: 180px;
    justify-content: center;
  }

  .xyz-inputs {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }

  .tag-editor {
    width: 100%;
  }

  .tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 8px;
  }

  /* 局部修复：全局主题将 el-tag 文本设为浅色，叠加表格浅底后“状态”看不清 */
  :deep(.admin-model-table) {
    --mm-table-bg: rgba(255, 255, 255, 0.92);
    --mm-table-bg-alt: rgba(248, 250, 252, 0.92);
    --mm-table-border: rgba(15, 23, 42, 0.10);
    --mm-table-text: #1f2937;
    --mm-table-text-dim: rgba(31, 41, 55, 0.78);

    .el-table {
      background: transparent !important;
      color: var(--mm-table-text) !important;
      border-radius: 14px;
      overflow: hidden;
    }

    .el-table__inner-wrapper::before {
      background-color: transparent !important;
    }

    .el-table__header-wrapper th.el-table__cell {
      background: var(--mm-table-bg) !important;
      color: rgba(15, 23, 42, 0.82) !important;
      font-weight: 800;
      border-bottom: 1px solid var(--mm-table-border) !important;
    }

    .el-table__body-wrapper td.el-table__cell {
      background: var(--mm-table-bg) !important;
      color: var(--mm-table-text) !important;
      border-bottom: 1px solid rgba(15, 23, 42, 0.06) !important;
    }

    .el-table__body tr.el-table__row--striped td.el-table__cell {
      background: var(--mm-table-bg-alt) !important;
    }

    .cell {
      color: var(--mm-table-text) !important;
    }

    /* 状态标签：确保文字与边框在浅底上可读 */
    .status-tag.el-tag {
      border-radius: 999px;
      padding: 0 10px;
      height: 26px;
      line-height: 24px;
      font-weight: 800;
      letter-spacing: 0.2px;
      border: 1px solid rgba(15, 23, 42, 0.16) !important;
      background: rgba(15, 23, 42, 0.05) !important;
      color: rgba(15, 23, 42, 0.88) !important;
    }

    /* 兼容不同 type 的底色（覆盖 element-plus 默认浅色方案，提高对比度） */
    .status-tag.el-tag--success {
      border-color: rgba(16, 185, 129, 0.30) !important;
      background: rgba(16, 185, 129, 0.10) !important;
      color: rgba(6, 95, 70, 0.92) !important;
    }
    .status-tag.el-tag--warning {
      border-color: rgba(245, 158, 11, 0.32) !important;
      background: rgba(245, 158, 11, 0.12) !important;
      color: rgba(120, 53, 15, 0.92) !important;
    }
    .status-tag.el-tag--danger {
      border-color: rgba(239, 68, 68, 0.30) !important;
      background: rgba(239, 68, 68, 0.10) !important;
      color: rgba(127, 29, 29, 0.92) !important;
    }
    .status-tag.el-tag--info {
      border-color: rgba(100, 116, 139, 0.26) !important;
      background: rgba(100, 116, 139, 0.10) !important;
      color: rgba(30, 41, 59, 0.88) !important;
    }
  }
}
</style>

