-- =====================================================================================================================
-- V4 · Gestión de expedientes: el expediente, sus partes, su bitácora de actuaciones y su historial de estados.
-- =====================================================================================================================

CREATE TABLE expediente (
    id                          BIGINT IDENTITY(1,1) NOT NULL,
    numero_interno              VARCHAR(20)    NOT NULL,
    caratula                    NVARCHAR(250)  NOT NULL,
    cliente_id                  BIGINT         NOT NULL,
    abogado_responsable_id      BIGINT         NOT NULL,
    materia_id                  BIGINT         NOT NULL,
    tipo_proceso_id             BIGINT         NOT NULL,
    juzgado_id                  BIGINT         NULL,
    numero_expediente_judicial  VARCHAR(40)    NULL,
    anio_judicial               SMALLINT       NULL,
    estado_expediente_id        BIGINT         NOT NULL,
    fecha_inicio                DATE           NOT NULL,
    fecha_cierre                DATE           NULL,
    motivo_cierre               NVARCHAR(MAX)  NULL,
    descripcion                 NVARCHAR(MAX)  NULL,
    monto_reclamado             NUMERIC(15,2)  NULL,
    moneda                      CHAR(3)        NOT NULL CONSTRAINT df_expediente_moneda DEFAULT 'PYG',
    creado_en                   DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_expediente_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por                  BIGINT         NULL,
    modificado_en               DATETIMEOFFSET(3) NULL,
    modificado_por              BIGINT         NULL,
    CONSTRAINT pk_expediente PRIMARY KEY (id),
    CONSTRAINT uq_expediente_numero_interno UNIQUE (numero_interno),
    CONSTRAINT ck_expediente_fecha_cierre CHECK (fecha_cierre IS NULL OR fecha_cierre >= fecha_inicio),
    -- Fecha y motivo de cierre van juntos. Que el estado sea de cierre (estado_expediente.es_cierre) lo controla la aplicación.
    CONSTRAINT ck_expediente_cierre CHECK ((fecha_cierre IS NULL AND motivo_cierre IS NULL)
                                        OR (fecha_cierre IS NOT NULL AND motivo_cierre IS NOT NULL)),
    CONSTRAINT ck_expediente_monto CHECK (monto_reclamado IS NULL OR monto_reclamado >= 0),
    CONSTRAINT ck_expediente_anio_judicial CHECK (anio_judicial IS NULL OR anio_judicial BETWEEN 1900 AND 2100),
    CONSTRAINT fk_expediente_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_expediente_abogado_responsable FOREIGN KEY (abogado_responsable_id) REFERENCES usuario (id),
    CONSTRAINT fk_expediente_materia FOREIGN KEY (materia_id) REFERENCES materia (id),
    -- El tipo de proceso debe pertenecer a la materia elegida.
    CONSTRAINT fk_expediente_tipo_proceso FOREIGN KEY (tipo_proceso_id, materia_id) REFERENCES tipo_proceso (id, materia_id),
    CONSTRAINT fk_expediente_juzgado FOREIGN KEY (juzgado_id) REFERENCES juzgado (id),
    CONSTRAINT fk_expediente_estado FOREIGN KEY (estado_expediente_id) REFERENCES estado_expediente (id),
    CONSTRAINT fk_expediente_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_expediente_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
CREATE INDEX ix_expediente_cliente_id ON expediente (cliente_id);
CREATE INDEX ix_expediente_abogado_responsable_id ON expediente (abogado_responsable_id);
CREATE INDEX ix_expediente_materia_id ON expediente (materia_id);
CREATE INDEX ix_expediente_tipo_proceso_id ON expediente (tipo_proceso_id);
CREATE INDEX ix_expediente_juzgado_id ON expediente (juzgado_id);
CREATE INDEX ix_expediente_estado_expediente_id ON expediente (estado_expediente_id);
CREATE INDEX ix_expediente_fecha_inicio ON expediente (fecha_inicio);
CREATE INDEX ix_expediente_creado_por ON expediente (creado_por);
CREATE INDEX ix_expediente_modificado_por ON expediente (modificado_por);

CREATE TABLE expediente_parte (
    id                BIGINT IDENTITY(1,1) NOT NULL,
    expediente_id     BIGINT         NOT NULL,
    rol_parte_id      BIGINT         NOT NULL,
    nombre            NVARCHAR(150)  NOT NULL,
    numero_documento  VARCHAR(20)    NULL,
    abogado           NVARCHAR(120)  NULL,
    contacto          NVARCHAR(150)  NULL,
    observaciones     NVARCHAR(MAX)  NULL,
    creado_en         DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_expediente_parte_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por        BIGINT         NULL,
    modificado_en     DATETIMEOFFSET(3) NULL,
    modificado_por    BIGINT         NULL,
    CONSTRAINT pk_expediente_parte PRIMARY KEY (id),
    CONSTRAINT fk_expediente_parte_expediente FOREIGN KEY (expediente_id) REFERENCES expediente (id),
    CONSTRAINT fk_expediente_parte_rol_parte FOREIGN KEY (rol_parte_id) REFERENCES rol_parte (id),
    CONSTRAINT fk_expediente_parte_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_expediente_parte_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
CREATE INDEX ix_expediente_parte_expediente_id ON expediente_parte (expediente_id);
CREATE INDEX ix_expediente_parte_rol_parte_id ON expediente_parte (rol_parte_id);
CREATE INDEX ix_expediente_parte_creado_por ON expediente_parte (creado_por);
CREATE INDEX ix_expediente_parte_modificado_por ON expediente_parte (modificado_por);

CREATE TABLE actuacion (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    expediente_id      BIGINT         NOT NULL,
    tipo_actuacion_id  BIGINT         NOT NULL,
    fecha              DATE           NOT NULL,
    descripcion        NVARCHAR(MAX)  NOT NULL,
    usuario_id         BIGINT         NOT NULL,
    creado_en          DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_actuacion_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por         BIGINT         NULL,
    modificado_en      DATETIMEOFFSET(3) NULL,
    modificado_por     BIGINT         NULL,
    CONSTRAINT pk_actuacion PRIMARY KEY (id),
    CONSTRAINT fk_actuacion_expediente FOREIGN KEY (expediente_id) REFERENCES expediente (id),
    CONSTRAINT fk_actuacion_tipo_actuacion FOREIGN KEY (tipo_actuacion_id) REFERENCES tipo_actuacion (id),
    CONSTRAINT fk_actuacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_actuacion_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_actuacion_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
-- La bitácora se lee por expediente en orden de fecha.
CREATE INDEX ix_actuacion_expediente_fecha ON actuacion (expediente_id, fecha);
CREATE INDEX ix_actuacion_tipo_actuacion_id ON actuacion (tipo_actuacion_id);
CREATE INDEX ix_actuacion_usuario_id ON actuacion (usuario_id);
CREATE INDEX ix_actuacion_creado_por ON actuacion (creado_por);
CREATE INDEX ix_actuacion_modificado_por ON actuacion (modificado_por);

CREATE TABLE expediente_estado_historial (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    expediente_id       BIGINT         NOT NULL,
    estado_anterior_id  BIGINT         NULL,
    estado_nuevo_id     BIGINT         NOT NULL,
    fecha               DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_expediente_estado_historial_fecha DEFAULT SYSUTCDATETIME(),
    usuario_id          BIGINT         NOT NULL,
    observacion         NVARCHAR(MAX)  NULL,
    CONSTRAINT pk_expediente_estado_historial PRIMARY KEY (id),
    CONSTRAINT fk_expediente_estado_historial_expediente FOREIGN KEY (expediente_id) REFERENCES expediente (id),
    CONSTRAINT fk_expediente_estado_historial_anterior FOREIGN KEY (estado_anterior_id) REFERENCES estado_expediente (id),
    CONSTRAINT fk_expediente_estado_historial_nuevo FOREIGN KEY (estado_nuevo_id) REFERENCES estado_expediente (id),
    CONSTRAINT fk_expediente_estado_historial_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
CREATE INDEX ix_expediente_estado_historial_expediente ON expediente_estado_historial (expediente_id, fecha);
CREATE INDEX ix_expediente_estado_historial_anterior ON expediente_estado_historial (estado_anterior_id);
CREATE INDEX ix_expediente_estado_historial_nuevo ON expediente_estado_historial (estado_nuevo_id);
CREATE INDEX ix_expediente_estado_historial_usuario ON expediente_estado_historial (usuario_id);
