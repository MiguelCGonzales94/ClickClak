/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Misma familia de marca que frontend-campo (prototipo/tokens.css), más los
        // tonos oscuros que necesita el shell tipo panel-de-escritorio (sidebar oscuro +
        // contenido claro) que pidió el usuario, ejemplo "Felicita".
        primario: "#1E4FD8",
        primarioOscuro: "#163AA6",
        acento: "#12B3A6",

        fondo: "#F5F6FA",
        superficie: "#FFFFFF",
        texto: "#1A2233",
        textoSuave: "#6B7387",
        borde: "#E1E5F0",

        // Sidebar / superficies oscuras.
        panelOscuro: "#12141C",
        panelOscuroHover: "#1C1F2B",
        panelOscuroBorde: "#252836",
        textoClaro: "#E8EAF0",
        textoClaroSuave: "#8B92A8",

        // Login: negro puro (panel izquierdo) + azul profundo (panel derecho, huella).
        loginNegro: "#08090C",
        loginAzulProfundo: "#0B1730",
        loginAzulBrillo: "#173B7A",

        peligro: "#D8433D",
        peligroFondo: "#FBEAE9",
        peligroTexto: "#A02E29",
        exito: "#1C9A6C",
        exitoFondo: "#E8F7F1",
        exitoTexto: "#146B4D",
        advertencia: "#C98A12",
        advertenciaFondo: "#FDF3E0",
        advertenciaTexto: "#8A5F0C",
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
