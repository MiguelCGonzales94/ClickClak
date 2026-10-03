# Pipeline de integración continua (CI)

Cap. IX 9.1 y 9.2 del informe · CLICKCLACK-9. Describe qué valida el CI de ClickClak, cómo se probó y qué no cubre. Los flujos están en `.github/workflows/`.

Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

> **Estado de verificación.** Los flujos se escribieron y se **ejecutaron localmente comando por comando**, desde cero y contra una base de datos vacía. **Todavía no se han ejecutado en GitHub Actions**: el primer resultado real será el del pull request que los incorpore. Lo que dependa de Actions (caché, artefactos, resumen del paso) no está probado.

## 1. Alcance y decisiones

- **Decisión (2-sep-2026):** GitHub Actions como herramienta de CI.
- **Requerimiento de `CONTRIBUTING.md`:** el CI se dispara solo sobre la carpeta que cambió. Hay un flujo por componente, con filtro de rutas.
- **Decisión:** solo se usan acciones oficiales de GitHub (`actions/checkout`, `actions/setup-java`, `actions/setup-node` y `actions/upload-artifact`), sin acciones de terceros ni secretos. Los flujos solo piden permiso de lectura del contenido.
- **Decisión:** el CI **no despliega**. Es integración continua, no entrega continua. El despliegue sigue siendo manual con `infra/scripts/desplegar.sh` (ver [despliegue-azure.md](despliegue-azure.md)).
- **Decisión:** las ejecuciones repetidas sobre la misma rama se cancelan (`concurrency`), para no gastar minutos.

## 2. Flujos

| Flujo | Se dispara cuando cambia | Qué valida |
|---|---|---|
| `backend.yml` | `backend/**` | Compila y ejecuta toda la suite con **JDK 21** (el de producción) contra un Postgres 16 con PostGIS 3.4 como servicio. Publica un resumen por clase y conserva los informes de Surefire 14 días. |
| `frontend-admin.yml` | `frontend-admin/**` | `npm ci`, `npm run build` (`tsc` y build de producción), `npm run test --if-present` (Vitest) y `npm audit` de producción. |
| `frontend-campo.yml` | `frontend-campo/**` | Lo mismo para la aplicación de campo; el build también genera el service worker. |
| `infra.yml` | `infra/**` | Sintaxis de los scripts de shell y validación estructural de los archivos compose. |

Todos corren también en `push` a `main` y a mano (`workflow_dispatch`), y cada uno se dispara además si cambia su propio archivo de flujo.

Detalles de diseño:

- **El backend usa el puerto 5434 del servicio.** El perfil `dev` apunta a `localhost:5434`; el servicio de Postgres del flujo publica ese mismo puerto, de modo que las pruebas corren sin cambiar configuración.
- **`mvnw` se invoca con `sh`.** El script se versionó sin permiso de ejecución.
- **`npm audit` falla con severidad alta o crítica**, no con moderada: hoy ambos frontends tienen 2 vulnerabilidades moderadas de `react-router-dom` 6.x (CLICKCLACK-68) y el flujo no debe quedar en rojo por algo ya conocido y documentado.
- **Los compose se validan con valores de prueba.** Exigen variables de `infra/.env`, que no está en el repositorio; el paso les da un valor ficticio solo para comprobar la estructura del archivo.

## 3. Evidencia

Se ejecutaron en local los mismos comandos de cada flujo, el 3-oct-2026, sobre **dos estados** del repositorio y con una base de datos recién creada (sin esquema), para reproducir lo que ve una máquina limpia.

| Estado probado | Backend | `frontend-admin` | `frontend-campo` | `infra` |
|---|---|---|---|---|
| `main` actual más los flujos (lo que verá el PR que los incorpore) | 104 pruebas, 0 fallos, build correcto | Build correcto, audit correcto, sin pruebas | Build correcto, audit correcto, sin pruebas | 1 compose válido, sin scripts |
| Simulacro de la fusión de los 13 PR abiertos más los flujos | **178 pruebas, 0 fallos, 0 errores, en 45 s** | Build, 17 pruebas Vitest y audit correctos | Build y audit correctos, sin pruebas | 9 scripts con sintaxis correcta y 2 compose válidos |

Otros datos medidos:
- Contra la base vacía, Flyway aplicó V1, V2 y la semilla de desarrollo, y las 178 pruebas pasaron: **no dependen de datos previos** en la base.
- El paso de resumen del backend se ejecutó tal como está escrito en el YAML y produjo la tabla por clase.
- Los cuatro archivos son YAML válido y solo referencian acciones oficiales.

## 4. Lo que el pipeline no hace

| No cubierto | Detalle |
|---|---|
| **Despliegue continuo** | Nada se publica en la VM de forma automática. |
| **Lint** | Los frontends tienen un script `lint` que invoca `eslint`, pero `eslint` no está entre sus dependencias: el script fallaría, por eso no se incluyó. |
| **Pruebas de `frontend-campo`** | No tiene pruebas; el paso solo evita fallar. La cola sin conexión no tiene cobertura automatizada. |
| **Análisis de dependencias del backend** | El `npm audit` cubre los frontends; Maven no tiene equivalente todavía (CLICKCLACK-69). |
| **Escaneo de imágenes de contenedor y cobertura de código** | No hay. |
| **Protección de `main`** | Los flujos informan, pero nada impide fusionar con el CI en rojo, porque `main` no está protegida. |
| **Verificación de la VM** | `verificar-despliegue.sh` necesita la VM encendida y no corre en el CI. |

## 5. Limitaciones conocidas

- **Filtros de rutas y comprobaciones obligatorias.** Si se exigiera un flujo con filtro de rutas como comprobación obligatoria, un PR que no toque esa carpeta quedaría esperando un resultado que nunca llega. **Recomendación:** antes de exigir comprobaciones, agregar un flujo de resumen que corra siempre.
- **Versiones fijadas por etiqueta** (`@v4`), no por huella (SHA): una etiqueta puede moverse. Es una decisión de simplicidad para este proyecto.
- **Minutos de Actions.** Si el repositorio pasa a privado, los minutos gratuitos dependen del plan. No se verificó el cupo.
- **Base de datos de las pruebas.** Las pruebas usan un Postgres efímero del servicio, no una copia de producción.

## 6. Cómo ejecutarlo en local

```bash
# Backend, con un Postgres con PostGIS en el puerto 5434 (por ejemplo el de infra/docker-compose.yml)
cd backend && sh ./mvnw -B -ntp test

# Frontends (desde cada carpeta)
npm ci && npm run build && npm run test --if-present && npm audit --omit=dev --audit-level=high

# Infraestructura
find infra -type f \( -name '*.sh' -o -name '*.envsh' \) -exec sh -n {} \;
```

## 7. Pendientes

1. **Ver el primer resultado real en GitHub Actions** y corregir lo que falle, que no se puede anticipar desde local.
2. **Proteger `main`:** exigir pull request y revisión, y comprobaciones cuando exista el flujo de resumen. Es un ajuste en la configuración de GitHub que decide el equipo.
3. **Actualizar `CONTRIBUTING.md`:** ya describe un CI que ahora existe, pero sigue diciendo que `main` está protegida y que se prefiere el squash, y ninguna de las dos cosas es cierta.
4. **Lint, análisis de dependencias del backend y pruebas de `frontend-campo`.**
5. **Entrega continua** hacia la VM, si el equipo la quiere.
