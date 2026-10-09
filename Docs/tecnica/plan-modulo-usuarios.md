# Plan de trabajo: mejora del módulo de usuarios (HU04)

Plan ejecutable de punta a punta con **una sola confirmación**. Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. Alcance

Hallazgos que se corrigen (revisión del 8-oct-2026, leyendo el código; no se reprodujeron en la VM):

| # | Hallazgo | Fase |
|---|---|---|
| H1 | Desactivar un usuario no corta su sesión: `JwtAuthenticationFilter` solo mira tokens revocados, no el estado del usuario (tokens de 8 h). | 1 |
| H2 | Cambiar el rol deja la contraseña inconsistente (COLABORADOR a SUPERVISOR queda sin hash; el camino inverso conserva el hash). | 1 |
| H3 | Un admin puede desactivarse, eliminarse o bajarse de rol y dejar el sistema sin administradores. | 1 |
| H4 | El correo distingue mayúsculas (consulta y `UNIQUE`), pero la edición lo compara sin distinguirlas. | 1 |
| H5 | El panel no edita usuarios; no hay confirmación ni manejo de error al activar o desactivar; no hay búsqueda, filtros ni paginación; el tipo de documento es texto libre. | 4 |
| H6 | No existe eliminación, estado de cuenta visible, historial por usuario ni cambio de contraseña. | 2, 3 |

**Decisiones del usuario (8-oct-2026):**
- **D1.** Cortar la sesión consultando al usuario en cada petición (no lista en memoria).
- **D2.** Cambiar de rol está permitido: al pasar a SUPERVISOR o RRHH_ADMIN se exige contraseña; al pasar a COLABORADOR se borra el hash.
- **D3.** Eliminar = borrado físico solo si el usuario no tiene ningún historial; si lo tiene, responde 409 y se ofrece desactivar.
- **D4.** Estado de cuenta = `ACTIVA`, `INACTIVA`, `BLOQUEADA` (5 intentos fallidos) y `CLAVE_PENDIENTE`.
- **D5.** Contraseñas: cambio propio (con la actual) y restablecimiento por el admin con clave temporal de un solo uso visible.

**Fuera de alcance:** desplegar en la VM de Azure, mezclar pull requests, persistir el bloqueo por intentos (sigue en memoria, límite ya documentado), enviar correos, segundo factor.

## 2. Reglas de ejecución

- **Nomenclatura en español** para todo el código nuevo (variables, funciones, tablas, columnas, endpoints, clases).
- Estilo del código vecino: comentarios Javadoc breves que explican el porqué, `record` para DTOs, `@Transactional` en servicios, auditoría con `BitacoraAuditoria`.
- **Git:** GitHub Flow de `CONTRIBUTING.md`. Cada fase en su rama, apilada sobre la anterior, y un pull request por fase. Mensajes de commit en imperativo con prefijo `backend:`, `frontend-admin:`, `docs:` o `infra:`.
- **Aislamiento:** se trabaja en un *git worktree* nuevo creado desde `main`, para no tocar `codex/offline-operativo`, que tiene cambios sin confirmar.
- Un commit por paso numerado, con las pruebas del paso en verde antes de pasar al siguiente.
- Nunca se escribe una contraseña real ni temporal en logs, bitácora, pruebas ni documentos.

### Ramas y pull requests

| Fase | Rama | Base del PR |
|---|---|---|
| 1 | `feature/usuarios-1-base` | `main` |
| 2 | `feature/usuarios-2-crud-estado` | rama de la fase 1 |
| 3 | `feature/usuarios-3-claves` | rama de la fase 2 |
| 4 | `feature/usuarios-4-panel` | rama de la fase 3 |
| 5 | `feature/usuarios-5-docs` | rama de la fase 4 |

**Supuesto:** el equipo usa *squash merge*. Tras fusionar una fase hay que reapuntar el PR siguiente a `main` y resolver el conflicto de squash; la fase 5 solo toca `Docs/` y `infra/scripts/`, así que es la más fácil de reapuntar.

### Condiciones de parada

