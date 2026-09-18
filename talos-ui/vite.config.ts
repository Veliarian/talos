import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
// @ts-ignore: плагін має нюанси з типами за замовчуванням у bundler-режимі
import cesium from 'vite-plugin-cesium';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), (cesium as any)()],
  server: {
    port: 3000
  }
});