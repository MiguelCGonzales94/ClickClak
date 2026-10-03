-- V2: completa el modelo físico sobre V1 (CLICKCLACK-59, cap. V 5.2).
-- Tres frentes: dominios cerrados, coherencia de datos e índices sobre llaves foráneas.
-- Todo es aditivo: no cambia columnas ni tipos, de modo que `ddl-auto: validate` sigue en verde.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- 1. Dominios cerrados: espejo en base de datos de los enums Java (@Enumerated STRING).
--    Si se agrega un valor al enum, hay que ampliar aquí con una migración nueva.
-- ---------------------------------------------------------------------------
ALTER TABLE marcacion
    ADD CONSTRAINT ck_marcacion_tipo_evento
        CHECK (tipo_evento IN ('ENTRADA', 'INICIO_REFRIGERIO', 'FIN_REFRIGERIO', 'SALIDA')),
    ADD CONSTRAINT ck_marcacion_estado_validacion
        CHECK (estado_validacion IN ('VALIDO', 'OBSERVADO', 'FUERA_DE_TOLERANCIA', 'SOSPECHOSO', 'SIN_ASIGNACION')),
    ADD CONSTRAINT ck_marcacion_precision_no_negativa
        CHECK (precision_metros IS NULL OR precision_metros >= 0),
    ADD CONSTRAINT ck_marcacion_distancia_no_negativa
        CHECK (distancia_metros IS NULL OR distancia_metros >= 0);

ALTER TABLE incidencia
    ADD CONSTRAINT ck_incidencia_tipo
        CHECK (tipo IN ('TARDANZA', 'AUSENCIA', 'OLVIDO_REGISTRO', 'PERMISO', 'JUSTIFICACION')),
    ADD CONSTRAINT ck_incidencia_estado
        CHECK (estado IN ('REGISTRADA', 'EN_REVISION', 'APROBADA', 'RECHAZADA', 'CERRADA')),
    -- Una incidencia revisada debe dejar rastro de quién y cuándo; una no revisada, no.
    ADD CONSTRAINT ck_incidencia_revision_completa
        CHECK ((revisado_por_id IS NULL) = (revisado_en IS NULL));

ALTER TABLE incidencia_historial
    ADD CONSTRAINT ck_incidencia_historial_estado_anterior
        CHECK (estado_anterior IS NULL
               OR estado_anterior IN ('REGISTRADA', 'EN_REVISION', 'APROBADA', 'RECHAZADA', 'CERRADA')),
    ADD CONSTRAINT ck_incidencia_historial_estado_nuevo
        CHECK (estado_nuevo IN ('REGISTRADA', 'EN_REVISION', 'APROBADA', 'RECHAZADA', 'CERRADA'));

ALTER TABLE bitacora_auditoria
    ADD CONSTRAINT ck_bitacora_accion
        CHECK (accion IN ('CREACION', 'MODIFICACION', 'ELIMINACION'));

ALTER TABLE rol
    ADD CONSTRAINT ck_rol_nombre
        CHECK (nombre IN ('COLABORADOR', 'SUPERVISOR', 'RRHH_ADMIN'));

-- ---------------------------------------------------------------------------
-- 2. Coherencia de datos.
-- ---------------------------------------------------------------------------
ALTER TABLE proyecto
    ADD CONSTRAINT ck_proyecto_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio);

ALTER TABLE ubicacion
    ADD CONSTRAINT ck_ubicacion_radio_positivo CHECK (radio_tolerancia_metros > 0);

ALTER TABLE horario
    ADD CONSTRAINT ck_horario_jornada CHECK (hora_fin > hora_inicio),
    ADD CONSTRAINT ck_horario_tolerancia CHECK (tolerancia_minutos >= 0),
    -- El refrigerio se define completo o no se define, y cae dentro de la jornada.
    ADD CONSTRAINT ck_horario_refrigerio
        CHECK ((hora_inicio_refrigerio IS NULL AND hora_fin_refrigerio IS NULL)
               OR (hora_inicio_refrigerio IS NOT NULL
                   AND hora_fin_refrigerio IS NOT NULL
                   AND hora_inicio_refrigerio < hora_fin_refrigerio
                   AND hora_inicio_refrigerio >= hora_inicio
                   AND hora_fin_refrigerio <= hora_fin));

ALTER TABLE asignacion
    ADD CONSTRAINT ck_asignacion_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    -- Último respaldo de HU08 (el servicio ya detecta el conflicto): un colaborador no puede
    -- tener dos asignaciones activas con fechas solapadas. Rango inclusivo en ambos extremos
    -- y fecha_fin nula = sin fin, igual que AsignacionService.seSuperponen.
    ADD CONSTRAINT ex_asignacion_sin_solapamiento
        EXCLUDE USING gist (usuario_id WITH =, daterange(fecha_inicio, fecha_fin, '[]') WITH &&)
        WHERE (activo);

ALTER TABLE dispositivo
    ADD CONSTRAINT ck_dispositivo_contador CHECK (contador_firma >= 0);

-- ---------------------------------------------------------------------------
-- 3. Índices sobre llaves foráneas que V1 no cubrió (joins, borrados y filtros del panel).
-- ---------------------------------------------------------------------------
CREATE INDEX idx_usuario_rol ON usuario(rol_id);
CREATE INDEX idx_asignacion_proyecto ON asignacion(proyecto_id);
CREATE INDEX idx_asignacion_ubicacion ON asignacion(ubicacion_id);
CREATE INDEX idx_asignacion_horario ON asignacion(horario_id);
CREATE INDEX idx_marcacion_asignacion ON marcacion(asignacion_id);
CREATE INDEX idx_marcacion_dispositivo ON marcacion(dispositivo_id);
CREATE INDEX idx_incidencia_marcacion ON incidencia(marcacion_id);
CREATE INDEX idx_incidencia_creado_por ON incidencia(creado_por_id);
CREATE INDEX idx_incidencia_revisado_por ON incidencia(revisado_por_id);
-- Bandeja del supervisor: incidencias por estado y fecha del evento.
CREATE INDEX idx_incidencia_estado_fecha ON incidencia(estado, fecha_evento);
CREATE INDEX idx_incidencia_historial_usuario ON incidencia_historial(usuario_id);
CREATE INDEX idx_bitacora_usuario_fecha ON bitacora_auditoria(usuario_id, creado_en);
