# Módulo de asignaciones (varias sedes, edición y traslado)

> HU08. Cambio del 9-oct-2026: antes un técnico solo podía tener **una** sede a la vez y las asignaciones no se podían editar ni quitar. Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. Análisis: qué impedía lo pedido

El pedido fue: editar a los técnicos, agregarles más de una zona, y quitarlos de una para ponerlos en otra. El código tenía cuatro obstáculos:

| # | Obstáculo | Dónde |
|---|---|---|
| 1 | Solo existían *crear* y *listar*: no se podía editar ni quitar | `AsignacionController` |
| 2 | Una sola asignación en curso por técnico: el servicio respondía 409 y la base lo impedía con la restricción `ex_asignacion_sin_solapamiento` (por técnico, sin importar la sede) | `AsignacionService`, V2 |
| 3 | La marcación asumía **una** asignación vigente (`buscarVigente` devolvía una sola); con dos habría fallado | `MarcacionService` |
| 4 | Una asignación ya terminada se mostraba como «Vigente» y el tablero la contaba como vigente (solo se notaba porque nunca se podían terminar) | `AsignacionResponse` |

## 2. Decisiones

- **Decisión: lo que no puede repetirse es la misma sede con fechas que se cruzan.** Dos sedes distintas a la vez son válidas; la misma sede dos veces sería ambigua (¿qué turno y qué fechas mandan?). El servicio y la base aplican la misma regla (V4 reemplaza la restricción por `ex_asignacion_misma_sede_sin_solapamiento`: técnico + sede + rango de fechas, solo entre las activas). Relajar la restricción no puede fallar por datos existentes.
- **Decisión: la marcación se valida contra la sede vigente más cercana al punto donde marcó.** Es la que mejor explica dónde está la persona; si ninguna alcanza, la distancia que queda registrada es la de la sede más próxima y no la de una cualquiera. Un empate lo resuelve el orden de la consulta: la que empezó antes y luego la de menor id.
- **Decisión: la tardanza se mide contra el turno de la sede elegida.** Una entrada junto a la sede del turno de las 9:00 se compara con las 9:00, aunque la otra sede del técnico tenga turno a las 14:00. Dos entradas tardías el mismo día en dos sedes generan dos incidencias (la regla de duplicados solo aplica al registro manual).
- **Decisión: quitar es una baja lógica** (`activo = false`). Las marcaciones ya hechas siguen apuntando a su asignación, que deja de contar para validar, desaparece de la agenda y de la lista, y ya no bloquea volver a asignar la misma sede.
- **Decisión: mover es una sola operación.** Termina la asignación el día anterior a la fecha de cambio y crea la nueva en la otra sede, que hereda la fecha de fin, en **una transacción**: si falla el segundo paso, el técnico no queda sin asignación. Se pueden cambiar a la vez el servicio y el turno.
- **Decisión: la sede debe pertenecer al servicio elegido** (400 si no). La pantalla ya lo imponía; ahora lo exige también el servidor, que lo aceptaba.
- **Decisión: nuevo estado `FINALIZADA`** además de `VIGENTE` y `PROGRAMADA` (se calcula contra hoy en Lima; una asignación que termina hoy sigue vigente hoy).
- **Decisión: todo cambio queda en la bitácora** (`asignacion`, con el estado anterior y el nuevo y quién lo hizo). Antes crear una asignación no dejaba rastro.

## 3. API

Toda la gestión es de `SUPERVISOR` y `RRHH_ADMIN`; un colaborador recibe 403 y solo consulta su agenda.

