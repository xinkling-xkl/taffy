import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  // 配置开发服务器代理
  server: {
    proxy: {
      // 将 `/api` 开头的请求代理到 `http://localhost:8082`
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true, // 修改请求头中的 Host
      },
    },
    port: 8083, // 可选：修改前端开发服务器端口（默认 5173）
  },
})
