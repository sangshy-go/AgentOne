import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 8090,
    proxy: {
      '/api': {
        // 后端端口与根目录 .env 的 API_PORT 一致
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
