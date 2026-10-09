# Resultados de las pruebas

Ejecución local del [plan de validación](plan-de-pruebas.md) el 3-oct-2026 (CLICKCLACK-64). Corresponde al apartado 7.1 del informe.

Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

> **Actualización del 8-oct-2026.** El módulo de usuarios (HU04) se amplió después de este corte: la sección 9 recoge sus resultados (backend 244 pruebas, panel 40 pruebas, 21 comprobaciones del script de usuarios en local y un recorrido manual por el panel). Las cifras de las secciones 1 a 8 son las del 3-oct y no se modificaron.

> **Qué se probó y qué no.** El primer corte se ejecutó en local sobre un simulacro de la fusión de los 13 PR abiertos (commit `6a1fb44` de un worktree local, no subido). Después de fusionar, la suite completa del backend se repitió sobre `main` (`8ae83d1`) con JDK 21 y el despliegue de Azure se verificó con 52/52 comprobaciones correctas. WebAuthn real y la cola sin conexión del service worker **no se probaron** y siguen en la sección 5. No se hicieron pruebas de carga (decisión del 3-oct).

## 1. Resumen

| Prueba | Resultado |
|---|---|
| Fusión simulada de los 13 PR en el orden indicado | **Sin conflictos** |
| Backend: 28 clases, 178 pruebas | **0 fallos, 0 errores, 0 omitidas** |
| Prueba de extremo a extremo por HTTP: 16 casos | **16 correctos** |
| `frontend-admin`: instalación limpia, build y 17 pruebas Vitest | **Correcto** |
| `frontend-campo`: instalación limpia y build (genera el service worker) | **Correcto**; **sin pruebas automatizadas** |
| Panel administrativo: recorrido de las 6 secciones | **Sin errores en la consola** |
| Panel administrativo: flujo de incidencias y separación de funciones | **Correcto** (ejecutado antes, ver 4) |

## 2. Pruebas automatizadas del backend

178 pruebas en 28 clases, con el contexto completo de Spring y Postgres 16.4 con PostGIS 3.4 reales (base de desarrollo). **Resultado probado nuevamente el 3-oct-2026 sobre `main`:** JDK 21.0.8, 178 pruebas, 0 fallos, 0 errores, 0 omitidas y `BUILD SUCCESS`. El detalle por clase está en [evidencia/backend-pruebas-integrado.txt](evidencia/backend-pruebas-integrado.txt).

| Caso | Requerimiento | Pruebas | Resultado |
|---|---|---|---|
| CP-A01 Autenticación, sesión, recuperación y bloqueo | OE2, Seguridad | 38 | Correcto |
| CP-A02 WebAuthn: protocolo y desafíos | OE2 | 21 | Correcto |
| CP-A03 Dispositivos | OE2 | 2 | Correcto |
| CP-A04 Marcación e idempotencia | OE3, OE4 | 9 | Correcto |
| CP-A05 Motor de validación y distancia (PostGIS) | OE3 | 9 | Correcto |
| CP-A06 Incidencias | OE5 | 34 | Correcto |
| CP-A07 Administración de datos maestros | OE6 | 27 | Correcto |
| CP-A08 Restricciones de la base | Seguridad | 8 | Correcto |
| CP-A09 JWT, contraseñas, tokens y validación de entradas | Seguridad | 29 | Correcto |
| CP-A10 Arranque del contexto | Todos | 1 | Correcto |
| **Total** | | **178** | **Correcto** |

El PR #4 reportaba 148 pruebas; el PR #7 (seguridad del backend) agrega 30 y el total integrado es 178.

## 3. Prueba de extremo a extremo por HTTP

Script [prueba_e2e_marcacion.py](evidencia/prueba_e2e_marcacion.py), ejecutado contra el backend integrado levantado en el puerto 8081. Salida completa en [evidencia/e2e-marcacion-salida.md](evidencia/e2e-marcacion-salida.md).

| Caso | Requerimiento | Resultado |
|---|---|---|
| E1 Ingreso puntual dentro del radio: `VALIDO`, sin incidencia | OE3 | Correcto |
| E2 Reenvío del mismo `uuid` con datos alterados: devuelve la original, sin duplicar ni sobrescribir; una sola fila en la base | OE4 | Correcto |
| E3 Salida a 1 km: `FUERA_DE_TOLERANCIA` | OE3 | Correcto |
| E4 Precisión de 800 m: `SOSPECHOSO` | OE3 | Correcto |
| E5 A 160 m con precisión de 50 m y radio de 150 m: `OBSERVADO` | OE3 | Correcto |
| E6 Ingreso con retraso: incidencia de tardanza automática en `REGISTRADA`, con una fila de historial y una de auditoría con el sistema como autor | OE3, OE5 | Correcto |
| E7 Hora del evento en el futuro: 400 | Seguridad | Correcto |
| E8 Sin asignación vigente: `SIN_ASIGNACION` | OE3 | Correcto |
| E9 Dispositivo de otro colaborador: 403 | OE2 | Correcto |
| E10 Marcar a nombre de otro usuario: 403 | Seguridad | Correcto |
| E11 Sin sesión: 401 | Seguridad | Correcto |

