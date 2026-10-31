import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 开发期代理只解决「同源」问题，让浏览器不触发跨域。
 * 目标地址由 VITE_PROXY_TARGET 控制（见 .env.development），
 * 生产构建不依赖代理，直接按 VITE_API_BASE_URL 访问后端。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'

  return {
    plugins: [vue()],
    server: {
      port: Number(env.VITE_DEV_PORT || 5173),
      proxy: {
        '/api': { target, changeOrigin: true },
        '/files': { target, changeOrigin: true },
        // 站内消息实时推送（WebSocket 升级请求必须带 ws: true）
        '/ws': { target, changeOrigin: true, ws: true }
      }
    },
    // 生产构建预览（npm run build && npx vite preview）：与 dev 同样的代理规则，
    // 提供一条不经过 Vite dev 转换链的访问路径（产物全预编译，对严格 CSP 环境更友好）
    preview: {
      port: Number(env.VITE_PREVIEW_PORT || 5174),
      proxy: {
        '/api': { target, changeOrigin: true },
        '/files': { target, changeOrigin: true },
        '/ws': { target, changeOrigin: true, ws: true }
      }
    },
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 1500
    }
  }
})
