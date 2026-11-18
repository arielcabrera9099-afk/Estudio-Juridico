-- =====================================================================================================================
-- V1 · Seguridad y navegación: usuarios, roles, permisos, menú, recuperación de clave y auditoría.
-- Motor: SQL Server 2019 o superior. Ver docs/modelo-de-datos.md.
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- usuario
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE usuario (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    nombre_usuario      VARCHAR(30)    NOT NULL,
    email               NVARCHAR(120)  NOT NULL,
    clave_hash          VARCHAR(100)   NOT NULL,
    nombres             NVARCHAR(80)   NOT NULL,
    apellidos           NVARCHAR(80)   NOT NULL,
    telefono            VARCHAR(30)    NULL,
    matricula           VARCHAR(30)    NULL,
    debe_cambiar_clave  BIT            NOT NULL CONSTRAINT df_usuario_debe_cambiar_clave DEFAULT 1,
    intentos_fallidos   SMALLINT       NOT NULL CONSTRAINT df_usuario_intentos_fallidos DEFAULT 0,
    bloqueado_hasta     DATETIMEOFFSET(3) NULL,
    ultimo_acceso       DATETIMEOFFSET(3) NULL,
    activo              BIT            NOT NULL CONSTRAINT df_usuario_activo DEFAULT 1,
    creado_en           DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_usuario_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por          BIGINT         NULL,
    modificado_en       DATETIMEOFFSET(3) NULL,
    modificado_por      BIGINT         NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uq_usuario_nombre_usuario UNIQUE (nombre_usuario),
    CONSTRAINT uq_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_nombre_usuario_minusculas CHECK (nombre_usuario = LOWER(nombre_usuario) COLLATE Latin1_General_BIN2),
    CONSTRAINT ck_usuario_intentos_fallidos CHECK (intentos_fallidos >= 0),
    CONSTRAINT fk_usuario_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_usuario_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
CREATE INDEX ix_usuario_creado_por ON usuario (creado_por);
CREATE INDEX ix_usuario_modificado_por ON usuario (modificado_por);

-- ---------------------------------------------------------------------------------------------------------------------
-- rol
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE rol (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    codigo          VARCHAR(30)    NOT NULL,
    nombre          NVARCHAR(60)   NOT NULL,
    descripcion     NVARCHAR(MAX)  NULL,
    activo          BIT            NOT NULL CONSTRAINT df_rol_activo DEFAULT 1,
    creado_en       DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_rol_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por      BIGINT         NULL,
    modificado_en   DATETIMEOFFSET(3) NULL,
    modificado_por  BIGINT         NULL,
    CONSTRAINT pk_rol PRIMARY KEY (id),
    CONSTRAINT uq_rol_codigo UNIQUE (codigo),
    CONSTRAINT fk_rol_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_rol_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id)
);
CREATE INDEX ix_rol_creado_por ON rol (creado_por);
CREATE INDEX ix_rol_modificado_por ON rol (modificado_por);

-- ---------------------------------------------------------------------------------------------------------------------
-- usuario_rol (puente)
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE usuario_rol (
    usuario_id  BIGINT NOT NULL,
    rol_id      BIGINT NOT NULL,
    CONSTRAINT pk_usuario_rol PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_usuario_rol_rol FOREIGN KEY (rol_id) REFERENCES rol (id)
);
CREATE INDEX ix_usuario_rol_rol_id ON usuario_rol (rol_id);

-- ---------------------------------------------------------------------------------------------------------------------
-- permiso (los define el sistema)
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE permiso (
    id           BIGINT IDENTITY(1,1) NOT NULL,
    codigo       VARCHAR(50)    NOT NULL,
    modulo       VARCHAR(30)    NOT NULL,
    descripcion  NVARCHAR(150)  NOT NULL,
    CONSTRAINT pk_permiso PRIMARY KEY (id),
    CONSTRAINT uq_permiso_codigo UNIQUE (codigo)
);

