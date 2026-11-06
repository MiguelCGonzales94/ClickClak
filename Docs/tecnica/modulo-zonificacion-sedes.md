# Zonificación de sedes en el mapa y CRUD de sedes y servicios

> HU06. Cambio del 4 y 5-nov-2026: antes RRHH debía **escribir a mano la latitud y la longitud** de cada sede y no había forma de corregir ni dar de baja ni una sede ni un servicio. Ahora la zona se marca en un mapa y tanto las sedes como los servicios se editan, desactivan y eliminan. Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. Análisis: qué fallaba

| # | Problema | Dónde |
|---|---|---|
| 1 | La zona se definía tecleando coordenadas sin verlas en ningún mapa; un signo cambiado o una latitud por longitud dejaba la sede en otro país y nadie lo notaba | `PaginaSedes.tsx` |
| 2 | El formulario solo exigía que los campos no estuvieran vacíos: no comprobaba rangos ni mostraba dónde caía el punto, y un rechazo del servidor aparecía como un texto genérico bajo el botón | `PaginaSedes.tsx` |
| 3 | Solo existían *crear* y *listar*: una sede mal ubicada no se podía corregir ni desactivar | `UbicacionController` |
| 4 | Crear una sede o un servicio no dejaba rastro en la bitácora | `UbicacionService`, `ProyectoService` |
| 5 | Los **servicios** tampoco se podían editar, dar de baja ni borrar (un nombre mal escrito quedaba para siempre) | `ProyectoController` |
| 6 | Desactivar una sede o un servicio no habría servido: ni el servidor ni los selectores de asignaciones ignoraban lo inactivo | `AsignacionService`, panel |

**Lo que se comprobó del reporte «se marcaba una zona pero no se registraba».** El servidor guarda y devuelve la zona correctamente: la prueba nueva `laZonaMarcadaSeGuardaYSeLeeIgualDesdeLaBase` registra una sede, vacía la caché de persistencia, la lee de PostGIS y compara centro y radio; y en el recorrido manual el punto quedó en la base. **No se logró reproducir el fallo original** (no se tiene el caso exacto), de modo que lo anterior descarta el almacenamiento pero no demuestra la causa en la pantalla antigua. Lo que sí cambia es que ahora la zona se ve en el mapa antes de guardar, la pantalla no deja enviar sin una zona válida y tras guardar la sede aparece en la tabla con su centro y radio, que es la comprobación visible de que quedó registrada.

## 2. Decisiones

- **Decisión: la zona sigue siendo un punto más un radio de tolerancia**, no un polígono. Es el modelo que ya usan el motor de validación contextual y la consulta `ST_Distance` de PostGIS, así que no hubo migración ni cambios en la marcación ni en la app de campo. Un polígono exigiría cambiar la columna, la validación («dentro o fuera») y todo lo que hoy razona con distancias; queda como mejora si el cliente lo pide.
- **Decisión: el mapa es Leaflet con teselas de OpenStreetMap.** Es la opción con calles reales y sin clave de API. El precio es abrir la CSP del proxy: `img-src` ahora incluye `https://tile.openstreetmap.org`. **Nada más se abrió**: scripts, estilos, conexiones y fuentes siguen limitados al propio origen, y Leaflet viaja empaquetado con el panel (no se carga de un CDN).
- **Decisión: la zona se puede marcar de cuatro formas**, para no depender solo del ratón: clic en el mapa, arrastrar el marcador, pegar «latitud, longitud» (lo que copia Google Maps; acepta espacio, punto y coma y coma decimal) o escribir los campos. Hay además «Usar mi ubicación».
- **Decisión: el radio se ajusta con un deslizador (10 a 1000 m) y con un campo numérico (hasta 5000 m, el máximo del servidor)**, y el círculo del mapa lo sigue en vivo.
- **Decisión: se muestra la dirección aproximada del punto** (5-nov-2026), arriba del mapa y en el detalle de una marcación, para saber qué lugar toma el punto. En la sede hay además «Usar como referencia», que copia esa dirección al campo *Referencia*; no se copia sola para no escribir sobre lo que la persona puso.
- **Decisión: la dirección la consulta el servidor, no el navegador.** `GET /api/geocodificacion/inversa?latitud=&longitud=` (SUPERVISOR y RRHH_ADMIN) llama a Nominatim de OpenStreetMap. Así la política de seguridad del proxy **no se abre** a otro origen para conexiones (`connect-src` sigue en `'self'`), el navegador nunca habla con un tercero y un único punto cumple la política de uso del servicio: se identifica con `User-Agent: ClickClak-Panel/1.0`, espacia las consultas (1,1 s) y recuerda las respuestas por punto (4 decimales, ~11 m; hasta 500). El panel espera 600 ms a que el marcador deje de moverse antes de pedirla. Si el servicio no responde, el servidor contesta 502 y la pantalla dice que no se pudo consultar, sin afectar las coordenadas. La URL se cambia con `clickclak.geocodificacion.url`.
- **Decisión: se dibujan atenuadas las demás sedes del mismo servicio**, con su nombre al pasar el cursor, para no encimar zonas por descuido.
- **Decisión: no se puede guardar sin zona.** La pantalla valida nombre, servicio, coordenadas en rango y radio entero antes de enviar y dice cuál es el problema; el servidor valida los rangos por su cuenta.
- **Decisión: editar no cambia el servicio de la sede.** Si una sede pasa a otro servicio, se registra otra; así las asignaciones y marcaciones históricas no cambian de significado.
- **Decisión: desactivar es una baja lógica y se rechaza (409) mientras haya asignaciones en curso o programadas en esa sede o servicio.** Las ya terminadas no cuentan. Se puede reactivar.
- **Decisión: eliminar (borrado físico) solo si nunca se usó**, la misma regla que ya tiene Usuarios. Una **sede** se elimina si ninguna asignación (ni siquiera una quitada) la usó, porque las marcaciones cuelgan de las asignaciones. Un **servicio** se elimina si nunca tuvo sedes ni asignaciones. Con historial el servidor responde 409 y la pantalla ofrece desactivar. Sirve para corregir altas por error.
- **Decisión: lo desactivado no se puede elegir.** El servidor rechaza (400) crear o mover una asignación hacia una sede o servicio desactivados, y agregar una sede a un servicio desactivado; los selectores del panel solo ofrecen lo activo. Una asignación que ya usa algo desactivado (por ejemplo una finalizada) sigue pudiendo editarse en turno y fechas.
- **Decisión: editar un servicio cambia nombre, cliente y fechas**; la fecha de fin no puede ser anterior a la de inicio. Se activa o desactiva con endpoints aparte.
- **Decisión: todo cambio queda en la bitácora** (`ubicacion`: estado anterior y nuevo, y quién lo hizo), incluido el alta.

