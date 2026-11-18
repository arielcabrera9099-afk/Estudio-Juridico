-- =====================================================================================================================
-- V6 · Control documental: archivos adjuntos por expediente. El archivo vive fuera de la base; aquí van sus datos.
-- =====================================================================================================================

CREATE TABLE documento (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    expediente_id        BIGINT         NOT NULL,
    tipo_documento_id    BIGINT         NOT NULL,
    nombre               NVARCHAR(200)  NOT NULL,
    descripcion          NVARCHAR(MAX)  NULL,
    fecha_documento      DATE           NULL,
    nombre_archivo       NVARCHAR(255)  NOT NULL,
    ruta_almacenamiento  NVARCHAR(500)  NOT NULL,
    tipo_mime            VARCHAR(100)   NOT NULL,
    tamano_bytes         BIGINT         NOT NULL,
    hash_sha256          CHAR(64)       NOT NULL,
    actuacion_id         BIGINT         NULL,
    eliminado_en         DATETIMEOFFSET(3) NULL,
    creado_en            DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_documento_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por           BIGINT         NULL,
    modificado_en        DATETIMEOFFSET(3) NULL,
    modificado_por       BIGINT         NULL,
    CONSTRAINT pk_documento PRIMARY KEY (id),
    CONSTRAINT uq_documento_ruta_almacenamiento UNIQUE (ruta_almacenamiento),
    CONSTRAINT ck_documento_tamano CHECK (tamano_bytes >= 0),
    CONSTRAINT fk_documento_expediente FOREIGN KEY (expediente_id) REFERENCES expediente (id),
    CONSTRAINT fk_documento_tipo_documento FOREIGN KEY (tipo_documento_id) REFERENCES tipo_documento (id),
    CONSTRAINT fk_documento_actuacion FOREIGN KEY (actuacion_id) REFERENCES actuacion (id),
    CONSTRAINT fk_documento_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_documento_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
-- Buscador: por expediente, tipo, fecha y nombre (la intercalación de la base ignora mayúsculas y acentos).
CREATE INDEX ix_documento_expediente_id ON documento (expediente_id);
CREATE INDEX ix_documento_tipo_documento_id ON documento (tipo_documento_id);
CREATE INDEX ix_documento_fecha_documento ON documento (fecha_documento) WHERE eliminado_en IS NULL;
CREATE INDEX ix_documento_nombre ON documento (nombre) WHERE eliminado_en IS NULL;
-- Detección de duplicados y alteraciones.
CREATE INDEX ix_documento_hash_sha256 ON documento (hash_sha256);
CREATE INDEX ix_documento_actuacion_id ON documento (actuacion_id);
CREATE INDEX ix_documento_creado_por ON documento (creado_por);
CREATE INDEX ix_documento_modificado_por ON documento (modificado_por);
