# Seguridad web — controles, evidencia y riesgos

> Cap. VI 6.3 del informe (APF2) · CLICKCLACK-57 y CLICKCLACK-7. Documenta qué controles de seguridad tiene ClickClak, dónde viven, cómo se comprobó que funcionan y qué riesgos quedan abiertos. La verificación se hizo el 3 de octubre de 2026 sobre la VM de Azure y en pruebas automatizadas del backend.

**Etiquetas:** **Implementado** = existe y tiene evidencia · **Parcial** = existe con límites declarados · **Pendiente** = decidido pero no hecho · **No aplica** = el riesgo no existe en esta arquitectura.

## 1. Enfoque

La defensa está en tres capas, de modo que ninguna dependa de las otras:

| Capa | Qué protege | Dónde |
|---|---|---|
| **Proxy Nginx** | Cifrado en tránsito, cabeceras y CSP, límites de peticiones por IP, superficie expuesta | `infra/nginx/` |
| **Backend Spring** | Autenticación, autorización, validación de entradas, errores, bloqueo por fuerza bruta, auditoría | `backend/` |
| **Infraestructura** | Aislamiento de red, secretos, acceso a la VM, cifrado en reposo | VM de Azure, Docker Compose |

Activos a proteger, en orden de importancia: los datos de asistencia y ubicación de los técnicos, las credenciales de los administradores y la integridad de las incidencias, que pueden afectar el pago.

## 2. Controles implementados

