import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxy same-origin: evita depender de CORS/cross-origin fetch del navegador para
    // hablar con el backend en desarrollo (puerto 8080 por defecto, ver application.yml)
    // — el navegador solo ve peticiones a su propio origen, Vite las reenvía por detrás.
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
