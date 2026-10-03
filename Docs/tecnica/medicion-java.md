# Medición del porcentaje de código Java

Medición del requisito del curso de que el back-end sea al menos 50 % código Java (CLICKCLACK-65).

Las etiquetas siguen la convención del proyecto: **Requerimiento**, **Supuesto**, **Recomendación**, **Decisión**.

## 1. Requerimiento y definición

- **Requerimiento.** La consigna del APF2 pide implementar el núcleo de la solución (back-end) "asegurando que al menos el 50 % sea código Java".
- **Supuesto.** "Back-end" es el directorio `backend/`. La consigna habla del back-end, no del proyecto completo. Esta lectura no está confirmada por escrito con el docente.
- **Decisión (2-sep-2026).** El back-end es Spring Boot (Java 21) y se expone como API REST pura; los dos frontends (React y TypeScript) son aplicaciones aparte.

## 2. Método

Se cuentan **líneas de código**: sin líneas en blanco ni comentarios (de línea y de bloque) de los archivos versionados en git. Se excluyen el envoltorio generado de Maven (`mvnw`, `mvnw.cmd`) y los archivos de bloqueo de dependencias. Se cuentan Java, SQL, YAML, `pom.xml` y, para los frontends, TypeScript, CSS y JavaScript.

Se reproduce desde la raíz del repositorio con:

```bash
python Docs/tecnica/medir-java.py
```

El script es [medir-java.py](medir-java.py).

## 3. Resultado

Medido sobre la rama `feature/docs-bpm` el 3-oct-2026.

| Zona | Java | Otros |
|---|---|---|
| `backend/src/main` (producción) | 3178 | 258 (208 SQL y 50 YAML) |
| `backend/src/test` (pruebas) | 2432 | 0 |
| `backend/pom.xml` | 0 | 125 |
| Frontends `src` (producción) | 0 | 3834 (TypeScript, CSS y JavaScript) |
| Frontends `src` (pruebas) | 0 | 124 |

| Definición | Java | Total | % Java |
|---|---|---|---|
| **A. Back-end, código de producción (`src/main`)** | 3178 | 3436 | **92,5 %** |
| B. Back-end completo (producción, pruebas y `pom.xml`) | 5610 | 5993 | 93,6 % |
| C. Todo el código de aplicación, sin pruebas (back-end y frontends) | 3178 | 7270 | 43,7 % |
| D. Todo el código de aplicación, con pruebas | 5610 | 9826 | 57,1 % |

## 4. Lectura

- **Con el supuesto de la sección 1 (definiciones A y B), el requisito se cumple con amplio margen**: 92,5 % y 93,6 %. Lo que no es Java en el back-end son las migraciones y la semilla de desarrollo en SQL, la configuración en YAML y el `pom.xml`.
- **Si alguien midiera todo el proyecto y no solo el back-end, la cifra cambia**: sin contar pruebas (definición C) es 43,7 %, por debajo del 50 %; contando pruebas (D) es 57,1 %. La consigna habla del back-end, pero conviene que el equipo lo tenga presente.
- **Recomendación.** Sustentar con la definición A, citar la frase de la consigna, y **confirmar con el docente** que la lectura es solo el back-end. Es una pregunta de un minuto y elimina el riesgo de la definición C.
- Como referencia, la barra de lenguajes de GitHub (que cuenta bytes de todos los archivos reconocidos) daría aproximadamente 57,6 % de Java. Es un cálculo propio sobre los archivos versionados, **no la cifra oficial de GitHub**.

## 5. Límites de la medición

- Son líneas de código, no bytes ni puntos de función. El recuento de comentarios es una heurística simple: no distingue una cadena de texto que contenga `//` o `/*`.
- Mide solo lo que contiene esta rama. El PR #7 (seguridad del backend) aún no está fusionado y agrega Java (24 archivos, +839 y −55 líneas brutas), que no está incluido; al fusionarlo, la proporción de Java debería subir. Los PR #8 y #9 (proxy y réplica) no tocan Java. Los demás PR de infraestructura (#2, #5 y #6) no se revisaron en este punto.
- La infraestructura (`infra/`, scripts y configuración de Nginx) y el prototipo estático no se cuentan como código de aplicación.
- La semilla de desarrollo (`R__semilla_desarrollo.sql`) cuenta como SQL del back-end, aunque solo se carga con el perfil de desarrollo.
- El código generado por herramientas no se contó; no se versiona.

## 6. Pendiente

Repetir `python Docs/tecnica/medir-java.py` **tras fusionar los PR en `main`** y antes de etiquetar `v1-apf2`, y copiar la cifra final de la definición A al informe del APF2 (CLICKCLACK-30).
