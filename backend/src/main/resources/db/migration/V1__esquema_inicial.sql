CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE rol (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO rol (nombre) VALUES ('COLABORADOR'), ('SUPERVISOR'), ('RRHH_ADMIN');

CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    tipo_documento VARCHAR(20) NOT NULL,
    numero_documento VARCHAR(20) NOT NULL,
    correo VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255),
    rol_id BIGINT NOT NULL REFERENCES rol(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_usuario_correo UNIQUE (correo),
    CONSTRAINT uk_usuario_documento UNIQUE (tipo_documento, numero_documento)
);

-- Credenciales WebAuthn del dispositivo autorizado. No se almacena ninguna plantilla
-- biométrica: la biometría la verifica el sistema operativo del dispositivo, aquí solo
-- se guarda la clave pública de la credencial para validar la firma en cada marcación.
CREATE TABLE dispositivo (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    credential_id VARCHAR(255) NOT NULL,
    clave_publica TEXT NOT NULL,
    contador_firma BIGINT NOT NULL DEFAULT 0,
    nombre_dispositivo VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_dispositivo_credential UNIQUE (credential_id)
);

CREATE INDEX idx_dispositivo_usuario ON dispositivo(usuario_id);

CREATE TABLE proyecto (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    cliente VARCHAR(150) NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

-- Un proyecto puede tener varias sedes/frentes de trabajo (ubicaciones). El radio de
-- tolerancia es el margen que usa el motor de validación contextual (OE3) para no
-- emitir juicios binarios ante la imprecisión propia de la geolocalización.
CREATE TABLE ubicacion (
    id BIGSERIAL PRIMARY KEY,
    proyecto_id BIGINT NOT NULL REFERENCES proyecto(id),
    nombre VARCHAR(150) NOT NULL,
    direccion_referencia VARCHAR(255),
    geom geography(Point, 4326) NOT NULL,
    radio_tolerancia_metros INTEGER NOT NULL DEFAULT 150,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_ubicacion_proyecto ON ubicacion(proyecto_id);
CREATE INDEX idx_ubicacion_geom ON ubicacion USING GIST (geom);

CREATE TABLE horario (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    hora_inicio_refrigerio TIME,
    hora_fin_refrigerio TIME,
    tolerancia_minutos INTEGER NOT NULL DEFAULT 10,
    dias_semana VARCHAR(20) NOT NULL DEFAULT 'L,M,X,J,V'
);

-- Vínculo vigente colaborador-proyecto-ubicación-horario. Se versiona en el tiempo
-- (fecha_inicio/fecha_fin) porque el personal deslocalizado cambia de proyecto; la
-- marcación referencia la asignación vigente al momento del evento, no la actual.
CREATE TABLE asignacion (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    proyecto_id BIGINT NOT NULL REFERENCES proyecto(id),
    ubicacion_id BIGINT NOT NULL REFERENCES ubicacion(id),
    horario_id BIGINT NOT NULL REFERENCES horario(id),
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_asignacion_usuario ON asignacion(usuario_id);

-- uuid_cliente es generado en el dispositivo al capturar el evento offline (OE4): permite
-- deduplicar reintentos de sincronización sin depender del id autogenerado del servidor.
-- hora_evento (cuándo ocurrió) se diferencia explícitamente de hora_sincronizacion (cuándo
-- llegó al servidor), tal como exige el objetivo de registro sin conexión.
CREATE TABLE marcacion (
    id BIGSERIAL PRIMARY KEY,
    uuid_cliente UUID NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    asignacion_id BIGINT REFERENCES asignacion(id),
    dispositivo_id BIGINT NOT NULL REFERENCES dispositivo(id),
    tipo_evento VARCHAR(30) NOT NULL,
    hora_evento TIMESTAMPTZ NOT NULL,
    hora_sincronizacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    geom geography(Point, 4326) NOT NULL,
    precision_metros NUMERIC(10, 2),
    distancia_metros NUMERIC(10, 2),
    estado_validacion VARCHAR(30) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_marcacion_uuid_cliente UNIQUE (uuid_cliente)
);

CREATE INDEX idx_marcacion_usuario_hora ON marcacion(usuario_id, hora_evento);
CREATE INDEX idx_marcacion_geom ON marcacion USING GIST (geom);

CREATE TABLE incidencia (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    marcacion_id BIGINT REFERENCES marcacion(id),
    tipo VARCHAR(30) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'REGISTRADA',
    fecha_evento DATE NOT NULL,
    descripcion TEXT NOT NULL,
    creado_por_id BIGINT NOT NULL REFERENCES usuario(id),
    revisado_por_id BIGINT REFERENCES usuario(id),
    comentario_revision TEXT,
    revisado_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_incidencia_usuario ON incidencia(usuario_id);
CREATE INDEX idx_incidencia_estado ON incidencia(estado);

-- Sustento documental de la incidencia (OE5). Solo se guarda la ruta; el archivo
-- vive en el volumen Docker del backend, no en la base de datos.
CREATE TABLE incidencia_adjunto (
    id BIGSERIAL PRIMARY KEY,
    incidencia_id BIGINT NOT NULL REFERENCES incidencia(id),
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_almacenamiento VARCHAR(500) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    subido_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_incidencia_adjunto_incidencia ON incidencia_adjunto(incidencia_id);

-- Bitácora del flujo Registrada -> En revisión -> Aprobada/Rechazada -> Cerrada.
CREATE TABLE incidencia_historial (
    id BIGSERIAL PRIMARY KEY,
    incidencia_id BIGINT NOT NULL REFERENCES incidencia(id),
    estado_anterior VARCHAR(30),
    estado_nuevo VARCHAR(30) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    comentario TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_incidencia_historial_incidencia ON incidencia_historial(incidencia_id);

-- Bitácora de auditoría general (OE6): quién modificó qué entidad y con qué valores.
CREATE TABLE bitacora_auditoria (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuario(id),
    entidad VARCHAR(100) NOT NULL,
    entidad_id BIGINT NOT NULL,
    accion VARCHAR(30) NOT NULL,
    valores_anteriores JSONB,
    valores_nuevos JSONB,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bitacora_entidad ON bitacora_auditoria(entidad, entidad_id);
