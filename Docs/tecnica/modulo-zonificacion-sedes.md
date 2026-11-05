# Zonificación de sedes en el mapa

> HU06. Cambio del 4-nov-2026: antes RRHH debía **escribir a mano la latitud y la longitud** de cada sede y no había forma de corregirla ni de darla de baja. Ahora la zona se marca en un mapa. Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. Análisis: qué fallaba

| # | Problema | Dónde |
|---|---|---|
| 1 | La zona se definía tecleando coordenadas sin verlas en ningún mapa; un signo cambiado o una latitud por longitud dejaba la sede en otro país y nadie lo notaba | `PaginaSedes.tsx` |
| 2 | El formulario solo exigía que los campos no estuvieran vacíos: no comprobaba rangos ni mostraba dónde caía el punto, y un rechazo del servidor aparecía como un texto genérico bajo el botón | `PaginaSedes.tsx` |
| 3 | Solo existían *crear* y *listar*: una sede mal ubicada no se podía corregir ni desactivar | `UbicacionController` |
| 4 | Crear una sede no dejaba rastro en la bitácora | `UbicacionService` |

**Lo que se comprobó del reporte «se marcaba una zona pero no se registraba».** El servidor guarda y devuelve la zona correctamente: la prueba nueva `laZonaMarcadaSeGuardaYSeLeeIgualDesdeLaBase` registra una sede, vacía la caché de persistencia, la lee de PostGIS y compara centro y radio; y en el recorrido manual el punto quedó en la base. **No se logró reproducir el fallo original** (no se tiene el caso exacto), de modo que lo anterior descarta el almacenamiento pero no demuestra la causa en la pantalla antigua. Lo que sí cambia es que ahora la zona se ve en el mapa antes de guardar, la pantalla no deja enviar sin una zona válida y tras guardar la sede aparece en la tabla con su centro y radio, que es la comprobación visible de que quedó registrada.

## 2. Decisiones

- **Decisión: la zona sigue siendo un punto más un radio de tolerancia**, no un polígono. Es el modelo que ya usan el motor de validación contextual y la consulta `ST_Distance` de PostGIS, así que no hubo migración ni cambios en la marcación ni en la app de campo. Un polígono exigiría cambiar la columna, la validación («dentro o fuera») y todo lo que hoy razona con distancias; queda como mejora si el cliente lo pide.
- **Decisión: el mapa es Leaflet con teselas de OpenStreetMap.** Es la opción con calles reales y sin clave de API. El precio es abrir la CSP del proxy: `img-src` ahora incluye `https://tile.openstreetmap.org`. **Nada más se abrió**: scripts, estilos, conexiones y fuentes siguen limitados al propio origen, y Leaflet viaja empaquetado con el panel (no se carga de un CDN).
- **Decisión: la zona se puede marcar de cuatro formas**, para no depender solo del ratón: clic en el mapa, arrastrar el marcador, pegar «latitud, longitud» (lo que copia Google Maps; acepta espacio, punto y coma y coma decimal) o escribir los campos. Hay además «Usar mi ubicación».
- **Decisión: el radio se ajusta con un deslizador (10 a 1000 m) y con un campo numérico (hasta 5000 m, el máximo del servidor)**, y el círculo del mapa lo sigue en vivo.
- **Decisión: se dibujan atenuadas las demás sedes del mismo servicio**, con su nombre al pasar el cursor, para no encimar zonas por descuido.
- **Decisión: no se puede guardar sin zona.** La pantalla valida nombre, servicio, coordenadas en rango y radio entero antes de enviar y dice cuál es el problema; el servidor valida los rangos por su cuenta.
- **Decisión: editar no cambia el servicio de la sede.** Si una sede pasa a otro servicio, se registra otra; así las asignaciones y marcaciones históricas no cambian de significado.
- **Decisión: desactivar es una baja lógica y se rechaza (409) mientras haya asignaciones en curso o programadas en esa sede.** Las ya terminadas no cuentan. La sede se puede reactivar.
- **Decisión: todo cambio queda en la bitácora** (`ubicacion`: estado anterior y nuevo, y quién lo hizo), incluido el alta.

