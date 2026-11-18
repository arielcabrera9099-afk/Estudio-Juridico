-- =====================================================================================================================
-- V5 · Agenda legal: eventos (audiencias, vencimientos, reuniones, diligencias), recordatorios y notificaciones.
-- =====================================================================================================================

CREATE TABLE evento_agenda (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    tipo_evento_id  BIGINT         NOT NULL,
    expediente_id   BIGINT         NULL,
    titulo          NVARCHAR(150)  NOT NULL,
    descripcion     NVARCHAR(MAX)  NULL,
    fecha_inicio    DATETIMEOFFSET(3) NOT NULL,
    fecha_fin       DATETIMEOFFSET(3) NULL,
    todo_el_dia     BIT            NOT NULL CONSTRAINT df_evento_agenda_todo_el_dia DEFAULT 0,
    lugar           NVARCHAR(200)  NULL,
    juzgado_id      BIGINT         NULL,
    responsable_id  BIGINT         NOT NULL,
    estado          VARCHAR(15)    NOT NULL CONSTRAINT df_evento_agenda_estado DEFAULT 'PENDIENTE',
    resultado       NVARCHAR(MAX)  NULL,
    creado_en       DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_evento_agenda_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por      BIGINT         NULL,
    modificado_en   DATETIMEOFFSET(3) NULL,
    modificado_por  BIGINT         NULL,
    CONSTRAINT pk_evento_agenda PRIMARY KEY (id),
    CONSTRAINT ck_evento_agenda_estado CHECK (estado IN ('PENDIENTE', 'REALIZADO', 'SUSPENDIDO', 'CANCELADO')),
    CONSTRAINT ck_evento_agenda_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT fk_evento_agenda_tipo_evento FOREIGN KEY (tipo_evento_id) REFERENCES tipo_evento (id),
    CONSTRAINT fk_evento_agenda_expediente FOREIGN KEY (expediente_id) REFERENCES expediente (id),
    CONSTRAINT fk_evento_agenda_juzgado FOREIGN KEY (juzgado_id) REFERENCES juzgado (id),
    CONSTRAINT fk_evento_agenda_responsable FOREIGN KEY (responsable_id) REFERENCES usuario (id),
    CONSTRAINT fk_evento_agenda_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_evento_agenda_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
CREATE INDEX ix_evento_agenda_tipo_evento_id ON evento_agenda (tipo_evento_id);
CREATE INDEX ix_evento_agenda_expediente_id ON evento_agenda (expediente_id);
CREATE INDEX ix_evento_agenda_juzgado_id ON evento_agenda (juzgado_id);
CREATE INDEX ix_evento_agenda_responsable_fecha ON evento_agenda (responsable_id, fecha_inicio);
-- Calendario semanal y mensual: rango de fechas.
CREATE INDEX ix_evento_agenda_fecha_inicio ON evento_agenda (fecha_inicio) INCLUDE (estado, tipo_evento_id);
CREATE INDEX ix_evento_agenda_creado_por ON evento_agenda (creado_por);
CREATE INDEX ix_evento_agenda_modificado_por ON evento_agenda (modificado_por);

CREATE TABLE recordatorio (
    id                BIGINT IDENTITY(1,1) NOT NULL,
    evento_agenda_id  BIGINT         NOT NULL,
    usuario_id        BIGINT         NOT NULL,
    fecha_aviso       DATETIMEOFFSET(3) NOT NULL,
    canal             VARCHAR(10)    NOT NULL CONSTRAINT df_recordatorio_canal DEFAULT 'SISTEMA',
    estado            VARCHAR(10)    NOT NULL CONSTRAINT df_recordatorio_estado DEFAULT 'PENDIENTE',
    enviado_en        DATETIMEOFFSET(3) NULL,
    CONSTRAINT pk_recordatorio PRIMARY KEY (id),
    CONSTRAINT ck_recordatorio_canal CHECK (canal IN ('SISTEMA', 'EMAIL')),
    CONSTRAINT ck_recordatorio_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'ERROR', 'ANULADO')),
    CONSTRAINT fk_recordatorio_evento_agenda FOREIGN KEY (evento_agenda_id) REFERENCES evento_agenda (id),
    CONSTRAINT fk_recordatorio_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
CREATE INDEX ix_recordatorio_evento_agenda_id ON recordatorio (evento_agenda_id);
CREATE INDEX ix_recordatorio_usuario_id ON recordatorio (usuario_id);
-- La tarea programada busca los pendientes cuya fecha de aviso ya llegó.
CREATE INDEX ix_recordatorio_pendientes ON recordatorio (fecha_aviso) WHERE estado = 'PENDIENTE';

CREATE TABLE notificacion (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id       BIGINT         NOT NULL,
    titulo           NVARCHAR(150)  NOT NULL,
    mensaje          NVARCHAR(MAX)  NULL,
    tipo             VARCHAR(20)    NOT NULL,
    referencia_tipo  VARCHAR(30)    NULL,
    referencia_id    BIGINT         NULL,
    leida_en         DATETIMEOFFSET(3) NULL,
    creado_en        DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_notificacion_creado_en DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_notificacion PRIMARY KEY (id),
    CONSTRAINT ck_notificacion_tipo CHECK (tipo IN ('VENCIMIENTO', 'AUDIENCIA', 'SISTEMA')),
    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
-- La campana muestra las no leídas del usuario, las más nuevas primero.
CREATE INDEX ix_notificacion_usuario ON notificacion (usuario_id, creado_en) INCLUDE (leida_en);
