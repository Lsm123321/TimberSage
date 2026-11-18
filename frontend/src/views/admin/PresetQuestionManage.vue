<template>
  <div class="preset-question-manage">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>预设问题管理</span>
          <el-button type="primary" @click="showAddDialog">添加问题</el-button>
        </div>
      </template>

      <el-table :data="questions" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="categoryId" label="分类" width="100">
          <template #default="scope">
            {{ getCategoryName(scope.row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column prop="modelId" label="模型" width="100">
          <template #default="scope">
            {{ getModelName(scope.row.modelId) }}
          </template>
        </el-table-column>
        <el-table-column prop="questionText" label="问题" min-width="200" show-overflow-tooltip />
        <!-- 展示场景：区分 AI 问答页与模型详情页悬浮问答，避免两处混用同一批问题 -->
        <el-table-column prop="scene" label="展示位置" width="130">
          <template #default="scope">
            <el-tag size="small" :type="sceneTagType(scope.row.scene)">
              {{ sceneLabel(scope.row.scene) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="clickCount" label="点击次数" width="100" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="scope">
            <el-switch
              v-model="scope.row.enabled"
              @change="toggleEnabled(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="editQuestion(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteQuestion(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadQuestions"
        style="margin-top: 20px; justify-content: center;"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="dialogTitle"
      width="600px"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="分类" prop="categoryId">
          <el-select
            v-model="form.categoryId"
            placeholder="将由模型自动带出"
            clearable
            disabled
            style="width: 100%;"
          >
            <el-option
              v-for="category in categoryOptions"
              :key="category.id"
              :label="category.categoryName"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型范围" prop="modelId">
          <el-select v-model="form.modelId" placeholder="选择模型" clearable filterable style="width: 100%;">
            <el-option
              label="通用（全部模型）"
              :value="null"
            />
            <el-option
              v-for="model in modelOptions"
              :key="model.id"
              :label="model.modelName"
              :value="model.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="问题" prop="questionText">
          <el-input v-model="form.questionText" placeholder="请输入问题" />
        </el-form-item>
        <!-- 展示位置：决定这条问题出现在 AI 问答页还是模型详情页悬浮问答 -->
        <el-form-item label="展示位置" prop="scene">
          <el-radio-group v-model="form.scene">
            <el-radio-button label="chat">AI 问答页</el-radio-button>
            <el-radio-button label="model">模型详情页</el-radio-button>
            <el-radio-button label="all">两处都显示</el-radio-button>
          </el-radio-group>
          <div class="scene-tip">
            AI 问答页＝左侧菜单进入的独立页面；模型详情页＝右下角悬浮问答。
          </div>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const questions = ref([])
const categoryOptions = ref([])
const modelOptions = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const dialogTitle = ref('添加问题')
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  categoryId: null,
  modelId: null,
  questionText: '',
  sortOrder: 0,
  enabled: true,
  scene: 'all'
})

const rules = {
  questionText: [{ required: true, message: '请输入问题', trigger: 'blur' }]
}

/** 展示位置的显示文案与配色 */
const sceneLabel = (scene) => {
  if (scene === 'chat') return 'AI 问答页'
  if (scene === 'model') return '模型详情页'
  return '两处都显示'
}

const sceneTagType = (scene) => {
  if (scene === 'chat') return 'success'
  if (scene === 'model') return 'warning'
  return 'info'
}

onMounted(() => {
  loadQuestions()
  loadCategoryOptions()
  loadModelOptions()
})

const loadQuestions = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/preset-questions', {
      params: { page: currentPage.value, pageSize: pageSize.value }
    })
    questions.value = response.data.data?.records || []
    total.value = response.data.data?.total || 0
  } catch (error) {
    console.error('[PresetQuestionManage] 加载问题列表失败', error)
  } finally {
    loading.value = false
  }
}

const loadCategoryOptions = async () => {
  try {
    const response = await request.get('/categories')
    categoryOptions.value = response.data.data || []
  } catch (error) {
    console.error('[PresetQuestionManage] 加载分类失败', error)
  }
}

const loadModelOptions = async () => {
  try {
    // 下拉要能选到最新加的模型，多拉一些
    const response = await request.get('/models', { params: { page: 1, pageSize: 500, sortBy: 'id' } })
    const data = response.data
    modelOptions.value = data?.content || data?.records || data?.data?.records || []
  } catch (error) {
    console.error('[PresetQuestionManage] 加载模型列表失败', error)
  }
}

// 选了模型就跟着把分类也带上，省得分类和模型对不上
watch(
  () => form.modelId,
  (modelId) => {
    if (!modelId) {
      form.categoryId = null
      return
    }
    const model = modelOptions.value.find(m => m.id === modelId)
    if (!model) return
    if (model.categoryId) {
      form.categoryId = model.categoryId
      return
    }
    // 老数据可能没存 categoryId，只能按分类名碰一碰
    if (model.categoryName) {
      const matchedCategory = categoryOptions.value.find(c => c.categoryName === model.categoryName)
      if (matchedCategory) form.categoryId = matchedCategory.id
    }
  }
)

const getCategoryName = (categoryId) => {
  if (!categoryId) return '全部'
  const category = categoryOptions.value.find(c => c.id === categoryId)
  return category ? category.categoryName : '-'
}

const getModelName = (modelId) => {
  if (!modelId) return '全部'
  const model = modelOptions.value.find(m => m.id === modelId)
  return model ? model.modelName : '-'
}

const showAddDialog = () => {
  isEdit.value = false
  dialogTitle.value = '添加问题'
  Object.assign(form, {
    id: null,
    categoryId: null,
    modelId: null,
    questionText: '',
    sortOrder: 0,
    enabled: true,
    scene: 'all'
  })
  dialogVisible.value = true
}

const editQuestion = (question) => {
  isEdit.value = true
  dialogTitle.value = '编辑问题'
  Object.assign(form, {
    id: question.id,
    categoryId: question.categoryId,
    modelId: question.modelId,
    questionText: question.questionText,
    sortOrder: question.sortOrder,
    enabled: question.enabled,
    scene: question.scene || 'all'
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      const url = isEdit.value ? `/admin/preset-questions/${form.id}` : '/admin/preset-questions'
      const method = isEdit.value ? 'PUT' : 'POST'
      const payload = {
        categoryId: form.categoryId || null,
        modelId: form.modelId || null,
        questionText: form.questionText,
        sortOrder: form.sortOrder,
        enabled: form.enabled,
        scene: form.scene || 'all'
      }

      try {
        await request({
          method,
          url,
          data: payload
        })

        ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
        dialogVisible.value = false
        loadQuestions()
      } catch (error) {
        console.error('[PresetQuestionManage] 保存问题失败', error)
        ElMessage.error('保存失败')
      }
    }
  })
}

const toggleEnabled = async (question) => {
  try {
    await request.put(`/admin/preset-questions/${question.id}`, {
      categoryId: question.categoryId,
      modelId: question.modelId,
      questionText: question.questionText,
      answerText: question.answerText,
      sortOrder: question.sortOrder,
      enabled: question.enabled,
      scene: question.scene || 'all'
    })
    ElMessage.success('更新成功')
  } catch (error) {
    console.error('[PresetQuestionManage] 更新问题失败', error)
    question.enabled = !question.enabled
    ElMessage.error('更新失败')
  }
}

const deleteQuestion = async (question) => {
  try {
    await request.delete(`/admin/preset-questions/${question.id}`)
    ElMessage.success('删除成功')
    loadQuestions()
  } catch (error) {
    console.error('[PresetQuestionManage] 删除问题失败', error)
    ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.preset-question-manage {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  /* 展示位置单选下的说明文字 */
  .scene-tip {
    width: 100%;
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--v2-text-weak);
  }
}
</style>
