import { defineConfig, type PluginOption } from 'vite';
import vue from '@vitejs/plugin-vue';
import cesiumPlugin from 'vite-plugin-cesium';
import { fileURLToPath, URL } from 'node:url';

// Cast to PluginOption factory to handle CJS/ESM interop typing cleanly
const cesium = (typeof cesiumPlugin === 'function'
    ? cesiumPlugin
    : (cesiumPlugin as unknown as { default: () => PluginOption }).default) as () => PluginOption;

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    cesium()
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
});