-- HU04: estado de cuenta y correo sin distinción de mayúsculas.
--
-- 1. Columnas nuevas: clave temporal pendiente de cambio, y fecha/motivo de la baja.
-- 2. El correo pasa a ser único sin distinguir mayúsculas. Antes de normalizar se comprueba que
--    no haya correos que solo difieran en mayúsculas: si los hay, la migración se detiene con
--    un mensaje claro en vez de elegir cuál conservar (decisión que le toca a RRHH).

ALTER TABLE usuario
    ADD COLUMN debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN desactivado_en TIMESTAMPTZ,
    ADD COLUMN motivo_baja VARCHAR(255);

DO $$
DECLARE
    duplicados TEXT;
BEGIN
    SELECT string_agg(correo_minusculas, ', ')
      INTO duplicados
      FROM (
            SELECT lower(correo) AS correo_minusculas
              FROM usuario
             GROUP BY lower(correo)
            HAVING count(*) > 1
           ) repetidos;

    IF duplicados IS NOT NULL THEN
        RAISE EXCEPTION 'V3 detenida: hay usuarios con el mismo correo salvo por mayúsculas (%). Unificarlos a mano y reintentar.', duplicados;
    END IF;
END $$;

UPDATE usuario SET correo = lower(correo) WHERE correo <> lower(correo);

CREATE UNIQUE INDEX uk_usuario_correo_minusculas ON usuario (lower(correo));
