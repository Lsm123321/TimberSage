import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 开发模式下反代 Docker MinIO（19100→9000），与生产 nginx.conf 的 /minio/ 行为一致，
      // 保证后端生成的 SPRING_MINIO_PUBLIC_ENDPOINT 链接（/minio/...）在 dev 下同样可加载
      '/minio': {
        target: 'http://localhost:19100',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/minio/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('three')) return 'vendor-three'
          if (id.includes('echarts')) return 'vendor-echarts'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element-plus'
          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'vendor-vue'
          return 'vendor-misc'
        }
      }
    }
  }
})
