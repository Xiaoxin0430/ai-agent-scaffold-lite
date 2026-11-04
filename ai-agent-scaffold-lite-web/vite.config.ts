import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/canvas/',
  build: {
    outDir: '../docs/dev-ops/nginx/html/canvas',
    emptyOutDir: true,
  },
});
