# Resultados de las pruebas

Ejecución local del [plan de validación](plan-de-pruebas.md) el 3-oct-2026 (CLICKCLACK-64). Corresponde al apartado 7.1 del informe.

Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

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