Me detengo y consulto, sin improvisar, si ocurre cualquiera de estas:
1. Las pruebas existentes fallan por algo distinto de un cambio intencional del plan, y no se resuelve con un ajuste directo de la prueba.
2. La migración V3 detecta correos duplicados al normalizar o falla en la base local.
3. Otro módulo (Asignaciones, Dashboard, Incidencias, la app de campo) deja de compilar o de pasar sus pruebas por culpa de un cambio de contrato.
4. Un paso exige algo fuera del alcance: tocar la VM, enviar correo, instalar dependencias nuevas o cambiar la política de seguridad ya documentada.
5. El borrado físico (paso 2.4) encuentra una tabla con referencias a `usuario` que no está prevista aquí.

## 3. Preparación (paso 0)

0.1. Crear el worktree: `git worktree add ../ClickClak-usuarios -b feature/usuarios-1-base main`. Copiar este plan a `Docs/tecnica/` en el worktree y confirmarlo como primer commit (`docs: agrega el plan de trabajo del módulo de usuarios`).
0.2. Levantar Postgres local: `docker compose -f infra/docker-compose.yml up -d postgres` (puerto 5434).
0.3. Línea base: `cd backend && ./mvnw test` y, en `frontend-admin`, `npm ci && npm test && npm run build`. Anotar cuántas pruebas pasan; esa cifra es la referencia para todo el plan.

## 4. Fase 1: corregir fallas (rama `feature/usuarios-1-base`)

1.1. **Migración `V3__usuarios_estado_cuenta.sql`**
- Agrega a `usuario`: `debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE`, `desactivado_en TIMESTAMPTZ`, `motivo_baja VARCHAR(255)`.
- Un bloque `DO` aborta con un mensaje claro si `lower(correo)` produciría duplicados; después normaliza `correo` a minúsculas y crea el índice único `uk_usuario_correo_minusculas` sobre `lower(correo)`.
- Actualiza la entidad `Usuario` con los tres campos.
- Prueba: la migración corre limpia sobre la base vacía del CI y sobre la base local con la semilla de desarrollo.

1.2. **Correo normalizado (H4).** Una función única (`trim` y `toLowerCase(Locale.ROOT)`) usada en alta, edición, login (`AutenticacionService`) y recuperación (`RecuperacionClaveService`). La clave de `AlmacenIntentosFallidos` ya se normaliza. Pruebas: `Ana@x.com` y `ana@x.com` chocan como duplicados; el login funciona con cualquier combinación de mayúsculas.

1.3. **Corte de sesión (H1, D1).** `JwtAuthenticationFilter` recibe `UsuarioRepository` y, por petición, carga al usuario por id:
- Inexistente o inactivo: limpia el contexto de seguridad (queda como no autenticado, 401).
- Con `debeCambiarClave`: solo deja pasar `/api/auth/cambiar-clave` y `/api/auth/logout`; el resto responde 403 con un código de error estable (`CLAVE_PENDIENTE`).
- El rol se toma de la base y no del token, para que un cambio de rol tenga efecto inmediato.
- Ajustar las pruebas existentes que construyen el filtro o autentican con un token fabricado. Pruebas nuevas: usuario desactivado con token aún vigente recibe 401; usuario con clave pendiente recibe 403 fuera del endpoint permitido.

1.4. **Rol y contraseña al editar (H2, D2).** `EditarUsuarioRequest` gana `password` opcional.
- Pasar a rol distinto de COLABORADOR desde COLABORADOR, sin `password`: 400.
- Pasar a COLABORADOR: se anula `passwordHash` y no se acepta `password`.
- Sin cambio de tipo de rol: `password` ignorado con 400 si viene (el cambio de clave tiene su propio endpoint, fase 3).
- La bitácora registra el cambio de rol, nunca la contraseña.

1.5. **Protección de administradores (H3).** Reglas en `UsuarioService`, con mensaje claro (409 o 400 según el caso):
- El actor no puede desactivarse, eliminarse ni cambiarse el rol a sí mismo.
- No se puede desactivar ni bajar de rol al último RRHH_ADMIN activo.
- Se agrega `contarPorRolYActivo(rol, activo)` al repositorio.

1.6. **Cierre de la fase:** `./mvnw test` completo; suite en verde con las pruebas nuevas. Abrir PR de fase 1.

## 5. Fase 2: CRUD completo y estado de cuenta (rama `feature/usuarios-2-crud-estado`)

