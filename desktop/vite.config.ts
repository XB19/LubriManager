import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Port 5173 (Vite par défaut) est déjà autorisé par le CORS du backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: false,
  },
  clearScreen: false,
});
