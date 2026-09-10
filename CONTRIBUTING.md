# Flujo de trabajo — ClickClak

Modelo: **GitHub Flow simplificado**. `main` siempre desplegable; todo cambio entra por rama corta + Pull Request + CI en verde.

## Ramas

- `main` — protegida. No se pushea directo; solo se mergea vía PR aprobado con CI en verde.
- `feature/<area>-<descripcion-corta>` — una rama por historia de usuario o tarea. Ej: `feature/backend-registro-marcacion`, `feature/frontend-campo-cola-offline`.
- `fix/<descripcion-corta>` — corrección de bug.

Sin ramas `develop` ni `release/*`: no aplican porque no hay múltiples versiones en soporte paralelo.

## Commits

Mensaje corto en imperativo, prefijo por componente cuando el cambio es local a uno:
```
backend: agrega validación de geocerca contextual en registro de marcación
frontend-campo: implementa cola offline con Dexie
infra: agrega healthcheck a servicio postgres
```

## Pull Requests

1. Abrir PR contra `main` desde la rama feature/fix.
2. CI debe pasar (build + tests del componente tocado — los workflows en `.github/workflows/` disparan solo sobre la carpeta que cambió).
3. Al menos 1 revisión de otro integrante del equipo antes de mergear.
4. Squash merge preferido para mantener `main` legible.

## Hitos de evaluación (tags)

Marcar con tag anotado el estado del repo en cada entrega del curso:
```
git tag -a v1-apf2 -m "Despliegue v1 en producción — APF2, semana 9"
git tag -a v2-apf3 -m "Versión final — APF3, semana 13"
```

## Supuesto

Este documento fija el flujo de trabajo del equipo; no es un requisito del curso. Ajustar si el curso pide evidenciar otro modelo de branching explícitamente en algún capítulo.
