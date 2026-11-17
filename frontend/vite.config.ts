import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  preview: { port: 4173, strictPort: true },
  test: { include: ['src/**/*.test.ts'], environment: 'node', clearMocks: true },
})
