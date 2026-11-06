# Módulo de asistencia (supervisión de marcaciones)

> Cierra el hallazgo H-09 de [resultados-de-pruebas.md](resultados-de-pruebas.md): hasta el 9-oct-2026 el backend solo permitía **registrar** una marcación (`POST /api/marcaciones`) y el panel no tenía dónde verlas. Este módulo agrega la **consulta**. Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. Qué permite

Quien supervisa (SUPERVISOR y RRHH_ADMIN) ve las marcaciones de todo el personal con lo que dictaminó el motor de validación contextual (OE3), y puede filtrarlas por día, persona, sede, tipo de evento y estado de validación.

| Dato | Para qué sirve |
|---|---|
| Persona, evento (entrada, refrigerio, salida), hora del evento | Quién marcó y cuándo ocurrió |
| Sede y proyecto (de la asignación vigente al momento del evento) | Dónde debía estar |
| Estado de validación | `VALIDO`, `OBSERVADO`, `FUERA_DE_TOLERANCIA`, `SOSPECHOSO`, `SIN_ASIGNACION` |
| Distancia a la sede, radio de tolerancia y precisión del GPS | Entender por qué quedó en ese estado |
| Retraso de sincronización | Detectar marcaciones hechas sin conexión y enviadas después (OE4) |
| Coordenadas y dispositivo | Revisar una marcación dudosa |

## 2. API

Ambos endpoints exigen rol `SUPERVISOR` o `RRHH_ADMIN`; un colaborador recibe 403 y sin sesión 401. `POST /api/marcaciones` no cambió.

| Endpoint | Qué devuelve |
|---|---|
| `GET /api/marcaciones` | Página de marcaciones, la más reciente primero (`contenido`, `pagina`, `tamano`, `total`, `totalPaginas`) |
| `GET /api/marcaciones/resumen` | `total` y `porEstado`: el reparto por estado con los mismos filtros, **sin** el de estado |

Parámetros opcionales (todos combinables): `usuarioId`, `proyectoId`, `ubicacionId`, `desde`, `hasta` (AAAA-MM-DD, ambos inclusivos), `estado`, `tipoEvento`; la lista además `pagina` (desde 0) y `tamano` (20 por defecto, máximo 100). Un filtro mal formado, una página negativa, un tamaño fuera de rango o `desde` posterior a `hasta` responden 400.

**Decisión: los días son de Lima.** `desde=2026-10-09` abarca de las 00:00 a las 24:00 de Lima, no de UTC. Una marcación a las 23:59 de Lima del 14 (04:59 UTC del 15) cuenta como del 14. Está probado en los bordes.

**Decisión: filtros con `Specification`.** Un parámetro nulo dentro de una consulta JPQL fija no tiene tipo en Postgres (ya ocurrió en la búsqueda de usuarios); con especificaciones solo se agrega lo que viene informado. Las asociaciones (persona, asignación, sede, proyecto, dispositivo) viajan en la misma consulta, sin una consulta por fila.

## 3. Estados de validación

Las explicaciones salen de `MotorValidacionContextualService` y se muestran en la pantalla al pasar el cursor sobre el estado y en el detalle.

| Estado | Significa |
|---|---|
| `VALIDO` | Aun contando el error del GPS en su contra, la persona está dentro del radio de la sede |
| `OBSERVADO` | Según el margen de error del GPS podría estar dentro o fuera: se acepta y queda para revisión, nunca se rechaza solo por esto |
| `FUERA_DE_TOLERANCIA` | Aun contando el error del GPS a su favor, está fuera del radio |
| `SOSPECHOSO` | El GPS reportó una precisión peor que 500 m o ninguna: la lectura no es confiable |
| `SIN_ASIGNACION` | No tenía una asignación vigente para validar; aparece sin sede ni proyecto |

El umbral de 500 m es hoy un parámetro de negocio **pendiente de calibrar** con datos reales; si cambia, hay que actualizar el texto de `frontend-admin/src/asistencia/reglas.ts`.

### 3.1 Puntualidad de las entradas (5-nov-2026)

Además del estado de validación (dónde), cada **entrada con turno asignado** se clasifica por cuándo llegó respecto del inicio del turno. Es una columna aparte: una marcación puede ser `VALIDO` en lugar y llegar `TARDE`.

| Puntualidad | Cuándo | En la lista |
|---|---|---|
| `TEMPRANO` | Antes de la hora de inicio | «Temprano · 15 min antes» |
| `DENTRO_DE_TOLERANCIA` | Desde la hora de inicio hasta el fin de la tolerancia del turno, inclusive | «En tolerancia · A la hora» o «+7 min» |
| `TARDE` | Pasada la tolerancia | «Tarde · 11 min tarde» |

