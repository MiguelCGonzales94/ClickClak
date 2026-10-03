# Plan de validación del servicio

Plan de pruebas funcionales y no funcionales de ClickClak para el APF2 (CLICKCLACK-29). Los resultados de su ejecución están en [resultados-de-pruebas.md](resultados-de-pruebas.md) (CLICKCLACK-64). Corresponde al apartado 7.1 del informe.

Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. Objetivo y alcance

Demostrar, con evidencia, que la versión 1 cumple los objetivos específicos del Capítulo 1 que ya tienen código, y declarar con la misma claridad lo que no se probó.

- **Dentro del alcance (Decisión, 3-oct-2026):** pruebas funcionales y de seguridad, ejecutadas **en local primero**. La ejecución en la VM de Azure se hace una sola vez, tras fusionar los PR y desplegar desde `main`.
- **Fuera del alcance de este plan:**
  - **Pruebas de carga y rendimiento.** Se decidió no incluirlas en el APF2. El criterio de aceptación de CLICKCLACK-64 menciona "carga básica": queda como **brecha declarada** y se propone moverla a CLICKCLACK-10 y 32 (análisis de capacidad y rendimiento).
  - **Prueba de penetración independiente**, segundo factor para administradores y análisis de dependencias del backend (ya declarados en [seguridad-web.md](seguridad-web.md)).
  - **Continuidad, replicación y recuperación ante desastres**: tienen su propio documento ([replicacion-postgres.md](replicacion-postgres.md)).

## 2. Base de los requerimientos

**Supuesto.** El repositorio no contiene una especificación de requerimientos (SRS). Se toman como requerimientos los **objetivos específicos OE2 a OE6** del Capítulo 1 y las historias de Jira que los implementan. **OE1** (modelado) se evidencia en [bpm.md](bpm.md) y **OE7** (despliegue, monitoreo, respaldo, SLA) queda fuera de este plan funcional.

| Requerimiento | Resumen | Historias de Jira (aproximadas) |
|---|---|---|
| OE2 | Registro de eventos de jornada con identidad acreditada (biometría delegada al sistema operativo) y dispositivo autorizado | CLICKCLACK-58 y 7 |
| OE3 | Motor de validación contextual con estado graduado | CLICKCLACK-6 (PostGIS) |
| OE4 | Registro sin conexión con sincronización diferida e integridad frente a duplicados y reenvíos | Sin historia propia en Jira |
| OE5 | Gestión de incidencias con flujo de estados, revisión, aprobación y trazabilidad | CLICKCLACK-55 y 56 |
| OE6 | Plataforma web de administración (proyectos, ubicaciones, horarios, asignaciones) | Épica CLICKCLACK-1 |
| Seguridad | Controles C1 a C22 de [seguridad-web.md](seguridad-web.md) | CLICKCLACK-57, 28 y 7 |

**Supuesto:** la correspondencia con Jira es aproximada; las historias no están redactadas por objetivo específico. **Recomendación:** al cerrar la trazabilidad (CLICKCLACK-23), asociar cada historia a su OE.

> **Declaración.** La biometría se verifica **por sesión**, no en cada marcación, y los controles contra ubicaciones falsas del Capítulo 1 no están implementados (ver [bpm.md](bpm.md), sección 7). Este plan prueba lo que el sistema hace, no lo que el Capítulo 1 promete.

## 3. Entorno

| Elemento | Valor |
|---|---|
| Equipo | Windows 10 Pro, ejecución local |
| Java | JDK 23.0.1 ejecutando un proyecto con `java.version` 21 |
| Spring Boot | 3.3.4 |
| Base de datos | PostgreSQL 16.4 con PostGIS 3.4, en Docker (`postgis/postgis:16-3.4`), puerto 5434, base `clickclak` de **desarrollo** |
| Node | 22.21.0 (npm 10.9.4) |
| Navegador | Navegador integrado basado en Chromium |
| Estado probado | Simulacro local de la fusión en `main` de los 13 PR abiertos, sin subirlo (ver sección 5) |

**Límites del entorno.**
- El navegador integrado **no registra service workers** ni abre el sitio de la VM por su certificado autofirmado.
- El inicio de sesión de la app de campo usa WebAuthn y exige un autenticador: **no se puede ejercitar en el navegador integrado**.
- Las pruebas del backend usan la base de desarrollo, no una base aparte. Las que escriben datos limpian lo suyo o se revierten.
- El JDK de las pruebas (23) no es el de la imagen de producción: **no se verificó** con el JDK 21.