| # | Control | Capa | Evidencia |
|---|---|---|---|
| C1 | **TLS 1.2 y 1.3** con suites modernas y sin tickets de sesión; HTTP redirige a HTTPS | Proxy | `verificar-despliegue.sh`: redirección 301 |
| C2 | **Cabeceras de seguridad**: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy` (GPS solo del propio origen), `Cross-Origin-Opener-Policy` | Proxy | Verificación: las cuatro primeras presentes en 443 y 8443 (`Cross-Origin-Opener-Policy` está configurada pero el script no la comprueba) |
| C3 | **CSP estricta**: `default-src 'self'`, sin scripts ni recursos externos, `frame-ancestors 'none'` | Proxy | Prueba en navegador (sección 4.3) |
| C4 | **HSTS**, activado solo con certificado real | Proxy | Verificación: presente en 443 y 8443 con Let's Encrypt; `max-age=31536000` |
| C5 | **Límite de peticiones por IP**: login y WebAuthn 10/min (ráfaga 5), recuperación de clave 6/min (ráfaga 3), API 20/s | Proxy | Verificación: la ráfaga a la recuperación recibe 429 |
| C6 | **Superficie mínima**: `/actuator` y archivos ocultos no se enrutan, métodos limitados, cuerpos de máximo 1 MiB, plazos cortos contra conexiones lentas | Proxy | Verificación: 404 en `/actuator/*`, `/.env`, `/.git/config`; 405 en `TRACE` |
| C7 | **Solo el proxy publica puertos**; Postgres, backend y frontends en red interna | Infra | `docker-compose.prod.yml`; verificación |
| C8 | **Bloqueo por fuerza bruta**: 5 contraseñas incorrectas en 15 minutos bloquean la cuenta 15 minutos, aunque luego se acierte la clave | Backend | `AlmacenIntentosFallidosTest` (9), `AutenticacionServiceTest` y `SeguridadWebTest` |
| C9 | **Login sin enumeración de cuentas**: mismo mensaje y mismo trabajo de hash exista o no la cuenta, esté activa o tenga contraseña | Backend | `AutenticacionServiceTest.sinCuentaUtil_siempreSeComparaContraElHashFicticio` |
| C10 | **Contraseñas con bcrypt**, política mínima de 8 caracteres con letras y números, máximo 72 | Backend | `PoliticaContrasenasTest`, validación de DTOs |
| C11 | **JWT HS256** firmado con clave simétrica (la biblioteca rechaza claves de menos de 256 bits; no hay prueba propia de ese rechazo) y revocación en el cierre de sesión | Backend | `JwtServiceTest`, `AlmacenTokensRevocadosTest` |
| C12 | **Autorización por rol** en cada operación de escritura y en los catálogos; el colaborador solo ve y registra lo suyo | Backend | `IncidenciaControllerTest`, `MarcacionControllerTest`, `SeguridadWebTest` |
| C13 | **Separación de funciones**: nadie revisa una incidencia que le afecta o que él mismo registró | Backend | `IncidenciaServiceTest.nadieRevisaUnaIncidenciaQueLeAfecta` |
| C14 | **Validación de entradas**: tamaños acordes a cada columna de la base, rangos de coordenadas, formato de días de la semana, campos obligatorios | Backend | `SeguridadWebTest` (campos desmedidos, coordenadas, horario) |
| C15 | **Errores uniformes sin detalles internos**: ni trazas, ni SQL, ni nombres de clases; los fallos inesperados salen como 500 genérico | Backend | `SeguridadWebTest.unFalloInternoSaleComo500GenericoSinDetalles` |
| C16 | **CORS restringido por entorno**: en producción ningún origen externo | Backend | `SeguridadWebTest.corsAutorizaElOrigenConfigurado…` |
| C17 | **Hora del evento no futura** en las marcaciones (tolerancia de 5 minutos) | Backend | `SeguridadWebTest.laMarcacionRechazaUnEventoDelFuturo` |
| C18 | **Auditoría**: historial de cada cambio de estado de una incidencia y bitácora con valores anteriores y nuevos | Backend | `IncidenciaControllerTest.flujoCompleto_dejaHistorialYAuditoriaDeCadaPaso` |
| C19 | **Restricciones de integridad en la base**: dominios cerrados, fechas y horarios coherentes, asignaciones sin solapamiento | Base de datos | `RestriccionesBaseDatosTest` (8) |
| C20 | **Secretos fuera del repositorio**: se generan en la VM con `openssl` y viven en un archivo con permisos 600; sin cuenta de desarrollo en producción | Infra | `despliegue-azure.md`; migraciones sin la semilla `dev` |
| C21 | **Acceso a la VM solo por llave SSH** (contraseña desactivada) y `fail2ban` | Infra | `sshd -T`: `passwordauthentication no` |
| C22 | **Cifrado en reposo del disco** con clave de la plataforma | Infra | `az disk`: `EncryptionAtRestWithPlatformKey` |

## 3. OWASP Top 10 (2021)

| Categoría | Estado | Cómo se cubre y qué falta |
|---|---|---|
| **A01** Control de acceso roto | **Implementado** | C12, C13, C7. El mínimo privilegio se corrigió el 3-oct: los listados de proyectos, ubicaciones y horarios eran accesibles a cualquier colaborador y exponían las coordenadas de todas las sedes. Falta una prueba automática por cada endpoint (matriz de roles). |
| **A02** Fallos criptográficos | **Parcial** | C1, C4, C10, C11, C22. El acceso público usa un certificado válido de Let's Encrypt y HSTS. El tráfico entre contenedores no va cifrado (misma VM) y no hay cifrado por columna. |
| **A03** Inyección | **Implementado** | Todas las consultas usan parámetros nombrados, sin concatenación (revisión de `@Query` en los repositorios, incluida la consulta nativa de distancia). C14 y C3 frente a XSS. |
| **A04** Diseño inseguro | **Parcial** | C8, C13, C17 y el flujo de estados de las incidencias. No hay un modelo de amenazas formal más allá de esta sección. |
| **A05** Configuración de seguridad incorrecta | **Implementado** | C2, C3, C6, C7, C15, C16. Se detectó y corrigió que el proxy conservaba el `default.conf` de la imagen (página de bienvenida) y que un cambio de configuración no se aplicaba sin recrear el contenedor. |
| **A06** Componentes vulnerables | **Parcial** | `npm audit` (solo producción): **2 moderadas en cada frontend**, ambas de `react-router-dom` 6.x: redirección abierta con barra invertida en `<Link>`/`useNavigate`, e inyección en hidratación SSR. **No son explotables aquí:** todos los destinos de navegación son literales y no hay SSR. El arreglo exige pasar a la versión 7 (cambio mayor); ver pendientes. **Las dependencias del backend no se han analizado.** |
| **A07** Fallos de identificación y autenticación | **Parcial** | C8, C9, C10, C11. Faltan: segundo factor para supervisores y RRHH (solo los colaboradores usan WebAuthn), política de contraseñas más exigente y cambio de contraseña con sesión iniciada. |
| **A08** Fallos de integridad de software y datos | **Pendiente** | No hay integración continua ni verificación de las imágenes base (CLICKCLACK-9). Las imágenes vienen de los registros oficiales. |
| **A09** Fallos de registro y monitoreo | **Parcial** | C18 y los logs de error del backend. Faltan alertas de eventos de seguridad (intentos bloqueados, 429, 401 repetidos): dependen del monitoreo de CLICKCLACK-13. |
| **A10** Falsificación de petición del lado del servidor | **No aplica** | El backend no hace peticiones a direcciones indicadas por el usuario. |

## 4. Evidencia

### 4.1 Pruebas automatizadas del backend

178 pruebas, 0 fallos, contra Postgres real. De ellas, **30 son nuevas de este trabajo**: `SeguridadWebTest` (16), `AlmacenIntentosFallidosTest` (9) y 5 casos nuevos en `AutenticacionServiceTest`. Cubren bloqueo con ventana de tiempo, comparación contra hash ficticio, errores sin detalles internos (JSON mal formado, método no permitido, ruta inexistente, tipo no soportado, fallo interno), CORS, catálogos por rol, Actuator protegido, cabeceras en las respuestas de error y validación de entradas.

### 4.2 Verificación del despliegue

**Resultado probado (3-oct-2026).** `infra/scripts/verificar-despliegue.sh` ejecutó **52 comprobaciones** en la VM real y las 52 pasaron: 40 corresponden al despliegue y la seguridad web, y 12 a la replicación de PostgreSQL. Las 22 específicas de seguridad web comprueban, en los puertos 443 y 8443, las cabeceras de seguridad y que el servidor no revele su versión; además HSTS coherente con el certificado, ausencia de cabeceras duplicadas en la API, `/actuator` y archivos ocultos con 404, `TRACE` con 405, errores sin detalles internos y el límite de la recuperación de clave. La ejecución posterior a Let's Encrypt confirmó HSTS presente y la cadena pública válida. Véase [`evidencia/letsencrypt-hsts-2026-10-03.txt`](evidencia/letsencrypt-hsts-2026-10-03.txt).

### 4.3 La CSP en un navegador real

Se sirvieron los dos frontends compilados, con el mismo archivo de cabeceras del proxy, y se abrieron en un navegador:

| Prueba | Resultado |
|---|---|
| Carga de la app de campo y del panel administrativo | Se renderizan completos y sin ninguna violación de la política |
| Llamada a la API del mismo origen | Permitida |
| Llamada a otro origen (`https://example.com`) | **Bloqueada** por `connect-src 'self'` |
| Script en línea inyectado | **Bloqueado** por `script-src 'self'` |
| Script externo | **Bloqueado** por `script-src 'self'` |
| Cabeceras de la página | `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `nosniff` |

Además, un análisis de lo que sirve la app desplegada no encontró scripts ni estilos en línea, atributos `on*`, `eval` ni referencias a otros orígenes.

**Limitación de esta prueba:** el navegador usado no permite registrar service workers (falla igual sin ninguna cabecera de seguridad), así que el registro del service worker de la PWA **no pudo validarse** con la política activa. Hay que comprobarlo en Chrome sobre Android cuando exista el certificado real.

## 5. Riesgos y limitaciones conocidos

| Riesgo | Detalle | Mitigación o decisión |
|---|---|---|
| Enlace de recuperación en el log | Sin servicio de correo, el enlace de restablecimiento se escribe en el log del backend; quien lea los logs puede restablecer claves | Aceptado para esta versión; requiere integrar SMTP |
| Estado en memoria | La revocación de tokens, los intentos fallidos, los tokens de recuperación y los desafíos WebAuthn se pierden al reiniciar el backend | Hay una sola instancia; para varias haría falta Redis o la base de datos |
| Bloqueo dirigido | Quien conozca un correo puede bloquearlo 15 minutos enviando intentos fallidos | Costo de bloquear por cuenta; el límite por IP del proxy acota la repetición |
| Cupo por IP compartida | Técnicos tras la misma IP de operador de telefonía comparten el límite del login | El cupo (10/min, ráfaga 5) deja margen para uso normal |
| SSH abierto a cualquier IP | El equipo se conecta desde sitios distintos | Solo llave, sin contraseña, y `fail2ban` |
| JWT simétrico de 8 horas | Sin renovación ni rotación; la revocación solo ocurre al cerrar sesión | Vigencia pensada para operar con poca conectividad |
| Sin segundo factor para administradores | Supervisores y RRHH entran solo con contraseña | Pendiente |
| Sin análisis de dependencias del backend | Solo se auditaron las de los frontends | Pendiente |
| Sin WAF, sin detección de intrusiones | El límite de peticiones y `fail2ban` son las únicas defensas activas contra abuso | El monitoreo de CLICKCLACK-13 aportará visibilidad |
| Sin prueba de penetración | Los controles se verificaron uno a uno, pero no hubo una prueba ofensiva independiente | Recomendable antes del cierre del curso |

## 6. Pendientes de seguridad

1. Migrar `react-router-dom` a 7.18.4 o superior (cambio mayor).
2. Analizar las dependencias del backend (OWASP Dependency-Check o Dependabot).
3. Segundo factor para supervisores y RRHH.
4. Alertas de eventos de seguridad en el monitoreo.
5. Probar el service worker de la PWA con la política activa en Chrome sobre Android.
