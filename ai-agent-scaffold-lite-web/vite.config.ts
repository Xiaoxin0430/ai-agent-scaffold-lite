import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/team/',
  build: {
    outDir: '../docs/dev-ops/nginx/html/team',
    emptyOutDir: true,
  },
});
