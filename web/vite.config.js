import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        // Rolldown (Vite 8) yêu cầu manualChunks là FUNCTION, không phải object.
        // Match từng path module → tên chunk vendor tương ứng.
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (/[\\/]node_modules[\\/](react|react-dom|react-router-dom)[\\/]/.test(id)) return 'vendor-react'
          if (/[\\/]node_modules[\\/](antd|@ant-design)[\\/]/.test(id))                  return 'vendor-antd'
          if (/[\\/]node_modules[\\/]@supabase[\\/]/.test(id))                            return 'vendor-supabase'
          if (/[\\/]node_modules[\\/]framer-motion[\\/]/.test(id))                        return 'vendor-motion'
          if (/[\\/]node_modules[\\/]@uiw[\\/]react-md-editor[\\/]/.test(id))             return 'vendor-md'
          if (/[\\/]node_modules[\\/]html2pdf|html2canvas|jspdf[\\/]/.test(id))           return 'vendor-pdf'
          return undefined
        },
      },
    },
    // Warn if chunk > 500 KB (gzip target)
    chunkSizeWarningLimit: 500,
  },
})
