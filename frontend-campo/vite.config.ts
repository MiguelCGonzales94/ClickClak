import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      manifest: {
        name: "ClickClak — Marcación",
        short_name: "ClickClak",
        start_url: "/",
        display: "standalone",
        background_color: "#ffffff",
        theme_color: "#10192C",
        icons: [],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,ico,png,svg}"],
      },
    }),
  ],
  server: {
    port: 5174,
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