**Limitación de la prueba.** Los usuarios de prueba tienen rol **supervisor**, no colaborador: el sistema **no permite que un colaborador tenga contraseña** (su acceso es solo por WebAuthn) y el script no puede ejercitar WebAuthn. La marcación no depende del rol del usuario autenticado, pero el caso no reproduce al colaborador real. Los dispositivos se insertaron directamente en la base como datos de prueba.

## 4. Pruebas en el navegador (panel administrativo)

| Caso | Resultado | Cuándo y dónde |
|---|---|---|
| CP-M01 Tomar para revisión, aprobar, rechazar con motivo, cerrar | Correcto: el estado, el historial y los contadores se actualizan; rechazar sin motivo se bloquea | Ejecutado el 3-oct contra el backend de la rama de incidencias (sin el PR #7) |
| CP-M02 Alta de incidencia | Correcto: duplicado muestra el 409; la fecha futura se bloquea en ausencia y se acepta en permiso | Igual |
| CP-M03 Separación de funciones | Correcto: con el supervisor y con el administrador el botón se deshabilita con el motivo, y la API responde 403 | Igual |
| CP-M04 Recorrido de las 6 secciones con la sesión de administrador | Correcto: cada una carga con sus datos y la consola no muestra errores; los totales del tablero coinciden con la API | Ejecutado después, contra el mismo backend |

Los casos CP-M01 a CP-M03 se ejecutaron antes de este plan y **no se repitieron sobre el estado integrado**. Los resultados quedan registrados como texto; **no se guardaron capturas de pantalla** en el repositorio.

### 4.1 Verificación del despliegue en Azure

| Caso | Resultado | Cuándo y dónde |
|---|---|---|
| CP-X03 `verificar-despliegue.sh` | **Correcto: 52/52 comprobaciones** (40 de despliegue y seguridad web; 12 de replicación) | 3-oct-2026, VM `vm-clickclak`, commit `8ae83d1` de `main` |

La salida completa se conserva en [`evidencia/despliegue-main-2026-10-03.txt`](evidencia/despliegue-main-2026-10-03.txt). **No prueba** WebAuthn real ni la cola sin conexión del service worker; ambos casos siguen pendientes en Android.

## 5. Casos no ejecutados

| Caso | Requerimiento | Motivo | Qué hace falta |
|---|---|---|---|
| CP-X01 Inicio de sesión con WebAuthn y biometría real | OE2 | Exige un autenticador | Probar en un dispositivo Android |
| CP-X02 Cola sin conexión y sincronización diferida en el cliente | OE4 | Exige service worker, geolocalización y WebAuthn; el navegador integrado no registra service workers | Probar en Chrome Android |
| Pruebas de carga y rendimiento | No funcional | Fuera del alcance decidido el 3-oct. El criterio de aceptación de CLICKCLACK-64 las menciona | Mover a CLICKCLACK-10 y 32, o ejecutarlas antes del APF2 |

## 6. Defectos y hallazgos

**Defecto corregido durante la prueba**

| ID | Severidad | Descripción | Estado |
|---|---|---|---|
| D-01 | Baja | En el formulario de alta de incidencias, el error del servidor (por ejemplo el 409) seguía visible tras editar un campo | Corregido (commit `0cbaced`, PR #10) |

**Hallazgos abiertos (no son fallos de las pruebas)**

| ID | Hallazgo | Detalle |
|---|---|---|
| H-01 | `frontend-campo` sin pruebas automatizadas | `vitest` termina con código 1 por no encontrar archivos de prueba, así que no se puede poner en un pipeline sin configurarlo. La cola sin conexión no tiene cobertura automatizada. |
| H-02 | El panel administrativo solo tiene pruebas de las reglas de incidencias | No hay pruebas de componentes ni de los formularios de administración. |
| H-03 | `CONTRIBUTING.md` no refleja el estado real | Dice que `main` está protegida, que los workflows corren en cada PR y que se prefiere el squash. No hay workflows (CLICKCLACK-9 sigue pendiente), `main` no está protegida y la fusión acordada es con merge commit. |
| H-04 | Biometría por sesión y sin controles contra GPS falso | Ya declarado en [bpm.md](bpm.md), sección 7. |
| H-05 | Datos reales de un integrante en la base de desarrollo | La base local contiene el nombre y el correo corporativo de un integrante del equipo. No están en el repositorio, pero **no deben aparecer en capturas** que se enlacen en el informe. |

## 7. Brechas de cobertura por requerimiento

| Requerimiento | Cobertura automatizada | Cobertura manual | Brecha |
|---|---|---|---|
| OE2 | Sí (protocolo WebAuthn, dispositivos) | No | Falta la prueba con biometría real en un dispositivo |
| OE3 | Sí | E2E | Ninguna relevante |
| OE4 | Servidor: sí. Cliente: no | No | La cola del cliente no se ha probado |
| OE5 | Sí | Sí | Falta el sustento documental (CLICKCLACK-56) |
| OE6 | Servidor: sí | Recorrido de lectura | No se probaron las altas desde la interfaz del panel |
| OE7 | No aplica a este plan | No | Pendiente tras el despliegue |

## 8. Cómo repetirlo

```bash
# Backend (desde backend/, con el Postgres de Docker en el puerto 5434)
./mvnw -B test

# Frontends (desde cada carpeta)
npm ci && npm run build && npx vitest run

# Extremo a extremo (con el backend levantado; pasar la URL base si no es el 8080)
python Docs/tecnica/evidencia/prueba_e2e_marcacion.py http://localhost:8080
```

Conviene repetir todo **tras fusionar los PR en `main`** y, una vez desplegado, ejecutar `verificar-despliegue.sh` en la VM (CP-X03).


## 9. Actualización: módulo de usuarios (8-oct-2026)

Ejecución local del módulo de usuarios ampliado (HU04), en cinco pull requests apilados (#22 a #25 y el de documentación). Las pruebas del backend y del panel corrieron sobre la rama de cada fase; el recorrido manual y el script de verificación, contra el backend en perfil `dev` y la base local. **No se probó en la VM de Azure.**

### 9.1 Resumen

| Prueba | Antes (3-oct) | Ahora (8-oct) | Resultado |
|---|---|---|---|
| Backend: clases y pruebas | 28 clases, 178 pruebas | **33 clases, 244 pruebas** | 0 fallos, 0 errores, 0 omitidas |
| `frontend-admin`: pruebas Vitest | 17 | **40** | Correcto; `tsc` y build de producción correctos |
| `verificar-usuarios.sh` (local) | no existía | **21 comprobaciones** | 21 correctas |
| Recorrido manual del panel (CP-M05) | no existía | 11 casos | Correcto (ver 9.4) |

### 9.2 Pruebas nuevas del backend (66)

| Clase | Pruebas | Qué cubre |
|---|---|---|
| `UsuarioServiceTest` | 39 (29 nuevas) | Correo normalizado, cambio de rol con y sin contraseña, protección del último administrador y de la propia cuenta, baja con motivo, desbloqueo, eliminación con y sin historial, restablecimiento de clave, estado de cuenta |
| `UsuarioGestionControllerTest` | 12 | Búsqueda paginada (texto, rol, estado, comodines como literales), permisos por rol, baja y reactivación, desbloqueo, eliminación (204 y 409) e historial, contra la base real |
| `SesionUsuarioControllerTest` | 5 | Usuario desactivado o eliminado con token vigente: 401; cambio de rol con efecto inmediato; clave pendiente: 403 salvo perfil y cierre de sesión |
| `ClavesUsuarioControllerTest` | 6 | Flujo completo con BCrypt real: restablecer, entrar con la temporal, quedar limitado, cambiarla, que la temporal deje de servir, y que ninguna clave aparezca en la bitácora |
| `CambioClaveServiceTest` | 7 | Clave actual incorrecta (cuenta como fallo), clave débil, igual a la actual, cuenta bloqueada, colaborador, usuario inactivo |
| `GeneradorClaveTemporalTest` | 3 | Cumple la política, longitud y caracteres sin ambigüedad, unicidad |
| `JwtServiceTest` | 4 (1 nueva) | Dos tokens del mismo usuario en el mismo instante son distintos |
| `RecuperacionClaveServiceTest` | 8 (1 nueva) | La recuperación por enlace limpia la marca de clave pendiente |
| `RestriccionesBaseDatosTest` | 10 (2 nuevas) | Correos que solo difieren en mayúsculas y valores por defecto de V3 |

La migración **V3** se probó además a mano en una base aparte: con correos duplicados salvo por mayúsculas **se detiene con un mensaje claro y revierte todo** (dentro de una transacción, como la ejecuta Flyway); sin duplicados normaliza el correo y crea el índice.

### 9.3 `verificar-usuarios.sh`

21 comprobaciones contra el backend local (`URL_BASE=http://localhost:8080`): alta de un supervisor temporal y búsqueda, sesión con su clave, restablecimiento (la temporal llega con `Cache-Control: no-store` y 12 caracteres), estado `CLAVE_PENDIENTE`, sesión previa limitada con 403 y código `CLAVE_PENDIENTE`, perfil aún disponible, clave anterior rechazada, ingreso con la temporal avisando el cambio, desactivación con motivo, token vigente rechazado de inmediato (401), el administrador no puede eliminarse a sí mismo (409), eliminación del usuario temporal (204) y 404 posterior. El usuario temporal no queda en la base. Ahora lo invoca `verificar-despliegue.sh`.

### 9.4 Recorrido manual (CP-M05)

Backend en perfil `dev` y panel en el navegador integrado, con el administrador de la semilla de desarrollo.

| Caso | Resultado |
|---|---|
| Alta con DNI de 3 dígitos | Se bloquea con "El DNI debe tener 8 dígitos" |
| Alta de un supervisor con DNI válido y correo con mayúsculas | Se crea; el correo queda en minúsculas |
| Menú de acciones de un supervisor activo | Editar, Restablecer contraseña, Ver historial, Desactivar, Eliminar |
| Restablecer contraseña | Muestra una clave de 12 caracteres una sola vez; la fila pasa a "Clave pendiente" |
| Ingreso con la temporal; intentar abrir `/usuarios` | Redirige a la pantalla de cambio de contraseña, la única disponible |
| Confirmación distinta y luego correcta | Se rechaza con el mensaje; la correcta vuelve al login con el aviso y la sesión borrada |
| Ingreso con la clave nueva; Supervisor ante Usuarios | Entra sin marca pendiente; no ve el enlace y `/usuarios` lo lleva al inicio |
| Historial | Alta, restablecimiento y cambio de clave, sin ninguna clave en el texto |
| Desactivar con motivo | Etiqueta "Inactiva" con el motivo como ayuda; el menú ofrece Activar |
| Eliminar un usuario con historial | Error 409 dentro del cuadro: "…desactívelo en su lugar"; el usuario sigue |
| Eliminar un usuario recién creado; búsqueda y filtros; edición con cambio de rol | Se elimina; la búsqueda y los filtros por estado y rol devuelven lo esperado; el cambio a un rol con clave pide contraseña y el contrario avisa que se borrará |

Los datos de prueba se borraron de la base local. **No se guardaron capturas** en el repositorio.

### 9.5 Defectos y hallazgos de esta actualización

| ID | Severidad | Descripción | Estado |
|---|---|---|---|
| D-02 | Media | Dos tokens del mismo usuario emitidos en el mismo segundo eran idénticos, así que revocar uno (cierre de sesión o cambio de clave) dejaba inservible el otro: tras cambiar la clave, el login inmediato devolvía 401. Lo detectó `ClavesUsuarioControllerTest` | Corregido: cada JWT lleva un `jti` único (PR #24) |
| D-03 | Baja | `verificar-usuarios.sh` enviaba un motivo con tilde y el servidor respondía 400 en una consola que no usa UTF-8 | Corregido: el motivo del script es ASCII |
| H-06 | Hallazgo | Con una sesión guardada que ya no sirve (token de otra ejecución o revocado), el tablero lanza sus peticiones y deja errores 401 "Uncaught (in promise)" en la consola en vez de volver al login | Abierto; es del tablero, anterior a este trabajo |
| H-07 | Hallazgo | `npm run lint` del panel no funciona: `eslint` no está instalado | Abierto; anterior a este trabajo |

### 9.6 Brechas

- **No se ejecutó en la VM.** `verificar-usuarios.sh` y la migración V3 sobre la base de producción están pendientes. Antes de aplicar V3 hay que comprobar que no hay correos duplicados salvo por mayúsculas: `SELECT lower(correo), count(*) FROM usuario GROUP BY 1 HAVING count(*) > 1;`.
- **El estado `BLOQUEADA` sale de un almacén en memoria:** se pierde al reiniciar el backend (límite ya declarado).
- La vista en un teléfono del panel de usuarios no se probó: el panel es de escritorio.
- El cambio de contraseña obligatorio no está en `verificar-usuarios.sh` (cambiar la clave dejaría historial y el usuario temporal ya no podría eliminarse); lo cubren `ClavesUsuarioControllerTest` y el recorrido manual.