| Endpoint | Qué hace | Errores |
|---|---|---|
| `POST /api/asignaciones` | Crea (como antes) | 409 misma sede cruzada · 400 sede de otro servicio o fechas |
| `PUT /api/asignaciones/{id}` | Edita servicio, sede, turno y fechas; el técnico no cambia | 404 inexistente o quitada · 409 · 400 |
| `DELETE /api/asignaciones/{id}` | Baja lógica, 204 | 404 inexistente o ya quitada |
| `POST /api/asignaciones/{id}/mover` | `{ubicacionId, fechaCambio, proyectoId?, horarioId?}`; devuelve la nueva (201) | 400 fecha de cambio no posterior al inicio, asignación que ya terminó antes de esa fecha, o misma sede · 409 el técnico ya tiene esa sede en esas fechas |
| `GET /api/asignaciones[?usuarioId=]` | Lista las activas (las quitadas no salen); trae el estado | |
| `GET /api/asignaciones/mias` | Agenda propia: vigentes y programadas, con todas las sedes | |

## 4. Pantalla

Sección **Asignaciones** del panel (SUPERVISOR y RRHH_ADMIN).

- **Lista** con filtro por técnico y por estado (por defecto *Vigentes y programadas*; lo finalizado se pide expresamente). Bajo el nombre aparece «N sedes vigentes» cuando un técnico tiene más de una.
- **Menú por fila:** *Editar*, *Mover a otra sede* (no en las finalizadas) y *Quitar*.
- **Editar:** mismo formulario que el alta, con el técnico fijo; avisa si no hay cambios.
- **Mover:** no ofrece la sede actual, limita la fecha de cambio al rango posible y **explica lo que hará** antes de confirmar («Sede A termina el … y Sede B empieza el …»).
- **App de campo:** la tarjeta de la agenda lista todas las sedes asignadas hoy cuando son varias.

## 5. Pruebas

| Capa | Pruebas | Qué cubren |
|---|---|---|
| `AsignacionServiceTest` | 20 | Misma sede cruzada, otra sede permitida, sede de otro servicio, editar (incluso contra sí misma), quitar, mover (fechas, misma sede, conflicto sin tocar la actual), agenda con varias sedes y los tres estados |
| `AsignacionControllerTest` (base real) | 8 | Varias sedes a la vez, edición y bitácora, mover y su estado `FINALIZADA`, quitar y volver a asignar, permisos y agenda propia, y una marcación que se valida contra la sede más cercana |
| `MarcacionServiceTest` | 4 nuevas | Más cercana de varias, sin ninguna en tolerancia, empate y tardanza contra el turno de la sede elegida |
| `RestriccionesBaseDatosTest` | 2 nuevas | Dos sedes a la vez permitidas, la misma sede cruzada rechazada, y una quitada no bloquea |
| Panel (`asignaciones/reglas.test.ts`) | 13 | Estados, filtros, sedes vigentes por técnico, rango y descripción del traslado, y detección de cambios |

Backend: **283 pruebas, 0 fallos** (254 antes). Panel: **68** (55 antes). Además se recorrió la pantalla en el navegador contra el backend local: dos sedes a la vez, duplicado rechazado con su mensaje, edición, traslado y baja.

## 6. Despliegue y límites

- **Migración V4.** Solo cambia una restricción y es segura sobre los datos existentes. **No es reversible sin cuidado:** una vez que haya técnicos con varias sedes a la vez, volver a la restricción anterior fallaría. Conviene un respaldo de la base antes de aplicarla.
- **Validar contra la más cercana tiene un límite.** Si dos sedes de un técnico tienen turnos que se cruzan y marca junto a una, el sistema no detecta que debía estar en la otra: valida lo que ve. Cubrirlo exigiría que el técnico indique en qué sede marca.
- **El técnico no elige la sede al marcar.** Es la decisión que evita cambiar la app de campo y la API de marcación; si el cliente necesita lo contrario, es un cambio mayor.
- **Las asignaciones quitadas no se ven.** No hay pantalla de histórico de bajas; la bitácora conserva quién y cuándo.
- **La lista no se pagina.** Con muchos técnicos y varias sedes cada uno crecerá; es el mismo límite de siempre de esta pantalla.
- **No se probó en la VM de Azure.**