## 4. Estrategia

| Nivel | Qué cubre | Herramienta | Tipo |
|---|---|---|---|
| Unitario | Reglas de servicios y utilidades (motor de validación, incidencias, almacenes de tokens) | JUnit 5 y Mockito | Automatizada |
| Integración HTTP | Controladores con seguridad, validación y Postgres real | MockMvc, Spring Boot Test | Automatizada |
| Base de datos | Restricciones de integridad y consulta de distancia (PostGIS) | JUnit con Postgres real | Automatizada |
| Frontend | Reglas puras de la pantalla de incidencias | Vitest | Automatizada |
| Extremo a extremo por HTTP | Marcación completa: validación, idempotencia, tardanza automática, rechazos | Script [prueba_e2e_marcacion.py](evidencia/prueba_e2e_marcacion.py) contra el backend real | Automatizada, ejecutada a mano |
| Interfaz | Flujos del panel administrativo en el navegador | Ejecución manual guiada | Manual |
| Compilación | Los dos frontends y el backend compilan en el estado integrado | `npm run build`, `mvnw test` | Automatizada |

## 5. Estado bajo prueba: simulacro de la fusión

Los 13 PR abiertos se fusionan con merge commit en el orden indicado por el equipo. Antes de fusionarlos de verdad, se simuló en un worktree local (no se sube nada) para saber dos cosas: si hay conflictos y si el resultado compila y pasa las pruebas.

Orden: #1 → #3 → #4 → #7 → #2 → #5 → #6 → #8 → #9 → #10 → #11 → #12 → #13.

## 6. Casos de prueba

Los casos automatizados se identifican por la clase de prueba; el número de pruebas de cada una está en los resultados.

### 6.1 Automatizados del backend

| ID | Requerimiento | Qué verifica | Clases de prueba |
|---|---|---|---|
| CP-A01 | OE2, Seguridad | Autenticación con contraseña, sesión, cierre de sesión, recuperación de clave, bloqueo por fuerza bruta, sin enumeración de cuentas | `AutenticacionServiceTest`, `AutenticacionControllerTest`, `AutenticacionSesionControllerTest`, `AutenticacionFlujoRealTest`, `RecuperacionClaveServiceTest`, `AlmacenIntentosFallidosTest` |
| CP-A02 | OE2 | Inicio de sesión y registro con WebAuthn, desafíos de un solo uso | `WebAuthnControllerTest`, `RepositorioCredencialesWebAuthnTest`, `AlmacenDesafiosWebAuthnTest` |
| CP-A03 | OE2 | Ciclo de vida de dispositivos | `DispositivoControllerTest` |
| CP-A04 | OE3, OE4 | Registro de marcaciones, dispositivo propio y activo, idempotencia por `uuid`, generación de tardanza | `MarcacionServiceTest`, `MarcacionControllerTest` |
| CP-A05 | OE3 | Estados `VALIDO`, `OBSERVADO`, `FUERA_DE_TOLERANCIA`, `SOSPECHOSO`, puntualidad, distancia con PostGIS | `MotorValidacionContextualServiceTest`, `UbicacionRepositoryTest` |
| CP-A06 | OE5 | Flujo de estados, separación de funciones, duplicados, auditoría e historial | `IncidenciaServiceTest`, `IncidenciaControllerTest` |
| CP-A07 | OE6 | Usuarios, proyectos, ubicaciones, horarios y asignaciones, con permisos por rol | `UsuarioServiceTest`, `UsuarioControllerTest`, `ConfiguracionOperativaControllerTest`, `AsignacionServiceTest`, `HorarioServiceTest` |
| CP-A08 | Seguridad | Restricciones de integridad en la base | `RestriccionesBaseDatosTest` |
| CP-A09 | Seguridad | JWT, política de contraseñas, tokens revocados y de recuperación, validación de entradas, errores uniformes, CORS, hora no futura | `JwtServiceTest`, `PoliticaContrasenasTest`, `AlmacenTokensRevocadosTest`, `AlmacenTokensRecuperacionTest`, `SeguridadWebTest` |
| CP-A10 | Todos | La aplicación arranca con su contexto completo | `ClickClakBackendApplicationTests` |

