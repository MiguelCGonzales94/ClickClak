Prueba E2E de marcación · http://localhost:8081 · sufijo de datos 261003015838 · usuarios de prueba con rol SUPERVISOR

| Caso | Descripción | Esperado | Obtenido | Resultado |
|---|---|---|---|---|
| E1 | Ingreso puntual dentro del radio (precisión 10 m): se registra y es VALIDO | 201 / VALIDO | 201 / VALIDO | CORRECTO |
| E1b | Un ingreso puntual no genera incidencia de tardanza | 0 tardanzas | 0 tardanzas | CORRECTO |
| E2 | Reenvío del mismo uuid con datos alterados: devuelve la marcación original, sin duplicar ni sobrescribir | mismo id 22 y latitud original | id 22 y latitud original | CORRECTO |
| E2b | El uuid repetido no crea una segunda fila en la base | 1 fila(s) | 1 fila(s) | CORRECTO |
| E3 | Salida a 1 km de la sede: FUERA_DE_TOLERANCIA | 201 / FUERA_DE_TOLERANCIA | 201 / FUERA_DE_TOLERANCIA | CORRECTO |
| E4 | Precisión reportada de 800 m (mayor que 500 m): SOSPECHOSO | 201 / SOSPECHOSO | 201 / SOSPECHOSO | CORRECTO |
| E5 | A 160 m con precisión de 50 m y radio de 150 m (ambiguo por el error del GPS): OBSERVADO | 201 / OBSERVADO | 201 / OBSERVADO | CORRECTO |
| E6 | Ingreso con 105 minutos de retraso (horario 08:00, tolerancia 10): se registra | 201 | 201 | CORRECTO |
| E6b | Se generó una incidencia TARDANZA automática en estado REGISTRADA | 1 tardanza REGISTRADA | 1 tardanza(s), estados ['REGISTRADA'] | CORRECTO |
| E6c | La incidencia automática nace con una fila de historial | 1 fila de historial | 1 fila(s) | CORRECTO |
| E6d | ...y una fila de auditoría con el sistema como autor (sin usuario) | 1 fila | 1 fila(s) | CORRECTO |
| E7 | Hora del evento 30 minutos en el futuro: se rechaza | 400 | 400 | CORRECTO |
| E8 | Colaborador sin asignación vigente: se registra con SIN_ASIGNACION | 201 / SIN_ASIGNACION | 201 / SIN_ASIGNACION | CORRECTO |
| E9 | Marcar con el dispositivo de otro colaborador: se rechaza | 403 | 403 | CORRECTO |
| E10 | Marcar a nombre de otro usuario alterando el cuerpo: se rechaza | 403 | 403 | CORRECTO |
| E11 | Marcar sin sesión: se rechaza | 401 | 401 | CORRECTO |

Resumen: 16 de 16 casos correctos.
