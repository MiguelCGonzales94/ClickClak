-- HU08: un colaborador puede estar asignado a más de una sede al mismo tiempo.
--
-- Hasta V3 la base prohibía dos asignaciones activas solapadas del mismo colaborador, sin importar la
-- sede. Ahora lo que no puede repetirse es la MISMA sede en fechas solapadas: sería ambiguo cuál de las
-- dos filas manda (turno, fechas). Sedes distintas pueden coexistir; al marcar, el servidor valida
-- contra la más cercana de las vigentes (ver MarcacionService).
--
-- Relajar una restricción no puede fallar por los datos existentes: todo lo que cumplía la regla
-- anterior cumple la nueva.

ALTER TABLE asignacion
    DROP CONSTRAINT ex_asignacion_sin_solapamiento;

ALTER TABLE asignacion
    ADD CONSTRAINT ex_asignacion_misma_sede_sin_solapamiento
        EXCLUDE USING gist (
            usuario_id WITH =,
            ubicacion_id WITH =,
            daterange(fecha_inicio, fecha_fin, '[]') WITH &&
        )
        WHERE (activo);
