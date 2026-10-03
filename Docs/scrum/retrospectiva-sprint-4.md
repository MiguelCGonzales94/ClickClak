# Retrospectiva del Sprint 4: datos de partida

Datos objetivos de Jira y de GitHub para la retrospectiva del Sprint 4 (CLICKCLACK-46), que va en el informe del APF2.

**Este documento reúne datos; no contiene las conclusiones del equipo.** Qué salió bien, qué salió mal y qué se cambia lo decide el equipo en la reunión; la sección 8 queda en blanco para eso. Los datos se leyeron el 3-oct-2026.

Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. El sprint

| Dato | Valor |
|---|---|
| Nombre en Jira | CLICKCLA Sprint 4 (estado: activo) |
| Fechas | 28-sep-2026 al 11-oct-2026 (semanas 8 y 9) |
| Meta | Despliegue v1 en producción para el APF2: incidencias, seguridad web, VM con TLS, DER/UML y pruebas con evidencia |
| Historias en el sprint | 27 |
| Estado en Jira | 0 en "Listo", 11 en "En curso", 16 en "Por hacer" |
| Historias con responsable | 3 de 27 |
| Historias con estimación | 0 de 27 (en este proyecto solo las historias terminadas tienen puntos) |

**Nota.** El sprint contiene historias etiquetadas `sprint-3` y `sprint-4`: en Jira no se llegó a abrir un Sprint 3 aparte, y sus pendientes se arrastraron al Sprint 4. El Sprint 3 del sílabo (14 al 27 de septiembre) no tiene registro propio.

## 2. Meta del sprint frente a lo entregado

| Parte de la meta | Historias de Jira | Entregables en GitHub (PR) | Estado en GitHub | Estado en Jira |
|---|---|---|---|---|
| Incidencias | 55 | #4 backend, #10 panel administrativo | Abiertos, sin revisión | 55 en curso |
| Seguridad web | 57 | #7 backend, #8 proxy | Abiertos, sin revisión | 57 en curso |
| VM con TLS | 61, 8, 67 | #5 VM, #6 despliegue | Abiertos, sin revisión | 61 y 8 en curso; 67 por hacer |
| DER y UML | 62, 63, 18 | #3 DER, #11 UML, #12 BPM | #3 y #11 abiertos; #12 fusionado en una rama de trabajo, no en `main` | 62 y 18 en curso; 63 por hacer |
| Pruebas con evidencia | 29, 64, 65 | #14 plan y resultados, #13 medición de Java | Abiertos, sin revisión | 29, 64 y 65 por hacer |
| Réplica de la base | 60, 66 | #9 | Abierto, sin revisión | 60 y 66 en curso |

**Lectura de los datos.** Los artefactos de la meta existen, pero **ninguno está en `main`** y **Jira no refleja el avance**: historias con PR terminado siguen "Por hacer" o "En curso". En la VM hay un despliegue funcionando con certificado autofirmado, construido a partir de una integración local de ramas. El Let's Encrypt (CLICKCLACK-67) sigue pendiente.

## 3. Actividad en GitHub

Repositorio `MiguelCGonzales94/ClickClak`, todas las ramas.