## 3. API

Toda la gestión es de `SUPERVISOR` y `RRHH_ADMIN`; un colaborador recibe 403.

| Endpoint | Qué hace | Errores |
|---|---|---|
| `POST /api/ubicaciones` | Registra una sede (como antes) y ahora deja bitácora | 404 servicio inexistente · 400 coordenadas o radio fuera de rango |
| `PUT /api/ubicaciones/{id}` | Corrige nombre, referencia, centro y radio; el servicio no cambia | 404 · 400 |
| `DELETE /api/ubicaciones/{id}` | Baja lógica; devuelve la sede con `activo: false` | 404 · 409 si tiene asignaciones en curso o programadas |
| `POST /api/ubicaciones/{id}/reactivar` | Vuelve a activarla | 404 |
| `GET /api/ubicaciones[?proyectoId=]` | Lista (incluye las inactivas, con su estado) | |

## 4. Pantalla

Sección **Sedes y servicios** del panel (SUPERVISOR y RRHH_ADMIN).

- **Tabla de sedes** con el centro de la zona, el radio y el estado. Menú por fila: *Editar zona* y *Desactivar*, o *Reactivar* si está inactiva.
- **Nueva sede / Editar zona:** un cuadro con el mapa. Al editar se abre encuadrado sobre la zona guardada, con el servicio bloqueado, y avisa si no hay cambios.
- **Desactivar** explica que las marcaciones ya hechas se conservan y que, si hay técnicos asignados, hay que moverlos o quitarlos antes; si el servidor rechaza, muestra su mensaje.

## 5. Pruebas

| Capa | Pruebas | Qué cubren |
|---|---|---|
| `UbicacionControllerTest` (base real) | 5 | La zona marcada se guarda y se lee igual (y `ST_Distance` al centro da menos de 1 m), editar mueve centro y radio y deja bitácora, coordenadas y radio fuera de rango y sede inexistente, desactivar con y sin asignaciones y reactivar, permisos |
| Panel (`sedes/reglas.test.ts`) | 14 | Lectura de coordenadas pegadas, formato y redondeo, radio, validación del formulario, detección de cambios, sedes vecinas y encuadre del mapa |
| Navegador (CP-M08) | recorrido manual | Ver `resultados-de-pruebas.md`, 9.10 |

## 6. Despliegue y límites

- **Hay que reaplicar la configuración del proxy.** El cambio de CSP está en `infra/nginx/plantillas/cabeceras-seguridad.inc.template`; el contenedor de Nginx se debe recrear para tomarlo (ver `despliegue-azure.md`). Sin eso el mapa se verá gris en producción. **No hay migración de base de datos.**
- **Dependencia de un servicio externo.** Las teselas las sirve OpenStreetMap; su política de uso es para tráfico moderado (un panel interno de RRHH lo es) y exige mostrar la atribución, que el mapa incluye. Si cae o se bloquea, el mapa se ve en blanco pero **las coordenadas se pueden seguir escribiendo o pegando**, así que no se pierde la función. Con mucho tráfico habría que alojar teselas propias o contratar un proveedor.
- **La IP y el navegador de quien abre el mapa llegan a OpenStreetMap** al pedir cada tesela (y el `Referer`, que la cabecera `Referrer-Policy` limita al origen del panel). Es el costo de no alojar el mapa.
- **Solo el panel de administración.** La app de campo no muestra mapa, y por eso no se tocó su CSP, aunque el proxy es compartido y la CSP sí queda abierta también allí.
- **No hay búsqueda de direcciones.** Para ubicar un lugar hay que acercarse con el mapa o pegar coordenadas; una búsqueda necesitaría abrir también `connect-src` a un geocodificador.
- **Marcar bien el centro sigue siendo responsabilidad de quien lo coloca.** El mapa lo hace visible, pero no impide ubicar una sede donde no corresponde.
- **El radio máximo es 5000 m** (límite del servidor); el deslizador llega a 1000 m por comodidad.
- **No se probó en la VM de Azure.**
