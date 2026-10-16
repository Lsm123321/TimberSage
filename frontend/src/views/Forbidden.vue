<template>
  <div class="forbidden-page">
    <el-card class="forbidden-card">
      <h1>403 无权限访问</h1>
      <p>当前账号没有访问该页面的权限，请联系管理员或返回首页。</p>
      <div class="actions">
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
</script>

<style scoped>
.forbidden-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.forbidden-card {
  width: 520px;
  text-align: center;
}

.forbidden-card h1 {
  margin-bottom: 12px;
  color: #f56c6c;
}

.forbidden-card p {
  color: #666;
}

.actions {
  margin-top: 20px;
}
</style>
