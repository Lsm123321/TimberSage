<template>
  <div class="knowledge-manage">
    <!-- 顶部统计条：让运营一眼看清知识库健康状况 -->
    <div class="kb-stats">
      <div class="stat-card" v-for="s in statCards" :key="s.key" :class="s.tone">
        <div class="stat-value">{{ s.value }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <!-- 视图切换：列表和图谱看的是同一批数据，所以放在同一页切换，不另开菜单 -->
    <div class="view-switch">
      <button
        class="view-tab"
        :class="{ active: viewMode === 'list' }"
        @click="switchView('list')"
      >
        <el-icon><List /></el-icon>
        <span>列表视图</span>
      </button>
      <button
        class="view-tab"
        :class="{ active: viewMode === 'graph' }"
        @click="switchView('graph')"
      >
        <el-icon><Share /></el-icon>
        <span>图谱视图</span>
      </button>
    </div>

    <!-- 图谱视图：独立组件，按需渲染（切走就销毁，避免 ECharts 实例堆积） -->
    <KnowledgeGraph
      v-if="viewMode === 'graph'"
      ref="graphRef"
      @open-node="handleGraphOpenNode"
    />

    <!-- 列表视图 -->
    <template v-else>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>知识库管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="openCreateDialog">新增知识</el-button>
          </div>
        </div>
      </template>

      <!-- 筛选栏：模型 / 类型 / 状态 / 关键词，任一变化都回到第一页 -->
      <div class="kb-toolbar">
        <el-select
          v-model="filters.modelId"
          placeholder="全部模型"
          clearable
          filterable
          class="filter-item filter-model"
          @change="handleFilterChange"
        >
          <el-option
            v-for="m in modelOptions"
            :key="m.id"
            :label="m.modelName"
            :value="m.id"
          />
        </el-select>

        <el-select
          v-model="filters.questionType"
          placeholder="全部类型"
          clearable
          class="filter-item"
          @change="handleFilterChange"
        >
          <el-option
            v-for="t in typeOptions"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>

        <el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          class="filter-item"
          @change="handleFilterChange"
        >
          <el-option
            v-for="s in statusOptions"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>

        <el-input
          v-model="filters.keyword"
          placeholder="搜索问题或答案"
          clearable
          class="filter-item filter-keyword"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />

        <el-button type="primary" plain @click="handleFilterChange">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>

        <!-- 批量操作：只有勾选了才可用 -->
        <div class="batch-actions" v-if="selectedRows.length > 0">
          <span class="batch-tip">已选 {{ selectedRows.length }} 条</span>
          <el-button type="success" size="small" @click="handleBatch('approve')">
            批量通过
          </el-button>
          <el-button type="danger" size="small" @click="handleBatch('reject')">
            批量驳回
          </el-button>
        </div>
      </div>

      <el-table
        :data="entries"
        style="width: 100%"
        v-loading="loading"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" />

        <el-table-column prop="id" label="ID" width="70" />

        <el-table-column prop="modelName" label="模型" min-width="150" show-overflow-tooltip />

        <el-table-column prop="questionType" label="类型" width="90">
          <template #default="scope">
            <el-tag
              :type="typeTagTone(scope.row.questionType)"
              size="small"
              effect="light"
            >
              {{ typeLabel(scope.row.questionType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />

        <el-table-column prop="answer" label="答案" min-width="220" show-overflow-tooltip />

        <el-table-column prop="hitCount" label="命中" width="80" sortable>
          <template #default="scope">
            <span :class="{ 'hit-hot': scope.row.hitCount >= 10 }">
              {{ scope.row.hitCount || 0 }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="statusTagTone(scope.row.status)" size="small">
              {{ statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="210" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openEditDialog(scope.row)">
              编辑
            </el-button>
            <el-button
              v-if="scope.row.status !== 1"
              size="small"
              type="success"
              @click="handleApprove(scope.row)"
            >
              通过
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="kb-pagination"
        @current-change="loadEntries"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 新增/编辑共用一个弹窗，靠 isEdit 区分 -->
    <el-dialog
      v-model="dialogVisible"
      class="v2-unified-dialog"
      modal-class="v2-unified-overlay"
      :title="dialogTitle"
      width="680px"
      @closed="handleDialogClosed"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="96px">
        <el-form-item label="关联模型" prop="modelId">
          <el-select v-model="form.modelId" placeholder="选择模型" filterable style="width: 100%">
            <el-option
              v-for="m in modelOptions"
              :key="m.id"
              :label="m.modelName"
              :value="m.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="知识类型" prop="questionType">
          <el-select v-model="form.questionType" placeholder="选择类型" style="width: 100%">
            <el-option
              v-for="t in typeOptions"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="问题" prop="question">
          <el-input
            v-model="form.question"
            type="textarea"
            :rows="2"
            placeholder="用户可能怎么问，就怎么写"
          />
        </el-form-item>

        <!-- 实时预览归一化结果：让管理员看到这句话实际会被匹配成什么形式。
             匹配效果强依赖归一化质量，把它暴露出来能大幅降低"改了问题却匹配不上"的困惑 -->
        <el-form-item label="归一化预览">
          <div class="normalize-preview">
            {{ normalizePreview || '（输入问题后自动生成）' }}
          </div>
        </el-form-item>

        <el-form-item label="答案" prop="answer">
          <el-input
            v-model="form.answer"
            type="textarea"
            :rows="9"
            placeholder="标准答案，建议 150-280 字，客观准确"
            show-word-limit
            maxlength="2000"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="0">候选</el-radio>
            <el-radio :label="1">通过</el-radio>
            <el-radio :label="2">驳回</el-radio>
            <el-radio :label="3">归档</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 驳回原因只在选"驳回"时出现，避免表单长期多一个必填项 -->
        <el-form-item v-if="form.status === 2" label="驳回原因">
          <el-input v-model="form.rejectReason" placeholder="说明为什么不通过" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, Share } from '@element-plus/icons-vue'
import KnowledgeGraph from './KnowledgeGraph.vue'
import {
  listKnowledge,
  getKnowledgeStats,
  createKnowledge,
  updateKnowledge,
  deleteKnowledge,
  changeKnowledgeStatus,
  batchKnowledgeStatus,
  getModelOptions,
  getCategoryOptions
} from '@/api/knowledge'

// ---------------------------------------------------------------- 视图切换

// 列表和图谱是同一批数据的两种看法，用切换而不是两个菜单
const viewMode = ref('list')
const graphRef = ref(null)

function switchView(mode) {
  if (viewMode.value === mode) return
  viewMode.value = mode
  // 从图谱切回列表时刷新一次，图谱里如果点过审核，列表数据可能已变
  if (mode === 'list') {
    loadEntries()
    loadStats()
  }
}

/**
 * 图谱里点知识类型节点时，切回列表并按模型+类型筛选。
 * 这样"看到图 → 看具体内容"是一条连贯的动线，不用用户自己去重新筛。
 */
function handleGraphOpenNode({ modelId, questionType }) {
  viewMode.value = 'list'
  filters.modelId = modelId || null
  filters.questionType = questionType || null
  filters.status = null
  filters.keyword = ''
  currentPage.value = 1
  loadEntries()
}

// ---------------------------------------------------------------- 选项常量

const typeOptions = [
  { value: 'structure', label: '结构' },
  { value: 'material', label: '材料' },
  { value: 'history', label: '历史' },
  { value: 'culture', label: '文化' },
  { value: 'aesthetic', label: '审美' },
  { value: 'compare', label: '对比' }
]

const statusOptions = [
  { value: 0, label: '候选' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已归档' }
]

const typeLabel = (v) => typeOptions.find((t) => t.value === v)?.label || v || '-'
const statusLabel = (v) => statusOptions.find((s) => s.value === v)?.label || '-'

// el-tag 的 type 只接受固定几个值，这里做一层映射
const typeTagTone = (v) =>
  ({ structure: '', material: 'success', history: 'warning',
     culture: 'danger', aesthetic: 'info', compare: 'primary' })[v] || 'info'

const statusTagTone = (v) =>
  ({ 0: 'info', 1: 'success', 2: 'danger', 3: 'info' })[v] || 'info'

// ---------------------------------------------------------------- 列表状态

const loading = ref(false)
const entries = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedRows = ref([])

const filters = reactive({
  modelId: null,
  questionType: null,
  status: null,
  keyword: ''
})

const modelOptions = ref([])
const categoryOptions = ref([])
const stats = ref({})

const statCards = computed(() => [
  { key: 'total', label: '知识总数', value: stats.value.total ?? 0, tone: 'tone-neutral' },
  { key: 'approved', label: '已通过', value: stats.value.approved ?? 0, tone: 'tone-ok' },
  { key: 'candidate', label: '待审核', value: stats.value.candidate ?? 0, tone: 'tone-warn' },
  { key: 'hits', label: '累计命中', value: stats.value.totalHits ?? 0, tone: 'tone-accent' },
  { key: 'saved', label: '已省 Token', value: stats.value.tokensSaved ?? 0, tone: 'tone-cool' }
])

async function loadEntries() {
  loading.value = true
  try {
    const res = await listKnowledge({
      page: currentPage.value,
      pageSize: pageSize.value,
      modelId: filters.modelId || undefined,
      questionType: filters.questionType || undefined,
      status: filters.status === null || filters.status === '' ? undefined : filters.status,
      keyword: filters.keyword || undefined
    })
    const data = res.data?.data || {}
    entries.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    // request 拦截器已经弹过错误提示，这里只兜底清空，避免表格残留旧数据
    entries.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getKnowledgeStats()
    stats.value = res.data?.data || {}
  } catch (e) {
    stats.value = {}
  }
}

async function loadOptions() {
  try {
    const [modelRes, categoryRes] = await Promise.all([
      getModelOptions(),
      getCategoryOptions()
    ])
    // 模型接口返回 Spring Page，不同版本字段名不一致，这里做兼容
    const md = modelRes.data?.data ?? modelRes.data
    modelOptions.value = md?.content || md?.records || md?.data?.records || []

    const cd = categoryRes.data?.data ?? categoryRes.data
    categoryOptions.value = Array.isArray(cd) ? cd : []
  } catch (e) {
    modelOptions.value = []
    categoryOptions.value = []
  }
}

function handleFilterChange() {
  currentPage.value = 1
  loadEntries()
}

function resetFilters() {
  filters.modelId = null
  filters.questionType = null
  filters.status = null
  filters.keyword = ''
  currentPage.value = 1
  loadEntries()
}

function handleSizeChange() {
  currentPage.value = 1
  loadEntries()
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

// ---------------------------------------------------------------- 弹窗与表单

const dialogVisible = ref(false)
const dialogTitle = ref('新增知识')
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  modelId: null,
  questionType: 'structure',
  question: '',
  answer: '',
  status: 0,
  rejectReason: ''
})

const rules = {
  modelId: [{ required: true, message: '请选择关联模型', trigger: 'change' }],
  questionType: [{ required: true, message: '请选择知识类型', trigger: 'change' }],
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }]
}

/**
 * 前端也做一份归一化预览。
 * 这里刻意和后端 KnowledgeService.normalize 保持同一套规则，
 * 只用于展示，实际匹配仍以服务端为准。
 */
const normalizePreview = computed(() => {
  const q = form.question
  if (!q || !q.trim()) return ''
  let s = q.trim().toLowerCase()
  const syn = [
    [/这座桥|这个桥|这桥|该桥|此桥/g, '〔指代〕'],
    [/这座建筑|这个建筑|该建筑模型|此建筑/g, '〔指代〕'],
    [/这个模型|该模型|这座模型/g, '〔指代〕'],
    [/它|其/g, ''],
    [/有什么(特点|特色|特征)/g, '有哪些特点'],
    [/为什么|为何|怎么会/g, '为何'],
    [/是什么|是啥|指什么/g, '是什么'],
    [/介绍一下|介绍下|讲讲|说说|讲一讲|说一说/g, '介绍'],
    [/怎么样|如何/g, '如何'],
    [/有哪些|有什么|有啥/g, '有哪些'],
    [/主要|一般|通常/g, '']
  ]
  syn.forEach(([re, rep]) => { s = s.replace(re, rep) })
  s = s.replace(/[\s\u3000!-/:-@[-`{-~，。、；：？！…—－（）《》〈〉“”‘’·「」『』【】]/g, '')
  s = s.replace(/(呢|吗|呀|吧|啊|嘛|请问|你好|谢谢)+$/, '')
  return s
})

function resetForm() {
  form.id = null
  form.modelId = null
  form.questionType = 'structure'
  form.question = ''
  form.answer = ''
  form.status = 0
  form.rejectReason = ''
}

function openCreateDialog() {
  isEdit.value = false
  dialogTitle.value = '新增知识'
  resetForm()
  // 如果列表正按某个模型筛选，新增时默认沿用，减少重复选择
  if (filters.modelId) {
    form.modelId = filters.modelId
  }
  // 新增默认落成候选，人工审核后才上线
  form.status = 0
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  dialogTitle.value = `编辑知识 #${row.id}`
  form.id = row.id
  form.modelId = row.modelId
  form.questionType = row.questionType || 'structure'
  form.question = row.question
  form.answer = row.answer
  form.status = row.status
  form.rejectReason = row.rejectReason || ''
  dialogVisible.value = true
}

function handleDialogClosed() {
  formRef.value?.clearValidate?.()
}

async function submitForm() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    const payload = {
      modelId: form.modelId,
      question: form.question,
      answer: form.answer,
      questionType: form.questionType,
      status: form.status,
      rejectReason: form.status === 2 ? form.rejectReason : null
    }
    try {
      if (isEdit.value) {
        await updateKnowledge(form.id, payload)
        ElMessage.success('保存成功，缓存已失效')
      } else {
        await createKnowledge(payload)
        ElMessage.success(form.status === 1 ? '新增成功' : '已加入待审核')
      }
      dialogVisible.value = false
      await Promise.all([loadEntries(), loadStats()])
    } catch (e) {
      // 拦截器已提示错误，保持弹窗打开让用户修改
    } finally {
      saving.value = false
    }
  })
}

// ---------------------------------------------------------------- 审核与删除

async function handleApprove(row) {
  try {
    await changeKnowledgeStatus(row.id, 'approve')
    ElMessage.success('已通过，立即参与检索')
    await Promise.all([loadEntries(), loadStats()])
  } catch (e) {
    // 拦截器已提示
  }
}

async function handleBatch(action) {
  const ids = selectedRows.value.map((r) => r.id)
  if (!ids.length) return

  let reason = null
  if (action === 'reject') {
    try {
      const { value } = await ElMessageBox.prompt(
        `将驳回 ${ids.length} 条知识，请填写原因`,
        '批量驳回',
        { inputPlaceholder: '例如：答案与史料不符', confirmButtonText: '确认驳回', cancelButtonText: '取消' }
      )
      reason = value
    } catch (e) {
      return // 用户取消
    }
  } else {
    try {
      await ElMessageBox.confirm(
        `将 ${ids.length} 条知识设为已通过，通过后会立即参与检索`,
        '批量通过',
        { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
      )
    } catch (e) {
      return
    }
  }

  try {
    const res = await batchKnowledgeStatus(ids, action, reason)
    const updated = res.data?.data?.updated ?? ids.length
    ElMessage.success(`已处理 ${updated} 条`)
    selectedRows.value = []
    await Promise.all([loadEntries(), loadStats()])
  } catch (e) {
    // 拦截器已提示
  }
}

async function handleDelete(row) {
  // 删除必须二次确认：这条知识可能是花人工审过的
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.question}」吗？删除后不可恢复。\n如果是暂时下线，建议改用「归档」。`,
      '删除知识',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteKnowledge(row.id)
    ElMessage.success('已删除')
    await Promise.all([loadEntries(), loadStats()])
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(() => {
  loadOptions()
  loadEntries()
  loadStats()
})
</script>

<style scoped>
.knowledge-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 统计条：五行卡片，用色区分状态含义，不用纯数字堆砌 */
.kb-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

/*
  知识库这页原本按「宣纸白底」写死了一套暖棕配色，
  在深色主题下会变成一片刺眼白块。统一换成主题 token：
  深色是墨底金线，浅色回到原来的宣纸棕调。
*/
.stat-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--v2-surface);
  border: 1px solid var(--v2-border-soft);
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: transform 0.18s cubic-bezier(0.16, 1, 0.3, 1),
              box-shadow 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--v2-shadow-soft);
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  color: var(--v2-text-strong);
}

.stat-label {
  font-size: 12px;
  color: var(--v2-text-weak);
  letter-spacing: 0.02em;
}

.stat-card.tone-ok { border-color: var(--v2-ok-border); }
.stat-card.tone-ok .stat-value { color: var(--v2-ok-text); }
.stat-card.tone-warn { border-color: var(--v2-warn-border); }
.stat-card.tone-warn .stat-value { color: var(--v2-warn-text); }
.stat-card.tone-accent { border-color: var(--v2-chip-border); }
.stat-card.tone-accent .stat-value { color: var(--v2-chip-text); }
.stat-card.tone-cool { border-color: var(--v2-info-border); }
.stat-card.tone-cool .stat-value { color: var(--v2-info-text); }

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: var(--v2-text-strong);
}

/* 视图切换：做成两个并排的胶囊，不做成 el-radio 是因为想要图标+文字的横向排布 */
.view-switch {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: var(--v2-surface-soft);
  border: 1px solid var(--v2-border-soft);
  align-self: flex-start;
}

.view-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--v2-text-body);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.view-tab:hover {
  color: var(--v2-text-strong);
  background: var(--v2-surface-hover);
}

/* 选中态：浮起来的一块，用暖金描边区分 */
.view-tab.active {
  background: var(--v2-surface-strong);
  color: var(--v2-chip-text);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px var(--v2-chip-border);
}

.view-tab .el-icon {
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.kb-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.filter-item {
  width: 140px;
}

.filter-model {
  width: 200px;
}

.filter-keyword {
  width: 220px;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--v2-chip-bg);
  border: 1px solid var(--v2-chip-border);
}

.batch-tip {
  font-size: 12px;
  color: var(--v2-chip-text);
}

.hit-hot {
  color: var(--v2-warn-text);
  font-weight: 600;
}

.kb-pagination {
  margin-top: 18px;
  justify-content: center;
}

/* 归一化预览：弱化成"辅助信息"的视觉层级，不抢问题/答案的注意力 */
.normalize-preview {
  width: 100%;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--v2-surface-soft);
  border: 1px dashed var(--v2-border-soft);
  color: var(--v2-text-body);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
}
</style>
