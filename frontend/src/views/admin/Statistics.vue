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
            @change="loadStatistics"
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
        <el-table-column
          prop="modelName"
          :label="t('modelName')"
          min-width="150"
          :formatter="(_, __, ___, row) => renderModelName(row)"
        />
        <el-table-column prop="dynasty" :label="t('dynasty')" width="100" />
        <el-table-column
          prop="categoryName"
          :label="t('category')"
          width="100"
          :formatter="(_, __, ___, row) => renderCategory(row)"
        />
        <el-table-column prop="ipAddress" :label="t('ipAddress')" width="150" />
        <el-table-column prop="downloadTime" :label="t('downloadTime')" width="180" />
      </el-table>
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

const filteredDownloadLogs = computed(() => {
  if (!onlyUncategorized.value) {
    return downloadLogs.value
  }
  return downloadLogs.value.filter(isUncategorizedRecord)
})

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

const loadStatistics = async () => {
  loading.value = true
  try {
    const params = {}
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0].toISOString().split('T')[0]
      params.endDate = dateRange.value[1].toISOString().split('T')[0]
    }
    const [rankResponse, logsResponse] = await Promise.all([
      request.get('/admin/statistics/downloads', { params }),
      request.get('/admin/statistics/download-logs', { params })
    ])
    topDownloadModels.value = rankResponse.data.data || []
    downloadLogs.value = logsResponse.data.data || []
    await nextTick()
    renderModelChart()
    renderCategoryChart()
  } catch (error) {
    console.error('[Statistics] 加载统计数据失败', error)
  } finally {
    loading.value = false
  }
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
}
</style>