- **Decisión: es la misma regla con la que se genera la incidencia de tardanza** (inicio más tolerancia del turno), así la columna y las incidencias no se contradicen. Los minutos se cuentan desde la hora de inicio, no desde el fin de la tolerancia.
- **Decisión: solo las entradas.** Salidas, refrigerios y marcaciones `SIN_ASIGNACION` (no hay turno contra el cual comparar) muestran un guion; el detalle dice cuál es el motivo.
- **Decisión: la hora se compara en Lima**, que es donde trabaja el personal.
- **Límite: turnos que cruzan la medianoche.** La puntualidad toma la diferencia por el camino más corto (una entrada a las 00:10 de un turno de 22:00 es tardía por 2 h 10 min). El motor de validación contextual no hace ese ajuste, así que en esos turnos la tardanza mostrada puede no tener incidencia automática. No hay turnos así cargados hoy.
- **Límite: no hay filtro por puntualidad.** Se ve en la columna y en el detalle; filtrar exigiría comparar la hora local con el turno dentro de la consulta. **Recomendación:** agregarlo si supervisión lo pide.
- **Datos:** `GET /api/marcaciones` suma `puntualidad`, `minutosRespectoAlInicio` (negativo si llegó antes), `turno`, `horaInicioTurno` y `toleranciaTurnoMinutos`.

### 3.2 Dirección del punto marcado

El detalle de la marcación muestra, arriba, la **dirección aproximada** del punto («Avenida El Olivar 1259, Jorge Chavez, Callao»). Se obtiene del servidor (`GET /api/geocodificacion/inversa`), que consulta a OpenStreetMap; ver [modulo-zonificacion-sedes.md](modulo-zonificacion-sedes.md), sección 2. No se guarda: se calcula al abrir el detalle y es solo una ayuda para leer las coordenadas.

## 4. Pantalla

Sección **Asistencia** del panel, visible para SUPERVISOR y RRHH_ADMIN.

- **Fechas:** arranca en el día de hoy (Lima), con atajos *Hoy*, *Ayer* y *Últimos 7 días*. Un rango invertido se avisa en pantalla sin pedirlo al servidor.
- **Resumen:** una etiqueta por estado con su cantidad; al pulsarla filtra la lista. Muestra cuántas marcaciones están *por revisar* (observadas, fuera de tolerancia y sospechosas).
- **Lista:** persona, evento, hora, **puntualidad**, sede y proyecto, estado, distancia y sincronización, con paginación y botón *Ver detalle*. Al pasar el cursor sobre la puntualidad sale la explicación con el turno y su tolerancia.
- **Detalle:** la dirección del punto marcado, todos los datos de la marcación (incluidos el turno y la puntualidad con su explicación), la explicación del estado y un enlace a OpenStreetMap con las coordenadas.
- **Persona:** lista a todos los usuarios y marca el rol de quien no es colaborador, porque cualquier usuario autenticado puede marcar su propia asistencia.

**Decisión de presentación:** la sincronización se muestra como *Inmediata* si tardó menos de un minuto y destacada en ámbar si tardó más. Ese minuto es una decisión de la pantalla, no una regla del dominio.

## 5. Pruebas

| Capa | Pruebas | Qué cubren |
|---|---|---|
| Backend (`AsistenciaControllerTest`, base real con PostGIS) | 9 | Datos y orden de la lista, bordes de día en hora de Lima, combinación de filtros, marcación `SIN_ASIGNACION`, retraso de sincronización, paginación y tamaños inválidos, filtros mal formados (400), resumen que ignora el estado, permisos por rol |
| Panel (`asistencia/reglas.test.ts`) | 15 | Aritmética de fechas, atajos de rango, validación del rango, descripción del retraso, etiquetas y colores de estado, total por revisar, enlace al mapa |

La suite del backend quedó en **254 pruebas** (245 antes) y la del panel en **55** (40 antes). Además se recorrió la pantalla en el navegador contra el backend local real: resumen, chips de estado, atajos de fecha, detalle con su enlace al mapa, rango inválido, y filtros de persona, sede y tipo. Los datos de prueba se borraron de la base local.

## 6. Límites y decisiones abiertas

- **Sin alcance por supervisor.** Igual que las incidencias, todo supervisor ve la asistencia de todo el personal. **Recomendación:** si el cliente exige que cada supervisor vea solo a su gente, hace falta modelar quién supervisa a quién; hoy no existe esa relación.
- **Privacidad (Supuesto).** La respuesta incluye latitud y longitud de cada marcación: es ubicación de personas. Acotarla a RRHH es un cambio de una línea en el controlador.
- **Sin exportación.** No hay descarga a CSV ni a Excel; el límite de 100 filas por página lo hace impracticable para un reporte mensual. **Recomendación:** un endpoint de exportación con rango obligatorio.
- **Sin vista para el colaborador.** Quien marca no puede consultar sus propias marcaciones desde la app de campo; sería un endpoint `mias`, como el de incidencias.
- **Sin mapa integrado.** El enlace abre un sitio externo; no se agregó un mapa propio porque la política de seguridad del proxy bloqueaba recursos externos. Desde el 4-nov-2026 esa política admite las teselas de OpenStreetMap para el mapa de sedes (ver [modulo-zonificacion-sedes.md](modulo-zonificacion-sedes.md)), de modo que mostrar un mapa aquí ya es posible.
- **Probado en la VM de Azure solo por HTTP** (9-oct-2026): `verificar-despliegue.sh` 73/73 y los endpoints del módulo; el recorrido manual de la pantalla contra producción sigue pendiente (CP-X05). Ver [resultados-de-pruebas.md](resultados-de-pruebas.md), 9.8.