-- ---------------------------------------------------------------------------------------------------------------------
-- rol_permiso (puente)
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE rol_permiso (
    rol_id      BIGINT NOT NULL,
    permiso_id  BIGINT NOT NULL,
    CONSTRAINT pk_rol_permiso PRIMARY KEY (rol_id, permiso_id),
    CONSTRAINT fk_rol_permiso_rol FOREIGN KEY (rol_id) REFERENCES rol (id),
    CONSTRAINT fk_rol_permiso_permiso FOREIGN KEY (permiso_id) REFERENCES permiso (id)
);
CREATE INDEX ix_rol_permiso_permiso_id ON rol_permiso (permiso_id);

-- ---------------------------------------------------------------------------------------------------------------------
-- menu (tres niveles: MODULO > SECCION > OPCION)
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE menu (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    padre_id    BIGINT         NULL,
    nivel       VARCHAR(10)    NOT NULL,
    codigo      VARCHAR(40)    NOT NULL,
    titulo      NVARCHAR(60)   NOT NULL,
    icono       VARCHAR(40)    NULL,
    ruta        VARCHAR(120)   NULL,
    permiso_id  BIGINT         NULL,
    orden       SMALLINT       NOT NULL CONSTRAINT df_menu_orden DEFAULT 0,
    activo      BIT            NOT NULL CONSTRAINT df_menu_activo DEFAULT 1,
    CONSTRAINT pk_menu PRIMARY KEY (id),
    CONSTRAINT uq_menu_codigo UNIQUE (codigo),
    CONSTRAINT ck_menu_nivel CHECK (nivel IN ('MODULO', 'SECCION', 'OPCION')),
    -- Un módulo no tiene padre; una sección y una opción sí. Solo las opciones tienen ruta y permiso.
    CONSTRAINT ck_menu_padre CHECK ((nivel = 'MODULO' AND padre_id IS NULL) OR (nivel <> 'MODULO' AND padre_id IS NOT NULL)),
    CONSTRAINT ck_menu_opcion CHECK ((nivel = 'OPCION' AND ruta IS NOT NULL AND permiso_id IS NOT NULL)
                                  OR (nivel <> 'OPCION' AND ruta IS NULL AND permiso_id IS NULL)),
    CONSTRAINT fk_menu_padre FOREIGN KEY (padre_id) REFERENCES menu (id),
    CONSTRAINT fk_menu_permiso FOREIGN KEY (permiso_id) REFERENCES permiso (id)
);
CREATE INDEX ix_menu_padre_id ON menu (padre_id);
CREATE INDEX ix_menu_permiso_id ON menu (permiso_id);

