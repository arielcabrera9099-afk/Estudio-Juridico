-- =====================================================================================================================
-- V3 · Gestión de clientes: personas físicas o jurídicas y sus contactos.
-- =====================================================================================================================

CREATE TABLE cliente (
    id                           BIGINT IDENTITY(1,1) NOT NULL,
    tipo_persona                 CHAR(1)        NOT NULL,
    nombres                      NVARCHAR(80)   NULL,
    apellidos                    NVARCHAR(80)   NULL,
    razon_social                 NVARCHAR(150)  NULL,
    tipo_documento_identidad_id  BIGINT         NULL,
    numero_documento             VARCHAR(20)    NULL,
    fecha_nacimiento             DATE           NULL,
    email                        NVARCHAR(120)  NULL,
    telefono                     VARCHAR(30)    NULL,
    celular                      VARCHAR(30)    NULL,
    direccion                    NVARCHAR(200)  NULL,
    ciudad_id                    BIGINT         NULL,
    observaciones                NVARCHAR(MAX)  NULL,
    activo                       BIT            NOT NULL CONSTRAINT df_cliente_activo DEFAULT 1,
    creado_en                    DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_cliente_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por                   BIGINT         NULL,
    modificado_en                DATETIMEOFFSET(3) NULL,
    modificado_por               BIGINT         NULL,
    CONSTRAINT pk_cliente PRIMARY KEY (id),
    CONSTRAINT ck_cliente_tipo_persona CHECK (tipo_persona IN ('F', 'J')),
    -- Persona física: nombres y apellidos. Persona jurídica: razón social y sin fecha de nacimiento.
    CONSTRAINT ck_cliente_datos_persona CHECK (
        (tipo_persona = 'F' AND nombres IS NOT NULL AND apellidos IS NOT NULL)
     OR (tipo_persona = 'J' AND razon_social IS NOT NULL AND fecha_nacimiento IS NULL)),
    CONSTRAINT ck_cliente_telefono CHECK (telefono IS NOT NULL OR celular IS NOT NULL),
    -- Tipo y número de documento van juntos.
    CONSTRAINT ck_cliente_documento CHECK ((tipo_documento_identidad_id IS NULL AND numero_documento IS NULL)
                                        OR (tipo_documento_identidad_id IS NOT NULL AND numero_documento IS NOT NULL)),
    CONSTRAINT fk_cliente_tipo_documento_identidad FOREIGN KEY (tipo_documento_identidad_id) REFERENCES tipo_documento_identidad (id),
    CONSTRAINT fk_cliente_ciudad FOREIGN KEY (ciudad_id) REFERENCES ciudad (id),
    CONSTRAINT fk_cliente_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_cliente_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
-- Único junto con el tipo de documento, cuando se cargó.
CREATE UNIQUE INDEX uq_cliente_documento ON cliente (tipo_documento_identidad_id, numero_documento)
    WHERE numero_documento IS NOT NULL;
CREATE INDEX ix_cliente_ciudad_id ON cliente (ciudad_id);
CREATE INDEX ix_cliente_creado_por ON cliente (creado_por);
CREATE INDEX ix_cliente_modificado_por ON cliente (modificado_por);
-- Búsqueda por nombre (la intercalación de la base ya ignora mayúsculas y acentos).
CREATE INDEX ix_cliente_apellidos_nombres ON cliente (apellidos, nombres);
CREATE INDEX ix_cliente_razon_social ON cliente (razon_social);

CREATE TABLE cliente_contacto (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    cliente_id      BIGINT         NOT NULL,
    nombre          NVARCHAR(120)  NOT NULL,
    cargo           NVARCHAR(60)   NULL,
    email           NVARCHAR(120)  NULL,
    telefono        VARCHAR(30)    NULL,
    es_principal    BIT            NOT NULL CONSTRAINT df_cliente_contacto_es_principal DEFAULT 0,
    creado_en       DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_cliente_contacto_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por      BIGINT         NULL,
    modificado_en   DATETIMEOFFSET(3) NULL,
    modificado_por  BIGINT         NULL,
    CONSTRAINT pk_cliente_contacto PRIMARY KEY (id),
    CONSTRAINT fk_cliente_contacto_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_cliente_contacto_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_cliente_contacto_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
CREATE INDEX ix_cliente_contacto_cliente_id ON cliente_contacto (cliente_id);
-- Un solo contacto principal por cliente.
CREATE UNIQUE INDEX uq_cliente_contacto_principal ON cliente_contacto (cliente_id) WHERE es_principal = 1;
CREATE INDEX ix_cliente_contacto_creado_por ON cliente_contacto (creado_por);
CREATE INDEX ix_cliente_contacto_modificado_por ON cliente_contacto (modificado_por);
