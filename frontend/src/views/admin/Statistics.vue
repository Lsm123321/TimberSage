<template>
  <div class="statistics-page">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('modelDownloadTop10') }}</span>
            </div>
          </template>
          <div ref="modelChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('modelCategoryDistribution') }}</span>
            </div>
          </template>
          <div ref="categoryChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>{{ t('detailedDownloadLogs') }}</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="t('to')"
            :start-placeholder="t('startDate')"
            :end-placeholder="t('endDate')"
            @change="handleFilterChange"
            style="margin-left: 20px;"
          />
          <el-switch
            v-model="onlyUncategorized"
            :active-text="t('onlyUncategorized')"
            style="margin-left: 16px;"
          />
          <el-tag type="warning" effect="light" style="margin-left: 12px;">
            {{ t('uncategorizedCountLabel') }}{{ uncategorizedCount }}
          </el-tag>
        </div>
      </template>

      <el-table :data="filteredDownloadLogs" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" :label="t('downloadUser')" width="120" />
        <!--
          el-table formatter 的签名是 (row, column, cellValue, index)，
          第一个参数才是行数据。之前写成 (_, __, ___, row) 拿到的是 index（数字），
          导致模型名恒显示「-」、分类恒显示「未设置分类」。
        -->
        <el-table-column
          prop="modelName"
          :label="t('modelName')"
          min-width="150"
          :formatter="(row) => renderModelName(row)"
        />
        <el-table-column prop="dynasty" :label="t('dynasty')" width="100" />
        <el-table-column
          prop="categoryName"
          :label="t('category')"
          width="100"
          :formatter="(row) => renderCategory(row)"
        />
        <el-table-column prop="ipAddress" :label="t('ipAddress')" width="150" />
        <el-table-column prop="downloadTime" :label="t('downloadTime')" width="180" />
      </el-table>

      <!--
        服务端分页：表格只渲染当前页，翻页时向后端要新数据。
        切换「仅未归类」时在客户端再筛一次当前页，保持开关立刻生效。
      -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { useUiPreferences } from '@/composables/useUiPreferences'

const loading = ref(false)
const downloadLogs = ref([])
// 分页状态：page 从 1 开始，与后端约定一致
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const topDownloadModels = ref([])
const dateRange = ref([])
const onlyUncategorized = ref(false)
const modelChartRef = ref(null)
const categoryChartRef = ref(null)
const modelChart = ref(null)
const categoryChart = ref(null)
let autoRefreshTimer = null
const { t } = useUiPreferences()

const isUncategorizedRecord = (item) => {
  const modelExists = item?.modelExists !== false
  return modelExists && !item?.categoryName
}

// 「仅未归类」只在当前页内过滤。
// 分页后后端无法按「分类为空」筛选（要 JOIN 三张表再判空，成本高），
// 所以这里保持客户端筛选，但计数口径要跟着标明是「本页」。
const filteredDownloadLogs = computed(() => {
  if (!onlyUncategorized.value) {
    return downloadLogs.value
  }
  return downloadLogs.value.filter(isUncategorizedRecord)
})

/** 当前页内的未归类条数 */
const uncategorizedCount = computed(() =>
  downloadLogs.value.filter(isUncategorizedRecord).length
)

const renderModelName = (row) => (row.modelExists === false ? t('deletedModelLabel') : (row.modelName || '-'))
const renderCategory = (row) => {
  if (row.modelExists === false) return t('deletedModelLabel')
  return row.categoryName || t('uncategorizedLabel')
}

onMounted(async () => {
  await loadStatistics()
  await nextTick()
  renderModelChart()
  renderCategoryChart()
  // 没选日期筛选时每 15 秒自动刷新一次，看了筛选结果就不刷（避免覆盖用户选的区间）
  // 保留在用户当前页，别把正在翻页的人弹回第一页
  autoRefreshTimer = window.setInterval(() => {
    if (!dateRange.value || dateRange.value.length === 0) {
      loadStatistics()
    }
  }, 15000)
})

