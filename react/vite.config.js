// vite.config.js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'D:/Learning/WhatsApp/Java/src/main/resources/static',
    emptyOutDir: true,
  }
});