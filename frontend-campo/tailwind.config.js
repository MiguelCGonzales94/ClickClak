/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      // Paleta oficial "A · Corporate Trust" (ver prototipo/tokens.css) — mismos
      // valores, para que el frontend real no diverja del sistema de diseño aprobado.
      colors: {
        primario: "#1E4FD8",
        primarioOscuro: "#163AA6",
        acento: "#12B3A6",
        fondo: "#F4F6FB",
        superficie: "#FFFFFF",
        texto: "#1A2233",
        textoSuave: "#6B7387",
        borde: "#E1E5F0",
        peligro: "#D8433D",
        peligroFondo: "#FBEAE9",
        peligroTexto: "#A02E29",
        exito: "#1C9A6C",
        exitoFondo: "#E8F7F1",
        exitoTexto: "#146B4D",
      },
      borderRadius: {
        sm: "8px",
        md: "14px",
        lg: "20px",
      },
      boxShadow: {
        tarjeta: "0 8px 24px rgba(20, 30, 70, 0.08)",
      },
    },
  },
  plugins: [],
};