## 3. API

Toda la gestión es de `SUPERVISOR` y `RRHH_ADMIN`; un colaborador recibe 403.

**Sedes (`/api/ubicaciones`)**

| Endpoint | Qué hace | Errores |
|---|---|---|
| `POST` | Registra una sede y deja bitácora | 404 servicio inexistente · 400 servicio desactivado, coordenadas o radio fuera de rango |
| `PUT /{id}` | Corrige nombre, referencia, centro y radio; el servicio no cambia | 404 · 400 |
| `POST /{id}/desactivar` | Baja lógica; devuelve la sede con `activo: false` | 404 · 409 si tiene asignaciones en curso o programadas |
| `POST /{id}/reactivar` | Vuelve a activarla | 404 |
| `DELETE /{id}` | Eliminación definitiva (204) | 404 · 409 si alguna asignación la usó |
| `GET [?proyectoId=]` | Lista (incluye las inactivas, con su estado) | |

**Servicios (`/api/proyectos`)**

| Endpoint | Qué hace | Errores |
|---|---|---|
| `POST` | Registra un servicio y deja bitácora | 400 fin anterior al inicio |
| `PUT /{id}` | Corrige nombre, cliente y fechas | 404 · 400 |
| `POST /{id}/desactivar` | Baja lógica | 404 · 409 si tiene asignaciones en curso o programadas |
| `POST /{id}/reactivar` | Vuelve a activarlo | 404 |
| `DELETE /{id}` | Eliminación definitiva (204) | 404 · 409 si tuvo sedes o asignaciones |
| `GET` | Lista | |

**Asignaciones:** crear, editar o mover hacia una sede o servicio desactivados responde 400.

**Dirección del punto (`/api/geocodificacion`)**

| Endpoint | Qué hace | Errores |
|---|---|---|
| `GET /inversa?latitud=&longitud=` | Dirección aproximada del punto (`display_name` y `address`, con los nombres de campo de Nominatim) | 400 coordenadas fuera de rango · 502 el servicio externo no respondió · 403 colaborador |


## 4. Pantalla

Sección **Sedes y servicios** del panel (SUPERVISOR y RRHH_ADMIN).

- **Tabla de servicios** con cliente, fechas, número de sedes y estado. Menú por fila: *Editar*, *Desactivar* y *Eliminar*, o *Reactivar* si está inactivo. «+ Nuevo servicio» abre un cuadro de diálogo.
- **Tabla de sedes** con el centro de la zona, el radio y el estado. Menú por fila: *Editar zona*, *Desactivar* y *Eliminar*, o *Reactivar* si está inactiva.
- **Nueva sede / Editar zona:** un cuadro con el mapa. Al editar se abre encuadrado sobre la zona guardada, con el servicio bloqueado, y avisa si no hay cambios.
- **Desactivar y Eliminar** piden confirmación y la explican (desactivar conserva lo registrado y se puede deshacer; eliminar es definitivo y solo procede si nunca se usó). Si el servidor rechaza, muestra su mensaje dentro del mismo cuadro.

