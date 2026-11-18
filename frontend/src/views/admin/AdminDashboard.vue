<template>
  <div class="admin-dashboard">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="24">
        <el-card shadow="hover" class="quick-card">
          <div class="quick-header">
            <div class="quick-title">
              <div class="quick-welcome">{{ t('adminWorkbench') }}</div>
              <div class="quick-sub">{{ t('quickEntryTodayTodos') }}</div>
            </div>
            <div class="quick-actions">
              <el-button type="primary" @click="goToPending">{{ t('goReview') }}（{{ statistics.pendingModels || 0 }}）</el-button>
              <el-button @click="goToModels">{{ t('modelManage') }}</el-button>
              <el-button @click="goToUsers">{{ t('userManage') }}</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
            <el-icon><Box /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalModels || 0 }}</div>
            <div class="stat-label">{{ t('totalModels') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.pendingModels || 0 }}</div>
            <div class="stat-label">{{ t('pendingModelsLabel') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.todayChats || 0 }}</div>
            <div class="stat-label">{{ t('todayQACount') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.pendingFeedback || 0 }}</div>
            <div class="stat-label">{{ t('pendingFeedback') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalUsers || 0 }}</div>
            <div class="stat-label">{{ t('totalUsers') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);">
            <el-icon><Download /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalDownloads || 0 }}</div>
            <div class="stat-label">{{ t('totalDownloadLogs') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #d299c2 0%, #fef9d7 100%);">
            <el-icon><Star /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalFavorites || 0 }}</div>
            <div class="stat-label">{{ t('totalFavoriteCount') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #89f7fe 0%, #66a6ff 100%);">
            <el-icon><Warning /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.pendingApplications || 0 }}</div>
            <div class="stat-label">{{ t('pendingApplications') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('trend7Days') }}</span>
        </div>
      </template>
      <div ref="chartRef" class="chart-container"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { Box, Clock, ChatDotRound, Document, User, Download, Star, Warning } from '@element-plus/icons-vue'
import { useUiPreferences } from '@/composables/useUiPreferences'

const chartRef = ref(null)
const statistics = ref({})
const router = useRouter()
const { t } = useUiPreferences()

onMounted(async () => {
  await fetchStatistics()
  await nextTick()
  renderChart()
})

const fetchStatistics = async () => {
  try {
    const response = await request.get('/admin/dashboard')
    const raw = response.data.data || {}
    statistics.value = {
      totalModels: raw.totalModels || 0,
      pendingModels: raw.pendingModels || 0,
      todayChats: raw.todayChats || 0,
      pendingFeedback: raw.pendingFeedback || 0,
      totalUsers: raw.totalUsers || 0,
      totalDownloads: raw.totalDownloads || 0,
      totalLikes: raw.totalLikes || 0,
      totalFavorites: raw.totalFavorites || 0,
      pendingApplications: raw.pendingApplications || 0,
      trend: Array.isArray(raw.trend) ? raw.trend : []
    }
  } catch (error) {
    console.error('[AdminDashboard] 获取仪表盘统计失败', error)
  }
}

const normalizeTrendRows = (rows) => {
  if (!Array.isArray(rows) || rows.length === 0) return []
  return rows.map((item) => ({
    date: item?.date || '',
    models: Number(item?.models) || 0,
    chats: Number(item?.chats) || 0,
    downloads: Number(item?.downloads) || 0
  }))
}

const renderChart = () => {
  if (!chartRef.value) return

  const chart = echarts.init(chartRef.value)
  const trend = normalizeTrendRows(statistics.value.trend)
  const legendModels = t('trendModels')
  const legendQa = t('trendQaCount')
  const legendDownloads = t('trendDownloadCount')

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: [legendModels, legendQa, legendDownloads]
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map(item => item.date)
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: legendModels,
        type: 'line',
        data: trend.map(item => item.models),
        smooth: true,
        itemStyle: { color: '#667eea' }
      },
      {
        name: legendQa,
        type: 'line',
        data: trend.map(item => item.chats),
        smooth: true,
        itemStyle: { color: '#4facfe' }
      },
      {
        name: legendDownloads,
        type: 'line',
        data: trend.map(item => item.downloads),
        smooth: true,
        itemStyle: { color: '#43e97b' }
      }
    ]
  }

  chart.setOption(option)
}

const goToPending = () => router.push({ path: '/admin/models', query: { status: 0 } })
const goToModels = () => router.push('/admin/models')
const goToUsers = () => router.push('/admin/users')
</script>

<style scoped>
.admin-dashboard {
  .stats-row {
    margin-bottom: 20px;
  }

  .quick-card {
    .quick-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }

    .quick-title {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .quick-welcome {
      font-size: 18px;
      font-weight: 700;
      color: var(--v2-text-strong);
    }

    .quick-sub {
      font-size: 13px;
      color: var(--v2-text-weak);
    }

    .quick-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }
  }

  .stat-card {
    display: flex;
    align-items: center;
    padding: 20px;

    .stat-icon {
      width: 60px;
      height: 60px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 15px;

      .el-icon {
        font-size: 28px;
        color: var(--v2-accent-contrast);
      }
    }

    .stat-info {
      flex: 1;

      .stat-value {
        font-size: 28px;
        font-weight: bold;
        color: var(--v2-text-strong);
      }

      .stat-label {
        font-size: 14px;
        color: var(--v2-text-weak);
      }
    }
  }

  .chart-card {
    .chart-container {
      width: 100%;
      height: 350px;
    }
  }
}
</style>