onBeforeUnmount(() => {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
  if (modelChart.value) {
    modelChart.value.dispose()
    modelChart.value = null
  }
  if (categoryChart.value) {
    categoryChart.value.dispose()
    categoryChart.value = null
  }
})

/**
 * 加载统计数据。
 * reloadLogs=false 时只刷新图表，不动明细分页（翻页时用，避免每次翻页都重拉排行）。
 */
const loadStatistics = async (reloadLogs = true) => {
  loading.value = true
  try {
    const filters = buildDateParams()
    const rankRequest = request.get('/admin/statistics/downloads', { params: filters })

    if (!reloadLogs) {
      const rankResponse = await rankRequest
      topDownloadModels.value = rankResponse.data.data || []
      await nextTick()
      renderModelChart()
      renderCategoryChart()
      return
    }

    const [rankResponse, logsResponse] = await Promise.all([
      rankRequest,
      request.get('/admin/statistics/download-logs', {
        params: { ...filters, page: currentPage.value, pageSize: pageSize.value }
      })
    ])
    topDownloadModels.value = rankResponse.data.data || []

    // 后端返回 PageDTO：{ total, page, pageSize, records }
    const payload = logsResponse.data.data || {}
    downloadLogs.value = payload.records || []
    total.value = payload.total || 0

    await nextTick()
    renderModelChart()
    renderCategoryChart()
  } catch (error) {
    console.error('[Statistics] 加载统计数据失败', error)
  } finally {
    loading.value = false
  }
}

/** 日期范围转接口参数；未选时不传，后端即返回全部 */
const buildDateParams = () => {
  if (!dateRange.value || dateRange.value.length !== 2) return {}
  return {
    startDate: toDateString(dateRange.value[0]),
    endDate: toDateString(dateRange.value[1])
  }
}

/**
 * 转 YYYY-MM-DD。
 * 不用 toISOString()：它按 UTC 取日期，东八区选「今天」会变成前一天。
 */
const toDateString = (d) => {
  const date = new Date(d)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadStatistics()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  // 改每页条数后回到第一页，否则可能停在一个不存在的页码上
  currentPage.value = 1
  loadStatistics()
}

/** 日期或筛选条件变了要回到第一页，不然会看到空页 */
const handleFilterChange = () => {
  currentPage.value = 1
  loadStatistics()
}

const renderModelChart = () => {
  if (!modelChartRef.value) return

  if (!modelChart.value) {
    modelChart.value = echarts.init(modelChartRef.value)
  }

  const modelData = topDownloadModels.value.map(item => ({
    name: item.modelName,
    value: item.downloadCount || 0
  }))

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: modelData.map(item => item.name),
      axisLabel: {
        rotate: 30,
        interval: 0
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: t('downloads'),
        type: 'bar',
        data: modelData.map(item => item.value),
        itemStyle: {
          color: '#667eea'
        }
      }
    ]
  }

  modelChart.value.setOption(option, true)
}

const renderCategoryChart = () => {
  if (!categoryChartRef.value) return

  if (!categoryChart.value) {
    categoryChart.value = echarts.init(categoryChartRef.value)
  }

  const categoryMap = {}
  downloadLogs.value.forEach(item => {
    const category = item.modelExists === false
      ? t('deletedModelLabel')
      : (item.categoryName || t('uncategorizedLabel'))
    categoryMap[category] = (categoryMap[category] || 0) + 1
  })

  const categoryData = Object.entries(categoryMap).map(([name, value]) => ({
    name,
    value
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: t('modelCategoryDistribution'),
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: categoryData,
        color: ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#00f2fe', '#43e97b', '#38f9d7', '#fa709a']
      }
    ]
  }

  categoryChart.value.setOption(option, true)
}
</script>

<style scoped>
.statistics-page {
  .chart-container {
    width: 100%;
    height: 350px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  /* 分页器靠右，与表格留出呼吸间隙 */
  .pagination-bar {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