-- ---------------------------------------------------------------------------------------------------------------------
-- token_recuperacion
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE token_recuperacion (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id  BIGINT            NOT NULL,
    token_hash  VARCHAR(100)      NOT NULL,
    vence_en    DATETIMEOFFSET(3) NOT NULL,
    usado_en    DATETIMEOFFSET(3) NULL,
    creado_en   DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_token_recuperacion_creado_en DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_token_recuperacion PRIMARY KEY (id),
    CONSTRAINT uq_token_recuperacion_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
CREATE INDEX ix_token_recuperacion_usuario_id ON token_recuperacion (usuario_id);

-- ---------------------------------------------------------------------------------------------------------------------
-- auditoria (solo inserción desde la aplicación)
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE auditoria (
    id                BIGINT IDENTITY(1,1) NOT NULL,
    fecha             DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_auditoria_fecha DEFAULT SYSUTCDATETIME(),
    usuario_id        BIGINT         NULL,
    accion            VARCHAR(20)    NOT NULL,
    tabla             VARCHAR(50)    NULL,
    registro_id       BIGINT         NULL,
    datos_anteriores  NVARCHAR(MAX)  NULL,
    datos_nuevos      NVARCHAR(MAX)  NULL,
    ip                VARCHAR(45)    NULL,
    CONSTRAINT pk_auditoria PRIMARY KEY (id),
    CONSTRAINT ck_auditoria_accion CHECK (accion IN ('INGRESO', 'INGRESO_FALLIDO', 'SALIDA', 'ALTA', 'MODIFICACION', 'BAJA', 'DESCARGA')),
    CONSTRAINT ck_auditoria_datos_anteriores CHECK (datos_anteriores IS NULL OR ISJSON(datos_anteriores) = 1),
    CONSTRAINT ck_auditoria_datos_nuevos CHECK (datos_nuevos IS NULL OR ISJSON(datos_nuevos) = 1),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
CREATE INDEX ix_auditoria_usuario_id ON auditoria (usuario_id);
CREATE INDEX ix_auditoria_fecha ON auditoria (fecha);
CREATE INDEX ix_auditoria_tabla_registro ON auditoria (tabla, registro_id);

-- =====================================================================================================================
-- Datos iniciales: roles, permisos, matriz rol-permiso y menú.
-- El usuario administrador inicial lo crea la aplicación en el paso de seguridad (su clave no puede ir en el repositorio).
-- =====================================================================================================================

INSERT INTO rol (codigo, nombre, descripcion) VALUES
    ('ADMINISTRADOR', N'Administrador', N'Acceso total, incluida la administración de usuarios, roles, catálogos y parámetros'),
    ('ABOGADO',       N'Abogado',       N'Gestiona clientes, expedientes, agenda, documentos y reportes'),
    ('ASISTENTE',     N'Asistente',     N'Carga y consulta datos; no cierra expedientes ni elimina documentos');

INSERT INTO permiso (codigo, modulo, descripcion) VALUES
    ('CLIENTES_VER',          'CLIENTES',    N'Ver clientes y su historial de casos'),
    ('CLIENTES_EDITAR',       'CLIENTES',    N'Dar de alta y modificar clientes y contactos'),
    ('EXPEDIENTES_VER',       'EXPEDIENTES', N'Ver expedientes, partes y actuaciones'),
    ('EXPEDIENTES_EDITAR',    'EXPEDIENTES', N'Registrar y modificar expedientes, partes y actuaciones'),
    ('EXPEDIENTES_CERRAR',    'EXPEDIENTES', N'Cerrar expedientes'),
    ('AGENDA_VER',            'AGENDA',      N'Ver el calendario y los vencimientos'),
    ('AGENDA_EDITAR',         'AGENDA',      N'Crear y modificar eventos de agenda'),
    ('DOCUMENTOS_VER',        'DOCUMENTOS',  N'Buscar y descargar documentos'),
    ('DOCUMENTOS_SUBIR',      'DOCUMENTOS',  N'Adjuntar documentos a un expediente'),
    ('DOCUMENTOS_ELIMINAR',   'DOCUMENTOS',  N'Eliminar documentos'),
    ('REPORTES_VER',          'REPORTES',    N'Ver y exportar reportes'),
    ('CATALOGOS_EDITAR',      'CATALOGOS',   N'Modificar catálogos'),
    ('SEGURIDAD_ADMINISTRAR', 'SEGURIDAD',   N'Administrar usuarios, roles, permisos y parámetros'),
    ('AUDITORIA_VER',         'SEGURIDAD',   N'Consultar la auditoría');

-- Administrador: todos los permisos.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r CROSS JOIN permiso p WHERE r.codigo = 'ADMINISTRADOR';

-- Abogado: todo salvo catálogos, seguridad y auditoría.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r CROSS JOIN permiso p
WHERE r.codigo = 'ABOGADO'
  AND p.codigo NOT IN ('CATALOGOS_EDITAR', 'SEGURIDAD_ADMINISTRAR', 'AUDITORIA_VER');

-- Asistente: además no cierra expedientes, no elimina documentos ni ve reportes.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r CROSS JOIN permiso p
WHERE r.codigo = 'ASISTENTE'
  AND p.codigo NOT IN ('CATALOGOS_EDITAR', 'SEGURIDAD_ADMINISTRAR', 'AUDITORIA_VER',
                       'EXPEDIENTES_CERRAR', 'DOCUMENTOS_ELIMINAR', 'REPORTES_VER');

-- Menú: módulos.
INSERT INTO menu (nivel, codigo, titulo, icono, orden) VALUES
    ('MODULO', 'CLIENTES',    N'Clientes',       'users',          1),
    ('MODULO', 'EXPEDIENTES', N'Expedientes',    'archive',        2),
    ('MODULO', 'AGENDA',      N'Agenda',         'calendar-check', 3),
    ('MODULO', 'DOCUMENTOS',  N'Documentos',     'file-text',      4),
    ('MODULO', 'REPORTES',    N'Reportes',       'bar-chart3',     5),
    ('MODULO', 'ADMIN',       N'Administración', 'settings',       6);

-- Menú: secciones (solo Administración las tiene).
INSERT INTO menu (padre_id, nivel, codigo, titulo, orden)
SELECT m.id, 'SECCION', s.codigo, s.titulo, s.orden
FROM (VALUES
    ('ADMIN_SEGURIDAD',     N'Seguridad',     1),
    ('ADMIN_CATALOGOS',     N'Catálogos',     2),
    ('ADMIN_CONFIGURACION', N'Configuración', 3)
) AS s (codigo, titulo, orden)
JOIN menu m ON m.codigo = 'ADMIN';

-- Menú: opciones. El padre es el módulo, o la sección cuando el módulo tiene secciones.
INSERT INTO menu (padre_id, nivel, codigo, titulo, ruta, permiso_id, orden)
SELECT padre.id, 'OPCION', o.codigo, o.titulo, o.ruta, p.id, o.orden
FROM (VALUES
    ('CLIENTES',            'CLIENTES_LISTA',            N'Clientes',                       '/clientes',              'CLIENTES_VER',          1),
    ('EXPEDIENTES',         'EXPEDIENTES_LISTA',         N'Expedientes',                    '/expedientes',           'EXPEDIENTES_VER',       1),
    ('EXPEDIENTES',         'EXPEDIENTES_NUEVO',         N'Nuevo expediente',               '/expedientes/nuevo',     'EXPEDIENTES_EDITAR',    2),
    ('AGENDA',              'AGENDA_CALENDARIO',         N'Calendario',                     '/agenda',                'AGENDA_VER',            1),
    ('AGENDA',              'AGENDA_VENCIMIENTOS',       N'Vencimientos',                   '/agenda/vencimientos',   'AGENDA_VER',            2),
    ('DOCUMENTOS',          'DOCUMENTOS_BUSCAR',         N'Buscar documentos',              '/documentos',            'DOCUMENTOS_VER',        1),
    ('REPORTES',            'REPORTES_EXPEDIENTES',      N'Casos activos y cerrados',       '/reportes/expedientes',  'REPORTES_VER',          1),
    ('REPORTES',            'REPORTES_AGENDA',           N'Agenda semanal y mensual',       '/reportes/agenda',       'REPORTES_VER',          2),
    ('ADMIN_SEGURIDAD',     'ADMIN_USUARIOS',            N'Usuarios',                       '/admin/usuarios',        'SEGURIDAD_ADMINISTRAR', 1),
    ('ADMIN_SEGURIDAD',     'ADMIN_ROLES',               N'Roles y permisos',               '/admin/roles',           'SEGURIDAD_ADMINISTRAR', 2),
    ('ADMIN_SEGURIDAD',     'ADMIN_AUDITORIA',           N'Auditoría',                      '/admin/auditoria',       'AUDITORIA_VER',         3),
    ('ADMIN_CATALOGOS',     'ADMIN_MATERIAS',            N'Materias y tipos de proceso',    '/admin/materias',        'CATALOGOS_EDITAR',      1),
    ('ADMIN_CATALOGOS',     'ADMIN_JUZGADOS',            N'Juzgados',                       '/admin/juzgados',        'CATALOGOS_EDITAR',      2),
    ('ADMIN_CATALOGOS',     'ADMIN_ESTADOS',             N'Estados del expediente',         '/admin/estados',         'CATALOGOS_EDITAR',      3),
    ('ADMIN_CATALOGOS',     'ADMIN_TIPOS',               N'Tipos de documento y de evento', '/admin/tipos',           'CATALOGOS_EDITAR',      4),
    ('ADMIN_CONFIGURACION', 'ADMIN_PARAMETROS',          N'Parámetros',                     '/admin/parametros',      'SEGURIDAD_ADMINISTRAR', 1)
) AS o (padre, codigo, titulo, ruta, permiso, orden)
JOIN menu padre ON padre.codigo = o.padre
JOIN permiso p ON p.codigo = o.permiso;
