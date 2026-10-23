import { tanstackRouter } from '@tanstack/router-plugin/vite'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { defineConfig } from 'vitest/config'

// Dev: trình duyệt chỉ nói chuyện với Vite (cùng origin), Vite chuyển tiếp /api và luồng OIDC tới Gateway.
// `changeOrigin: false` + `xfwd` giữ Host gốc và thêm X-Forwarded-*, để Gateway dựng đúng redirect_uri (overview.md §13.1).
const gateway = {
  target: process.env.GATEWAY_URL ?? 'http://localhost:8080',
  changeOrigin: false,
  xfwd: true,
}

export default defineConfig({
  plugins: [tanstackRouter({ target: 'react', autoCodeSplitting: true }), react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': gateway,
      '/oauth2': gateway,
      '/login/oauth2': gateway,
      '/logout': gateway,
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    exclude: ['e2e/**', 'node_modules/**'],
  },
})
