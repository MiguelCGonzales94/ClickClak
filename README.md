# ClickClak

Sistema móvil + web de control de asistencia, puntualidad y gestión de incidencias laborales para personal de campo, con biometría delegada al SO, geolocalización contextual y operación offline-first.

Proyecto del curso **Curso Integrador II: Sistemas (100000SI85)** — UTP, 2026 Ciclo 2. Propuesta de mejora interna sobre el proceso AS-IS de registro de asistencia.

## Estructura del repositorio

Monorepo con tres componentes desplegables independientes más la infraestructura compartida:

| Carpeta | Qué es | Stack |
|---|---|---|
| [`backend/`](backend/) | API REST | Spring Boot 3 (Java 21), Spring Data JPA, Spring Security, PostgreSQL/PostGIS |
| [`frontend-admin/`](frontend-admin/) | Panel administrativo (RRHH/supervisores) | React + TypeScript + Vite + Tailwind, SPA sin offline |
| [`frontend-campo/`](frontend-campo/) | App de marcación (PWA instalable Android) | React + TypeScript + Vite + Tailwind + Workbox + Dexie.js + WebAuthn |
| [`infra/`](infra/) | Despliegue | Docker Compose, Nginx, Postgres/PostGIS, Prometheus + Grafana |
| [`prototipo/`](prototipo/) | Prototipo estático de diseño (paleta, tokens, pantallas) | HTML/CSS |
| [`Docs/`](Docs/) | Entregables oficiales del curso | — |

## Flujo de trabajo

Ver [`CONTRIBUTING.md`](CONTRIBUTING.md) para el modelo de ramas, convención de commits y checks de CI antes de mergear.

## Levantar el entorno local

1. Base de datos: `docker compose -f infra/docker-compose.yml up -d postgres` (Postgres+PostGIS, publicado en el puerto **5434** del host — no 5432/5433, ver nota abajo).
2. Backend: `cd backend && ./mvnw spring-boot:run` (usa el Maven Wrapper, no requiere tener Maven instalado). Flyway aplica las migraciones automáticamente al arrancar.
3. Tests: `cd backend && ./mvnw test`.

**Nota de puertos (por máquina, no del proyecto):** en algunos equipos del develop ya hay instancias nativas de PostgreSQL corriendo como servicio de Windows en 5432 y/o 5433. Si `docker compose up` no falla pero el backend no logra autenticarse, es señal de ese choque de puertos — cambiar el mapeo en `infra/docker-compose.yml` y en `backend/src/main/resources/application-dev.yml` a un puerto libre (verificar con `Get-NetTCPConnection -LocalPort <puerto>` en PowerShell).
