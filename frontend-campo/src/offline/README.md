# offline/

Cola de marcaciones pendientes de sincronizar (IndexedDB vía Dexie.js) y lógica de reintento/sync al recuperar conexión. Los controles de coherencia de GPS (desplazamiento, precisión, IP) que compensan la limitación de no poder detectar GPS falso desde el navegador viven en el **backend**, no aquí — este módulo solo encola y reenvía.