## 5. Pruebas

| Capa | Pruebas | Qué cubren |
|---|---|---|
| `UbicacionControllerTest` (base real) | 7 | La zona marcada se guarda y se lee igual (y `ST_Distance` al centro da menos de 1 m), editar mueve centro y radio y deja bitácora, coordenadas y radio fuera de rango y sede inexistente, desactivar con y sin asignaciones y reactivar, eliminar con y sin historial (una asignación quitada cuenta), servicio desactivado, permisos |
| `ProyectoControllerTest` (base real) | 5 | Alta y edición con bitácora y validación de fechas, desactivar con y sin asignaciones en curso, eliminar con y sin sedes, rechazo de asignar a una sede o servicio desactivados, permisos |
| Panel (`sedes/reglas.test.ts`) | 14 | Lectura de coordenadas pegadas, formato y redondeo, radio, validación del formulario, detección de cambios, sedes vecinas y encuadre del mapa |
| `GeocodificacionServiceTest` | 6 | Con un servidor simulado (sin internet): pide español y el punto exacto, un punto a ~1 m sale de la memoria, uno distinto sí se consulta, un punto sin dirección no falla, un fallo del servicio externo no se recuerda, coordenadas fuera de rango |
| `GeocodificacionControllerTest` | 3 | Nombres de campo de la respuesta, 502 con mensaje sin detalles internos, permisos |
| Panel (`utilidades/direccion.test.ts`) | 5 | Formato de la dirección (calle y número, zona, distrito, sin repetir) y clave de caché |
| Panel (`servicios/reglas.test.ts`) | 8 | Validación del formulario de servicios, fechas, detección de cambios y el filtro de lo activo para los selectores |
| Navegador (CP-M08) | recorrido manual | Ver `resultados-de-pruebas.md`, 9.10 |

## 6. Despliegue y límites

- **Hay que reaplicar la configuración del proxy.** El cambio de CSP está en `infra/nginx/plantillas/cabeceras-seguridad.inc.template`; el contenedor de Nginx se debe recrear para tomarlo (ver `despliegue-azure.md`). Sin eso el mapa se verá gris en producción. **No hay migración de base de datos.**
- **Dependencia de un servicio externo.** Las teselas las sirve OpenStreetMap; su política de uso es para tráfico moderado (un panel interno de RRHH lo es) y exige mostrar la atribución, que el mapa incluye. Si cae o se bloquea, el mapa se ve en blanco pero **las coordenadas se pueden seguir escribiendo o pegando**, así que no se pierde la función. Con mucho tráfico habría que alojar teselas propias o contratar un proveedor.
- **La IP y el navegador de quien abre el mapa llegan a OpenStreetMap** al pedir cada tesela (y el `Referer`, que la cabecera `Referrer-Policy` limita al origen del panel). Es el costo de no alojar el mapa.
- **Las coordenadas que se consultan salen del servidor hacia OpenStreetMap** para obtener la dirección: son las de una sede o de una marcación. La VM necesita salida a internet por HTTPS (hoy la tiene). Si el servicio cae, solo falta la línea de la dirección.
- **La dirección es aproximada.** Depende de lo que OpenStreetMap tenga mapeado: en el centro de Lima un punto puede devolver solo «Lima», y en zonas nuevas puede no haber calle. Por eso no reemplaza a las coordenadas ni se guarda sola.
- **El límite de consultas es del servicio público** (una por segundo, tráfico moderado). Un panel interno cabe, pero si se usara de forma masiva (por ejemplo, la dirección de cada fila de una lista larga) habría que alojar un Nominatim propio o contratar un proveedor; por eso solo se pide en el detalle y en el formulario, nunca por fila.
- **Solo el panel de administración.** La app de campo no muestra mapa, y por eso no se tocó su CSP, aunque el proxy es compartido y la CSP sí queda abierta también allí.
- **No hay búsqueda de direcciones.** Para ubicar un lugar hay que acercarse con el mapa o pegar coordenadas; una búsqueda necesitaría abrir también `connect-src` a un geocodificador.
- **Marcar bien el centro sigue siendo responsabilidad de quien lo coloca.** El mapa lo hace visible, pero no impide ubicar una sede donde no corresponde.
- **El radio máximo es 5000 m** (límite del servidor); el deslizador llega a 1000 m por comodidad.
- **No se probó en la VM de Azure.**
