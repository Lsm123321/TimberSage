<template>
  <div class="admin-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>管理后台</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="仪表盘" name="dashboard">
          <div class="dashboard-content">
            <div class="stats-grid">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-item">
                  <div class="stat-number">{{ statistics.totalModels || 0 }}</div>
                  <div class="stat-label">模型总数</div>
                </div>
              </el-card>
              <el-card shadow="hover" class="stat-card">
                <div class="stat-item">
                  <div class="stat-number">{{ statistics.pendingModels || 0 }}</div>
                  <div class="stat-label">待审核模型</div>
                </div>
              </el-card>
              <el-card shadow="hover" class="stat-card">
                <div class="stat-item">
                  <div class="stat-number">{{ statistics.todayChats || 0 }}</div>
                  <div class="stat-label">今日问答</div>
                </div>
              </el-card>
              <el-card shadow="hover" class="stat-card">
                <div class="stat-item">
                  <div class="stat-number">{{ statistics.todayDownloads || 0 }}</div>
                  <div class="stat-label">今日下载</div>
                </div>
              </el-card>
              <el-card shadow="hover" class="stat-card">
                <div class="stat-item">
                  <div class="stat-number">{{ statistics.totalUsers || 0 }}</div>
                  <div class="stat-label">用户总数</div>
                </div>
              </el-card>
              <el-card shadow="hover" class="stat-card">
                <div class="stat-item">
                  <div class="stat-number">{{ statistics.pendingApplications || 0 }}</div>
                  <div class="stat-label">待审批申请</div>
                </div>
              </el-card>
            </div>

            <div class="chart-section">
              <el-card shadow="hover">
                <template #header>
                  <div class="chart-header">
                    <span>近7日趋势</span>
                  </div>
                </template>
                <div ref="chartRef" style="width: 100%; height: 400px;"></div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="用户管理" name="users">
          <div class="users-content">
            <el-table :data="usersData" style="width: 100%">
              <el-table-column prop="id" label="ID" width="80"></el-table-column>
              <el-table-column prop="username" label="用户名"></el-table-column>
              <el-table-column prop="email" label="邮箱"></el-table-column>
              <el-table-column prop="role" label="角色">
                <template #default="scope">
                  <el-select v-model="scope.row.role" @change="updateUserRole(scope.row.id, scope.row.role)">
                    <el-option label="普通用户" value="user"></el-option>
                    <el-option label="贡献者" value="contributor"></el-option>
                    <el-option label="管理员" value="admin"></el-option>
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间"></el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="模型管理" name="models">
          <div class="models-content">
            <el-table :data="modelsData" style="width: 100%">
              <el-table-column prop="id" label="ID" width="80"></el-table-column>
              <el-table-column prop="modelName" label="模型名称"></el-table-column>
              <el-table-column prop="dynasty" label="朝代"></el-table-column>
              <el-table-column prop="viewCount" label="浏览量"></el-table-column>
              <el-table-column prop="downloadCount" label="下载量"></el-table-column>
              <el-table-column prop="status" label="状态">
                <template #default="scope">
                  <el-tag :type="scope.row.status === 1 ? 'success' : 'warning'">
                    {{ scope.row.status === 1 ? '已审核' : '待审核' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="scope">
                  <el-button type="primary" size="small" @click="viewModel(scope.row.id)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="下载统计" name="downloads">
          <div class="downloads-content">
            <el-table :data="downloadsData" style="width: 100%">
              <el-table-column prop="modelId" label="模型ID" width="80"></el-table-column>
              <el-table-column prop="modelName" label="模型名称"></el-table-column>
              <el-table-column prop="downloadCount" label="下载量"></el-table-column>
              <el-table-column prop="dynasty" label="朝代"></el-table-column>
              <el-table-column prop="category" label="分类"></el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'

const router = useRouter()
const activeTab = ref('dashboard')
const chartRef = ref(null)
const chartInstance = ref(null)

const statistics = ref({})
const usersData = ref([])
const modelsData = ref([])
const downloadsData = ref([])

const fetchDashboardData = async () => {
  try {
    const response = await fetch('/api/admin/dashboard')
    const data = await response.json()
    if (data.code === 200) {
      statistics.value = data.data
      renderChart(data.data.trend)
    }
  } catch (error) {
    console.error('[Admin] 获取仪表盘数据失败', error)
  }
}

const fetchUsersData = async () => {
  try {
    const response = await fetch('/api/admin/users')
    const data = await response.json()
    if (data.code === 200) {
      usersData.value = data.data.records
    }
  } catch (error) {
    console.error('[Admin] 获取用户数据失败', error)
  }
}

const fetchModelsData = async () => {
  try {
    const response = await fetch('/api/models')
    const data = await response.json()
    if (data.code === 200) {
      modelsData.value = data.data.records
    }
  } catch (error) {
    console.error('[Admin] 获取模型数据失败', error)
  }
}

const fetchDownloadsData = async () => {
  try {
    const response = await fetch('/api/admin/statistics/downloads')
    const data = await response.json()
    if (data.code === 200) {
      downloadsData.value = data.data
    }
  } catch (error) {
    console.error('[Admin] 获取下载统计失败', error)
  }
}

const updateUserRole = async (userId, role) => {
  try {
    const response = await fetch(`/api/admin/users/${userId}/role`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${window.localStorage.getItem('token')}`
      },
      body: JSON.stringify({ role })
    })
    const data = await response.json()
    if (data.code !== 200) {
      alert('更新角色失败')
    }
  } catch (error) {
    console.error('[Admin] 更新角色失败', error)
  }
}

const viewModel = (modelId) => {
  router.push(`/buildings/${modelId}`)
}

const renderChart = (trendData) => {
  nextTick(() => {
    if (chartInstance.value) {
      chartInstance.value.dispose()
    }
    chartInstance.value = echarts.init(chartRef.value)
    
    const option = {
      tooltip: {
        trigger: 'axis'
      },
      legend: {
        data: ['模型', '问答', '下载']
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
        data: trendData.map(item => item.date)
      },
      yAxis: {
        type: 'value'
      },
      series: [
        {
          name: '模型',
          type: 'line',
          data: trendData.map(item => item.models)
        },
        {
          name: '问答',
          type: 'line',
          data: trendData.map(item => item.chats)
        },
        {
          name: '下载',
          type: 'line',
          data: trendData.map(item => item.downloads)
        }
      ]
    }
    
    chartInstance.value.setOption(option)
  })
}

onMounted(() => {
  fetchDashboardData()
  fetchUsersData()
  fetchModelsData()
  fetchDownloadsData()
})
</script>

<style lang="scss" scoped>
.admin-container {
  padding: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .dashboard-content {
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 20px;
      margin-bottom: 20px;

      .stat-card {
        .stat-item {
          display: flex;
          flex-direction: column;
          align-items: center;
          padding: 20px;

          .stat-number {
            font-size: 32px;
            font-weight: bold;
            color: #409eff;
            margin-bottom: 10px;
          }

          .stat-label {
            font-size: 14px;
            color: #606266;
          }
        }
      }
    }

    .chart-section {
      margin-top: 20px;
    }
  }

  .users-content,
  .models-content,
  .downloads-content {
    margin-top: 20px;
  }
}
</style>