### 6.2 Automatizados del frontend

| ID | Requerimiento | Qué verifica | Archivo |
|---|---|---|---|
| CP-F01 | OE5 | Acciones disponibles por estado, bloqueos por separación de funciones, fecha máxima por tipo | `frontend-admin/src/incidencias/reglas.test.ts` |
| CP-F02 | Todos | Compilación con `tsc` y build de producción de ambos frontends | `npm run build` |

> **Brecha.** `frontend-campo` **no tiene pruebas automatizadas**, y el panel administrativo solo tiene las reglas puras de incidencias: no hay pruebas de componentes ni de la cola sin conexión.

### 6.3 Extremo a extremo por HTTP

Script [prueba_e2e_marcacion.py](evidencia/prueba_e2e_marcacion.py). Crea sus propios datos (proyecto, ubicación de 150 m de radio, horario 08:00 con 10 minutos de tolerancia, dos colaboradores, dispositivos y una asignación).

| ID | Requerimiento | Caso | Resultado esperado |
|---|---|---|---|
| E1 | OE3 | Ingreso puntual dentro del radio | 201, `VALIDO`; sin tardanza |
| E2 | OE4 | Reenvío del mismo `uuid` con datos alterados | Devuelve la original, sin duplicar ni sobrescribir |
| E3 | OE3 | Salida a 1 km | `FUERA_DE_TOLERANCIA` |
| E4 | OE3 | Precisión reportada de 800 m | `SOSPECHOSO` |
| E5 | OE3 | A 160 m con precisión de 50 m y radio de 150 m | `OBSERVADO` |
| E6 | OE3, OE5 | Ingreso con retraso | Incidencia de tardanza automática, con historial y auditoría del sistema |
| E7 | Seguridad | Hora del evento en el futuro | 400 |
| E8 | OE3 | Colaborador sin asignación | `SIN_ASIGNACION` |
| E9 | OE2 | Dispositivo de otro colaborador | 403 |
| E10 | Seguridad | Marcar a nombre de otro usuario | 403 |
| E11 | Seguridad | Sin sesión | 401 |

### 6.4 Manuales en el panel administrativo

| ID | Requerimiento | Caso | Resultado esperado |
|---|---|---|---|
| CP-M01 | OE5 | Tomar para revisión, aprobar, rechazar con motivo, cerrar | El estado, el historial y los contadores se actualizan |
| CP-M02 | OE5 | Alta de incidencia: duplicado, fecha futura en ausencia y en permiso | 409; bloqueada; aceptada |
| CP-M03 | OE5, Seguridad | Separación de funciones con supervisor y con administrador | Botón deshabilitado con el motivo; el servidor responde 403 |
| CP-M04 | OE6 | Inicio de sesión y navegación por las secciones del panel | Cada sección carga sin errores en consola |

### 6.5 No ejecutables en este entorno

| ID | Requerimiento | Caso | Motivo | Cuándo |
|---|---|---|---|---|
| CP-X01 | OE2 | Inicio de sesión con WebAuthn y biometría real | Exige un autenticador | Prueba en un dispositivo Android |
| CP-X02 | OE4 | Cola sin conexión y sincronización diferida en el cliente | Exige service worker, geolocalización y WebAuthn | Prueba en Chrome Android |

**Resultado probado (3-oct-2026).** CP-X03 dejó de estar pendiente: se desplegó el commit `8ae83d1` de `main` y `verificar-despliegue.sh` terminó con **52/52 comprobaciones correctas**. Véase [`evidencia/despliegue-main-2026-10-03.txt`](evidencia/despliegue-main-2026-10-03.txt).

## 7. Criterios

- **Entrada:** el estado probado compila y la base de desarrollo está disponible.
- **Salida (aprobado):** 0 pruebas automatizadas con fallo o error; todos los casos E2E y manuales ejecutados con resultado registrado; los casos no ejecutables declarados con su motivo.
- **Defectos:** cada defecto hallado se registra con su severidad y su estado (corregido o abierto) en los resultados.

## 8. Trazabilidad

Cada caso se asocia al requerimiento que cubre. Los **requerimientos sin ningún caso ejecutado en este entorno** se declaran como brecha en [resultados-de-pruebas.md](resultados-de-pruebas.md), sección de brechas.