2.1. **`EstadoCuenta` (D4).** Enum `ACTIVA`, `INACTIVA`, `BLOQUEADA`, `CLAVE_PENDIENTE`. Se calcula al responder: inactivo si `activo` es falso; `CLAVE_PENDIENTE` si `debeCambiarClave`; `BLOQUEADA` si `AlmacenIntentosFallidos.tiempoRestanteDeBloqueo(correo)` no está vacío. Orden de precedencia: INACTIVA, BLOQUEADA, CLAVE_PENDIENTE, ACTIVA. `UsuarioResponse` suma `estadoCuenta`, `desactivadoEn` y `motivoBaja`.

2.2. **Baja con motivo.** `POST /{id}/desactivar` acepta un cuerpo opcional `{ "motivo": "..." }` (máx. 255). Al desactivar se guarda `desactivadoEn` y `motivoBaja`; al activar se limpian. Compatibilidad: sin cuerpo sigue funcionando.

2.3. **Desbloqueo.** `POST /{id}/desbloquear` (solo RRHH_ADMIN). Agrega `limpiar(correo)` a `AlmacenIntentosFallidos` si no existe. Registra en bitácora.

2.4. **Eliminación (D3).** `DELETE /api/usuarios/{id}` (solo RRHH_ADMIN).
- Primero se enumeran las tablas con clave foránea hacia `usuario` (consulta a `information_schema` sobre la base local) y se deja la lista como comentario en el servicio.
- Si existe cualquier referencia: 409 con mensaje que sugiere desactivar. Se captura además `DataIntegrityViolationException` como red de seguridad.
- Si no existe ninguna: borra, y la bitácora conserva `valoresAnteriores` con los datos del usuario eliminado.
- **Supuesto:** quien haya generado entradas de bitácora como actor tiene referencias y no se puede eliminar; es lo deseado para conservar la trazabilidad.

2.5. **Búsqueda paginada.** `GET /api/usuarios/buscar?q=&rol=&estado=&pagina=&tamano=` devuelve `PaginaResponse<UsuarioResponse>` (contenido, página, tamaño, total). `q` busca en nombres, apellidos, correo y número de documento sin distinguir mayúsculas. Tamaño por defecto 20, máximo 100.
- **Decisión:** `GET /api/usuarios` se deja **tal cual** (lista completa) porque lo usan Asignaciones, Dashboard, Incidencias y la app de campo; evita un cambio de contrato.
- El filtro por `estado` se aplica en el servicio sobre el estado calculado cuando es `BLOQUEADA`, y en la consulta para los demás.

2.6. **Historial.** `GET /api/usuarios/{id}/historial` (RRHH_ADMIN): entradas de `bitacora_auditoria` con `entidad = 'usuario'` y `entidad_id = id`, de la más reciente a la más antigua, con actor, acción, fecha y cambios. Si hace falta, se agrega un valor a `AccionAuditoria` para los cambios de clave y se documenta.

2.7. **Pruebas** de servicio y de controlador por cada regla, incluyendo permisos (Supervisor recibe 403 en todo lo nuevo salvo lo que ya podía leer). PR de fase 2.

## 6. Fase 3: contraseñas (rama `feature/usuarios-3-claves`)

3.1. **Cambio propio (D5).** `POST /api/auth/cambiar-clave` con `{ claveActual, claveNueva }`.
- Verifica la actual con `PasswordEncoder.matches`; si falla, registra el fallo en `AlmacenIntentosFallidos` y responde con el mismo error genérico del login.
- Aplica `PoliticaContrasenas`; la nueva debe ser distinta de la actual.
- Guarda el hash, pone `debeCambiarClave` en falso, revoca el token en uso (`AlmacenTokensRevocados`) y registra en bitácora (sin la clave). El usuario vuelve a iniciar sesión.
- Colaboradores: 400 (no tienen contraseña).

3.2. **Restablecimiento por admin.** `POST /api/usuarios/{id}/restablecer-clave` (solo RRHH_ADMIN).
- Genera una clave temporal de 12 caracteres con `SecureRandom` que cumple `PoliticaContrasenas`, guarda su hash, marca `debeCambiarClave` y la devuelve **una sola vez** en `ClaveTemporalResponse`, con `Cache-Control: no-store`.
- No se escribe en logs ni en bitácora (solo "clave restablecida").
- Rechaza: usuario colaborador (400), usuario inactivo (409), el propio actor (usar el cambio propio).
- Revoca las sesiones del usuario afectado: lo logra el filtro de 1.3 en la siguiente petición solo para clave pendiente, así que el endpoint permite únicamente el cambio de clave hasta que lo haga.

