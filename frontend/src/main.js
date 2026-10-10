import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/styles/tailwind.tw.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { ElMessage } from 'element-plus'

import './assets/styles/main.scss'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
/* 语言包由 App.vue 中 <el-config-provider> 随界面中英文切换 */
app.use(ElementPlus)

app.config.globalProperties.$message = ElMessage

app.mount('#app')
