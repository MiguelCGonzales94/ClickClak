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

Medido sobre `main` (commit `4d5d149`, con los pull requests de código ya fusionados) el 3-oct-2026.

| Zona | Java | Otros |
|---|---|---|
| `backend/src/main` (producción) | 3373 | 268 (SQL y YAML) |
| `backend/src/test` (pruebas) | 2834 | 0 |
| `backend/pom.xml` | 0 | 125 |
| Frontends `src` (producción) | 0 | 3834 (TypeScript, CSS y JavaScript) |
| Frontends `src` (pruebas) | 0 | 124 |

| Definición | Java | Total | % Java |
|---|---|---|---|
| **A. Back-end, código de producción (`src/main`)** | 3373 | 3641 | **92,6 %** |
| B. Back-end completo (producción, pruebas y `pom.xml`) | 6207 | 6600 | 94,0 % |
| C. Todo el código de aplicación, sin pruebas (back-end y frontends) | 3373 | 7475 | 45,1 % |
| D. Todo el código de aplicación, con pruebas | 6207 | 10433 | 59,5 % |

**Medición anterior.** Antes de fusionar los pull requests de código, sobre la rama de documentación, la definición A daba 92,5 % (3178 de 3436 líneas) y la C, 43,7 %. La diferencia se debe al código del PR de seguridad del backend y a los demás cambios ya fusionados.

## 4. Lectura

- **Con el supuesto de la sección 1 (definiciones A y B), el requisito se cumple con amplio margen**: 92,6 % y 94,0 %. Lo que no es Java en el back-end son las migraciones y la semilla de desarrollo en SQL, la configuración en YAML y el `pom.xml`.
- **Si alguien midiera todo el proyecto y no solo el back-end, la cifra cambia**: sin contar pruebas (definición C) es 45,1 %, por debajo del 50 %; contando pruebas (D) es 59,5 %. La consigna habla del back-end, pero conviene que el equipo lo tenga presente.
- **Recomendación.** Sustentar con la definición A, citar la frase de la consigna, y **confirmar con el docente** que la lectura es solo el back-end. Es una pregunta de un minuto y elimina el riesgo de la definición C.

## 5. Límites de la medición

- Son líneas de código, no bytes ni puntos de función. El recuento de comentarios es una heurística simple: no distingue una cadena de texto que contenga `//` o `/*`.
- Mide `main` en el commit indicado. Si el código cambia después, la cifra debe repetirse.
- La infraestructura (`infra/`, scripts y configuración de Nginx) y el prototipo estático no se cuentan como código de aplicación.
- La semilla de desarrollo (`R__semilla_desarrollo.sql`) cuenta como SQL del back-end, aunque solo se carga con el perfil de desarrollo.
- El código generado por herramientas no se contó; no se versiona.
- La cifra aproximada de la barra de lenguajes de GitHub (bytes, no líneas) que figuraba en una versión anterior de este documento no se volvió a calcular y se retiró.

## 6. Pendiente

Repetir `python Docs/tecnica/medir-java.py` antes de etiquetar `v1-apf2` si el código de `main` cambió desde el commit medido, y copiar la cifra final de la definición A al informe del APF2 (CLICKCLACK-30).