3.3. **Login.** La respuesta de login incluye `debeCambiarClave`, para que el panel redirija a la pantalla de cambio.

3.4. **Pruebas:** clave actual incorrecta, clave débil, misma clave, colaborador, inactivo, flujo completo (restablecer, login, solo se puede cambiar la clave, cambiar, entrar normal). PR de fase 3.

## 7. Fase 4: panel de administración (rama `feature/usuarios-4-panel`)

Archivos en `frontend-admin/src`: `types/api.ts`, `services/servicioUsuarios.ts`, `pages/PaginaUsuarios.tsx` (se divide), `components/` (formulario, filtros, confirmación, historial), nueva `pages/PaginaCambiarClave.tsx`, `routes/`, `store/ContextoSesion`.

4.1. Tipos y servicio: `buscar`, `eliminar`, `desbloquear`, `restablecerClave`, `historial`, `cambiarClave`; `estadoCuenta` y campos nuevos en los tipos.
4.2. Lista con búsqueda (con retardo al escribir), filtros de rol y estado, paginación y etiqueta de estado de cuenta (ACTIVA, INACTIVA, BLOQUEADA, CLAVE_PENDIENTE).
4.3. Formulario compartido de alta y edición: tipo de documento como lista (DNI, CE, Pasaporte) con validación del formato (DNI de 8 dígitos); contraseña solo cuando el rol la requiere; cambio de rol pide contraseña inicial si corresponde.
4.4. Acciones por usuario, con confirmación y errores visibles: editar, activar o desactivar (con motivo), desbloquear, restablecer clave (muestra la temporal una vez, con botón de copiar, y avisa que no se podrá ver de nuevo), eliminar (el 409 se muestra como "tiene historial: desactívalo").
4.5. Panel de historial por usuario.
4.6. `PaginaCambiarClave`: cambio propio y primer ingreso obligatorio; el guard de rutas redirige cuando el login trae `debeCambiarClave`. Enlace "Cambiar mi contraseña" en el menú.
4.7. Pruebas Vitest de las funciones puras (validación de documento, estado visible, armado de filtros). Verificación en vivo con el backend en perfil `dev` y el navegador integrado: alta, edición, búsqueda, desactivar con sesión abierta en otra pestaña, restablecer clave, cambio obligatorio, eliminar con y sin historial. Capturas de pantalla como evidencia.
4.8. `npm run build` y `npm test` en verde. PR de fase 4.

## 8. Fase 5: documentación y cierre (rama `feature/usuarios-5-docs`)

5.1. `Docs/tecnica/plan-de-pruebas.md`: casos nuevos (corte de sesión, rol y contraseña, último admin, eliminación, estados, claves). `resultados-de-pruebas.md`: cifras reales de las suites al cierre.
5.2. `Docs/tecnica/base-de-datos.md`: V3 y las columnas nuevas. `Docs/tecnica/seguridad-web.md`: corte de sesión, clave temporal y política de eliminación.
5.3. `infra/scripts/verificar-despliegue.sh`: comprobaciones nuevas del corte de sesión y del cambio de clave obligatorio, usando un usuario temporal que la propia comprobación crea y borra. Probar el script contra el stack local.
5.4. Jira (proyecto CLICKCLACK): una tarea por fase bajo la historia HU04, con el enlace al PR. Antes de crear nada se lee el tablero para no duplicar.
5.5. **Para el despliegue (lo hace el usuario o se pide aparte):** antes de aplicar V3 en la VM, ejecutar `SELECT lower(correo), count(*) FROM usuario GROUP BY 1 HAVING count(*) > 1;` y confirmar que no devuelve filas.

## 9. Informe final al usuario

Al terminar, un resumen con: los cinco PR (enlaces), cifras de pruebas antes y después, qué hallazgos quedaron cerrados y cuáles no, límites conocidos (bloqueo en memoria, clave temporal visible en pantalla, consulta por petición) y los pasos pendientes del despliegue.