| Dato | Valor |
|---|---|
| Commits (sin fusiones) desde el inicio | 38 |
| Primer commit | 9-sep-2026 (commit inicial del monorepo) |
| Pull requests abiertos o fusionados | 14 (13 abiertos y 1 fusionado) |
| Revisiones o comentarios en los PR leídos (#1, #4 y #10) | 0 |

Commits por día:

| Día | Commits |
|---|---|
| 9-sep | 3 |
| 10-sep | 1 |
| 2-oct | 11 |
| 3-oct | 23 |

Commits desde el 28-sep por componente: infra 12, backend 10, docs 9, frontend-admin 2, chore 1. Los 34 commits del 2 y 3 de octubre son el 89 % del total.

Tamaño de los PR (líneas agregadas y quitadas): #1 +1237/−177, #2 +181/−5, #3 +756, #4 +1251/−22, #5 +201, #6 +469/−3, #7 +966/−55, #8 +160/−26, #9 +433/−3, #10 +1509/−5, #11 +979, #12 +536, #13 +163, #14 +543.

## 4. Velocidad histórica en Jira

Solo las historias terminadas tienen puntos de historia.

| Sprint | Historias terminadas | Puntos |
|---|---|---|
| Sprint 1 (10 al 17 de agosto) | 6 | 30 |
| Sprint 2 (17 de agosto al 14 de septiembre) | 7 | 41 |
| Sprint 3 y Sprint 4 | 0 | Sin estimar |

**Con estos datos no se puede calcular la velocidad del Sprint 4**: no hay historias estimadas ni terminadas.

**Fiabilidad de los datos de Jira.** Las historias del Sprint 1 se marcaron como terminadas con segundos de diferencia el 9-sep a las 17:00, y las del Sprint 2 el 11-sep a las 11:26. El Sprint 1 se cerró en Jira el 9-sep aunque terminaba el 17-ago, y el Sprint 2 se cerró el 2-oct (noche, hora de Lima) aunque terminaba el 14-sep. Es una carga retroactiva, no un registro del avance diario: **un burndown construido con estas fechas no sería fiable**.

## 5. Responsables registrados

En Jira, historias terminadas por responsable (Sprint 1 y 2):

| Responsable en Jira | Historias | Puntos |
|---|---|---|
| Miguel Antonio Cuadros Gonzales | 4 | 31 |
| Antony Villanueva Fritz | 5 | 16 |
| U23257478 | 3 | 19 |
| U21314615 | 1 | 5 |
| **Total** | **13** | **71** |

Dos de los cuatro responsables aparecen como códigos y no como nombres. **Falta confirmar a quién corresponde cada uno.**

En GitHub, los 38 commits y los 14 PR provienen de una sola cuenta.

> **Cómo leer estos datos.** GitHub solo ve el trabajo que pasa por este repositorio. Otras personas del equipo pueden haber trabajado en documentos, diseño o investigación fuera de él, y eso no queda en estas cifras. **Recomendación:** que cada integrante aporte en la retrospectiva su propio recuento de lo que hizo, y que el equipo decida cómo presentar la participación en el informe.

## 6. Observaciones de proceso (hechos)

| Observación | Dato |
|---|---|
| Revisión por pares | `CONTRIBUTING.md` pide al menos una revisión por PR. Los 14 PR no tienen ninguna. El #12 se fusionó el 3-oct a las 01:45 (hora de Lima) sin revisión. |
| Integración continua | No hay workflows en el repositorio (CLICKCLACK-9 por hacer). `CONTRIBUTING.md` afirma lo contrario. |
| Protección de `main` | `main` no está protegida en GitHub. |
| Integración de ramas | Hay 13 PR abiertos a la vez, en dos cadenas encadenadas. Un simulacro local de la fusión no encontró conflictos, y el resultado integrado pasó las 178 pruebas del backend y la compilación de ambos frontends. |
| Concentración del trabajo | Desde el inicio del sprint (28-sep) todos los commits (34) son del 2 y 3 de octubre, los dos últimos de los seis días transcurridos. Antes del sprint, el repositorio solo tiene 4 commits (9 y 10 de septiembre). |
| Registro del avance en Jira | Las historias terminadas no están en "Listo"; los estados se actualizan por lotes. |
| Impedimentos | No hay registro de impedimentos ni de horas dedicadas. No se pueden analizar con estos datos. |
| Hechos favorables | Plan de pruebas con evidencia y brechas declaradas; 178 pruebas automatizadas sin fallos; documentación técnica que declara lo no probado; despliegue funcionando en la VM. |

## 7. Pendientes de Jira (propuesta, sin aplicar)

Las historias con trabajo hecho no se han movido de estado. Se propone actualizarlas **cuando los PR se fusionen** y no antes, para que "Listo" signifique "en `main`".

| Historia | Estado actual | Propuesta |
|---|---|---|
| 55 incidencias | En curso | Listo al fusionar #4 y #10 |
| 57 seguridad web | En curso | Listo al fusionar #7 y #8 |
| 59 migración V2 | En curso | Listo al fusionar #3 |
| 62 DER | En curso | Listo al fusionar #3 |
| 63 UML | Por hacer | Listo al fusionar #11 |
| 18 BPM | En curso | Listo al fusionar #11 (que ya contiene el #12) |
| 65 medición de Java | Por hacer | Listo al fusionar #13 y repetir la medición |
| 29 y 64 pruebas | Por hacer | En curso: falta la evidencia en la VM y las pruebas de carga |
| 60 y 66 réplica | En curso | Listo al fusionar #9 |
| 8 despliegue v1 | En curso | Listo tras desplegar desde `main`, con Let's Encrypt y la etiqueta `v1-apf2` |

## 8. Acuerdos del equipo (a completar en la reunión)

| Qué salió bien | Qué salió mal | Qué cambiamos en el Sprint 5 | Responsable |
|---|---|---|---|
| | | | |

**Preguntas sugeridas para la reunión**, a partir de los datos de las secciones 3 a 6:

1. ¿Por qué casi todo el trabajo se concentró al final del sprint, y qué lo habría repartido?
2. ¿Cómo hacemos que Jira refleje el avance mientras ocurre y no después?
3. ¿Qué haremos con la revisión de PR y la integración continua que `CONTRIBUTING.md` exige y hoy no existen?
4. ¿Cómo se reparte y se registra el trabajo de cada integrante, también el que no pasa por GitHub?
5. ¿Qué entra en el Sprint 5, con historias estimadas desde el inicio?

## 9. Fuentes y límites

- **Jira:** proyecto CLICKCLACK, consulta del sprint activo, historias terminadas y detalle de la historia 8. No se leyeron los historiales de cambios de estado.
- **GitHub:** `git log` de todas las ramas remotas y la API de PR. Solo se leyeron las revisiones y comentarios de los PR #1, #4 y #10.
- **No medido:** horas dedicadas, impedimentos, defectos escapados y tiempo de ciclo, porque no se registran.
