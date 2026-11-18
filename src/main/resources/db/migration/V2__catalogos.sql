-- =====================================================================================================================
-- V2 · Catálogos y parámetros: generales (departamentos, ciudades, documentos de identidad), del expediente, de la
-- agenda y de los documentos, con sus datos iniciales.
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- parametro
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE parametro (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    clave           VARCHAR(60)    NOT NULL,
    valor           NVARCHAR(500)  NOT NULL,
    tipo            VARCHAR(10)    NOT NULL,
    descripcion     NVARCHAR(250)  NULL,
    creado_en       DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_parametro_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por      BIGINT         NULL,
    modificado_en   DATETIMEOFFSET(3) NULL,
    modificado_por  BIGINT         NULL,
    CONSTRAINT pk_parametro PRIMARY KEY (id),
    CONSTRAINT uq_parametro_clave UNIQUE (clave),
    CONSTRAINT ck_parametro_tipo CHECK (tipo IN ('NUMERO', 'TEXTO', 'LISTA', 'BOOLEANO')),
    CONSTRAINT fk_parametro_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_parametro_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
CREATE INDEX ix_parametro_creado_por ON parametro (creado_por);
CREATE INDEX ix_parametro_modificado_por ON parametro (modificado_por);

-- ---------------------------------------------------------------------------------------------------------------------
-- Catálogos generales
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE departamento (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    codigo  VARCHAR(30)   NOT NULL,
    nombre  NVARCHAR(60)  NOT NULL,
    activo  BIT           NOT NULL CONSTRAINT df_departamento_activo DEFAULT 1,
    CONSTRAINT pk_departamento PRIMARY KEY (id),
    CONSTRAINT uq_departamento_codigo UNIQUE (codigo)
);

CREATE TABLE ciudad (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    departamento_id  BIGINT        NOT NULL,
    nombre           NVARCHAR(80)  NOT NULL,
    activo           BIT           NOT NULL CONSTRAINT df_ciudad_activo DEFAULT 1,
    CONSTRAINT pk_ciudad PRIMARY KEY (id),
    CONSTRAINT uq_ciudad_departamento_nombre UNIQUE (departamento_id, nombre),
    CONSTRAINT fk_ciudad_departamento FOREIGN KEY (departamento_id) REFERENCES departamento (id)
);
-- La clave única (departamento_id, nombre) ya sirve de índice para la clave foránea.

CREATE TABLE tipo_documento_identidad (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    codigo  VARCHAR(30)   NOT NULL,
    nombre  NVARCHAR(60)  NOT NULL,
    activo  BIT           NOT NULL CONSTRAINT df_tipo_documento_identidad_activo DEFAULT 1,
    CONSTRAINT pk_tipo_documento_identidad PRIMARY KEY (id),
    CONSTRAINT uq_tipo_documento_identidad_codigo UNIQUE (codigo)
);

-- ---------------------------------------------------------------------------------------------------------------------
-- Catálogos del expediente
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE materia (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    codigo  VARCHAR(30)   NOT NULL,
    nombre  NVARCHAR(60)  NOT NULL,
    activo  BIT           NOT NULL CONSTRAINT df_materia_activo DEFAULT 1,
    CONSTRAINT pk_materia PRIMARY KEY (id),
    CONSTRAINT uq_materia_codigo UNIQUE (codigo)
);

CREATE TABLE tipo_proceso (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    materia_id  BIGINT        NOT NULL,
    codigo      VARCHAR(30)   NOT NULL,
    nombre      NVARCHAR(80)  NOT NULL,
    activo      BIT           NOT NULL CONSTRAINT df_tipo_proceso_activo DEFAULT 1,
    CONSTRAINT pk_tipo_proceso PRIMARY KEY (id),
    CONSTRAINT uq_tipo_proceso_codigo UNIQUE (codigo),
    -- Permite que expediente verifique que el tipo de proceso pertenece a la materia elegida.
    CONSTRAINT uq_tipo_proceso_id_materia UNIQUE (id, materia_id),
    CONSTRAINT fk_tipo_proceso_materia FOREIGN KEY (materia_id) REFERENCES materia (id)
);
CREATE INDEX ix_tipo_proceso_materia_id ON tipo_proceso (materia_id);

CREATE TABLE estado_expediente (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    codigo     VARCHAR(30)   NOT NULL,
    nombre     NVARCHAR(60)  NOT NULL,
    es_activo  BIT           NOT NULL,
    es_cierre  BIT           NOT NULL,
    orden      SMALLINT      NOT NULL CONSTRAINT df_estado_expediente_orden DEFAULT 0,
    activo     BIT           NOT NULL CONSTRAINT df_estado_expediente_activo DEFAULT 1,
    CONSTRAINT pk_estado_expediente PRIMARY KEY (id),
    CONSTRAINT uq_estado_expediente_codigo UNIQUE (codigo),
    -- Un estado de cierre nunca cuenta como caso activo.
    CONSTRAINT ck_estado_expediente_cierre CHECK (NOT (es_activo = 1 AND es_cierre = 1))
);

CREATE TABLE circunscripcion (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    codigo  VARCHAR(30)   NOT NULL,
    nombre  NVARCHAR(60)  NOT NULL,
    activo  BIT           NOT NULL CONSTRAINT df_circunscripcion_activo DEFAULT 1,
    CONSTRAINT pk_circunscripcion PRIMARY KEY (id),
    CONSTRAINT uq_circunscripcion_codigo UNIQUE (codigo)
);

CREATE TABLE juzgado (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    circunscripcion_id  BIGINT         NOT NULL,
    materia_id          BIGINT         NOT NULL,
    nombre              NVARCHAR(150)  NOT NULL,
    turno               NVARCHAR(30)   NULL,
    direccion           NVARCHAR(200)  NULL,
    telefono            VARCHAR(30)    NULL,
    activo              BIT            NOT NULL CONSTRAINT df_juzgado_activo DEFAULT 1,
    CONSTRAINT pk_juzgado PRIMARY KEY (id),
    CONSTRAINT fk_juzgado_circunscripcion FOREIGN KEY (circunscripcion_id) REFERENCES circunscripcion (id),
    CONSTRAINT fk_juzgado_materia FOREIGN KEY (materia_id) REFERENCES materia (id)
);
CREATE INDEX ix_juzgado_circunscripcion_id ON juzgado (circunscripcion_id);
CREATE INDEX ix_juzgado_materia_id ON juzgado (materia_id);

CREATE TABLE rol_parte (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    codigo  VARCHAR(30)   NOT NULL,
    nombre  NVARCHAR(60)  NOT NULL,
    activo  BIT           NOT NULL CONSTRAINT df_rol_parte_activo DEFAULT 1,
    CONSTRAINT pk_rol_parte PRIMARY KEY (id),
    CONSTRAINT uq_rol_parte_codigo UNIQUE (codigo)
);

CREATE TABLE tipo_actuacion (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    codigo  VARCHAR(30)   NOT NULL,
    nombre  NVARCHAR(80)  NOT NULL,
    activo  BIT           NOT NULL CONSTRAINT df_tipo_actuacion_activo DEFAULT 1,
    CONSTRAINT pk_tipo_actuacion PRIMARY KEY (id),
    CONSTRAINT uq_tipo_actuacion_codigo UNIQUE (codigo)
);

-- ---------------------------------------------------------------------------------------------------------------------
-- Catálogo de la agenda
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE tipo_evento (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    codigo              VARCHAR(30)   NOT NULL,
    nombre              NVARCHAR(60)  NOT NULL,
    color               VARCHAR(20)   NOT NULL,
    es_plazo            BIT           NOT NULL CONSTRAINT df_tipo_evento_es_plazo DEFAULT 0,
    aviso_dias_defecto  SMALLINT      NULL,
    activo              BIT           NOT NULL CONSTRAINT df_tipo_evento_activo DEFAULT 1,
    CONSTRAINT pk_tipo_evento PRIMARY KEY (id),
    CONSTRAINT uq_tipo_evento_codigo UNIQUE (codigo),
    CONSTRAINT ck_tipo_evento_aviso_dias CHECK (aviso_dias_defecto IS NULL OR aviso_dias_defecto >= 0)
);

-- ---------------------------------------------------------------------------------------------------------------------
-- Catálogo de documentos
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE tipo_documento (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    codigo  VARCHAR(30)   NOT NULL,
    nombre  NVARCHAR(60)  NOT NULL,
    activo  BIT           NOT NULL CONSTRAINT df_tipo_documento_activo DEFAULT 1,
    CONSTRAINT pk_tipo_documento PRIMARY KEY (id),
    CONSTRAINT uq_tipo_documento_codigo UNIQUE (codigo)
);

-- =====================================================================================================================
-- Datos iniciales
-- =====================================================================================================================

INSERT INTO parametro (clave, valor, tipo, descripcion) VALUES
    ('seguridad.max_intentos',       N'5',                    'NUMERO', N'Intentos fallidos antes de bloquear al usuario'),
    ('seguridad.minutos_bloqueo',    N'15',                   'NUMERO', N'Duración del bloqueo, en minutos'),
    ('agenda.dias_aviso',            N'3,1,0',                'LISTA',  N'Días antes del vencimiento en que se avisa'),
    ('documentos.tamano_max_mb',     N'20',                   'NUMERO', N'Tamaño máximo por archivo, en MB'),
    ('documentos.tipos_permitidos',  N'pdf,doc,docx,jpg,png', 'LISTA',  N'Extensiones de archivo aceptadas'),
    ('expediente.formato_numero',    N'AAAA-NNNN',            'TEXTO',  N'Formato del número interno del expediente');

INSERT INTO tipo_documento_identidad (codigo, nombre) VALUES
    ('CI',        N'Cédula de identidad'),
    ('RUC',       N'RUC'),
    ('PASAPORTE', N'Pasaporte');

INSERT INTO departamento (codigo, nombre) VALUES
    ('ASUNCION',          N'Asunción (Distrito Capital)'),
    ('CONCEPCION',        N'Concepción'),
    ('SAN_PEDRO',         N'San Pedro'),
    ('CORDILLERA',        N'Cordillera'),
    ('GUAIRA',            N'Guairá'),
    ('CAAGUAZU',          N'Caaguazú'),
    ('CAAZAPA',           N'Caazapá'),
    ('ITAPUA',            N'Itapúa'),
    ('MISIONES',          N'Misiones'),
    ('PARAGUARI',         N'Paraguarí'),
    ('ALTO_PARANA',       N'Alto Paraná'),
    ('CENTRAL',           N'Central'),
    ('NEEMBUCU',          N'Ñeembucú'),
    ('AMAMBAY',           N'Amambay'),
    ('CANINDEYU',         N'Canindeyú'),
    ('PRESIDENTE_HAYES',  N'Presidente Hayes'),
    ('BOQUERON',          N'Boquerón'),
    ('ALTO_PARAGUAY',     N'Alto Paraguay');

-- Capitales departamentales y ciudades principales. El resto de los distritos se agrega cuando haga falta.
INSERT INTO ciudad (departamento_id, nombre)
SELECT d.id, c.nombre
FROM (VALUES
    ('ASUNCION',         N'Asunción'),
    ('CONCEPCION',       N'Concepción'),
    ('SAN_PEDRO',        N'San Pedro de Ycuamandiyú'),
    ('CORDILLERA',       N'Caacupé'),
    ('GUAIRA',           N'Villarrica'),
    ('CAAGUAZU',         N'Coronel Oviedo'),
    ('CAAGUAZU',         N'Caaguazú'),
    ('CAAZAPA',          N'Caazapá'),
    ('ITAPUA',           N'Encarnación'),
    ('MISIONES',         N'San Juan Bautista'),
    ('PARAGUARI',        N'Paraguarí'),
    ('ALTO_PARANA',      N'Ciudad del Este'),
    ('ALTO_PARANA',      N'Hernandarias'),
    ('ALTO_PARANA',      N'Presidente Franco'),
    ('ALTO_PARANA',      N'Minga Guazú'),
    ('CENTRAL',          N'Areguá'),
    ('CENTRAL',          N'Capiatá'),
    ('CENTRAL',          N'Fernando de la Mora'),
    ('CENTRAL',          N'Itauguá'),
    ('CENTRAL',          N'Lambaré'),
    ('CENTRAL',          N'Limpio'),
    ('CENTRAL',          N'Luque'),
    ('CENTRAL',          N'Mariano Roque Alonso'),
    ('CENTRAL',          N'Ñemby'),
    ('CENTRAL',          N'San Antonio'),
    ('CENTRAL',          N'San Lorenzo'),
    ('CENTRAL',          N'Villa Elisa'),
    ('NEEMBUCU',         N'Pilar'),
    ('AMAMBAY',          N'Pedro Juan Caballero'),
    ('CANINDEYU',        N'Salto del Guairá'),
    ('PRESIDENTE_HAYES', N'Villa Hayes'),
    ('BOQUERON',         N'Filadelfia'),
    ('ALTO_PARAGUAY',    N'Fuerte Olimpo')
) AS c (departamento, nombre)
JOIN departamento d ON d.codigo = c.departamento;

INSERT INTO materia (codigo, nombre) VALUES
    ('CIVIL',        N'Civil'),
    ('COMERCIAL',    N'Comercial'),
    ('PENAL',        N'Penal'),
    ('LABORAL',      N'Laboral'),
    ('FAMILIA',      N'Familia'),
    ('NINEZ',        N'Niñez y Adolescencia'),
    ('CONTENCIOSO',  N'Contencioso-Administrativo');

INSERT INTO tipo_proceso (materia_id, codigo, nombre)
SELECT m.id, t.codigo, t.nombre
FROM (VALUES
    ('CIVIL',       'CIVIL_ORDINARIO',        N'Ordinario'),
    ('CIVIL',       'CIVIL_EJECUTIVO',        N'Ejecutivo'),
    ('CIVIL',       'CIVIL_SUCESION',         N'Sucesión'),
    ('COMERCIAL',   'COMERCIAL_ORDINARIO',    N'Ordinario'),
    ('PENAL',       'PENAL_ACCION_PUBLICA',   N'Acción penal pública'),
    ('PENAL',       'PENAL_ACCION_PRIVADA',   N'Acción penal privada'),
    ('LABORAL',     'LABORAL_ORDINARIO',      N'Ordinario'),
    ('FAMILIA',     'FAMILIA_DIVORCIO',       N'Divorcio'),
    ('NINEZ',       'NINEZ_ALIMENTOS',        N'Asistencia alimenticia'),
    ('CONTENCIOSO', 'CONTENCIOSO_ADMIN',      N'Contencioso-administrativo')
) AS t (materia, codigo, nombre)
JOIN materia m ON m.codigo = t.materia;

INSERT INTO estado_expediente (codigo, nombre, es_activo, es_cierre, orden) VALUES
    ('ABIERTO',        N'Abierto',        1, 0, 1),
    ('EN_TRAMITE',     N'En trámite',     1, 0, 2),
    ('SUSPENDIDO',     N'Suspendido',     1, 0, 3),
    ('CON_SENTENCIA',  N'Con sentencia',  1, 0, 4),
    ('CERRADO',        N'Cerrado',        0, 1, 5),
    ('ARCHIVADO',      N'Archivado',      0, 1, 6);

INSERT INTO circunscripcion (codigo, nombre) VALUES
    ('CAPITAL',      N'Capital'),
    ('CENTRAL',      N'Central'),
    ('ALTO_PARANA',  N'Alto Paraná');

INSERT INTO rol_parte (codigo, nombre) VALUES
    ('ACTOR',        N'Actor'),
    ('DEMANDADO',    N'Demandado'),
    ('TERCERO',      N'Tercero'),
    ('QUERELLANTE',  N'Querellante'),
    ('IMPUTADO',     N'Imputado'),
    ('VICTIMA',      N'Víctima');

INSERT INTO tipo_actuacion (codigo, nombre) VALUES
    ('ESCRITO',          N'Presentación de escrito'),
    ('NOTIFICACION',     N'Notificación recibida'),
    ('PROVIDENCIA',      N'Providencia'),
    ('REUNION_CLIENTE',  N'Reunión con el cliente'),
    ('NOTA_INTERNA',     N'Nota interna');

-- color: token del sistema de diseño (static/css/tokens/tokens.css).
INSERT INTO tipo_evento (codigo, nombre, color, es_plazo, aviso_dias_defecto) VALUES
    ('AUDIENCIA',    N'Audiencia',    'wwp-info',    0, 1),
    ('VENCIMIENTO',  N'Vencimiento',  'wwp-danger',  1, 3),
    ('REUNION',      N'Reunión',      'wwp-success', 0, NULL),
    ('DILIGENCIA',   N'Diligencia',   'wwp-warning', 0, 1);

INSERT INTO tipo_documento (codigo, nombre) VALUES
    ('ESCRITO',       N'Escrito'),
    ('RESOLUCION',    N'Resolución judicial'),
    ('NOTIFICACION',  N'Notificación'),
    ('PRUEBA',        N'Prueba'),
    ('CONTRATO',      N'Contrato'),
    ('PODER',         N'Poder'),
    ('CEDULA',        N'Cédula de identidad'),
    ('OTRO',          N'Otro');
