-- =====================================================================================================================
-- V9 · Países (con su prefijo telefónico), departamentos por país, y un solo teléfono por cliente.
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- pais
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE pais (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    codigo              VARCHAR(30)   NOT NULL,
    nombre              NVARCHAR(60)  NOT NULL,
    prefijo_telefonico  VARCHAR(6)    NOT NULL,
    activo              BIT           NOT NULL CONSTRAINT df_pais_activo DEFAULT 1,
    CONSTRAINT pk_pais PRIMARY KEY (id),
    CONSTRAINT uq_pais_codigo UNIQUE (codigo),
    CONSTRAINT uq_pais_nombre UNIQUE (nombre),
    -- "+" y de 1 a 4 dígitos: +595, +54, +1.
    CONSTRAINT ck_pais_prefijo CHECK (prefijo_telefonico LIKE '+[0-9]%' AND LEN(prefijo_telefonico) BETWEEN 2 AND 5
                                      AND SUBSTRING(prefijo_telefonico, 2, 4) NOT LIKE '%[^0-9]%')
);

-- Código ISO 3166 de dos letras.
INSERT INTO pais (codigo, nombre, prefijo_telefonico) VALUES
    ('PY', N'Paraguay',        '+595'),
    ('AR', N'Argentina',       '+54'),
    ('BR', N'Brasil',          '+55'),
    ('BO', N'Bolivia',         '+591'),
    ('UY', N'Uruguay',         '+598'),
    ('CL', N'Chile',           '+56'),
    ('PE', N'Perú',            '+51'),
    ('CO', N'Colombia',        '+57'),
    ('EC', N'Ecuador',         '+593'),
    ('VE', N'Venezuela',       '+58'),
    ('MX', N'México',          '+52'),
    ('US', N'Estados Unidos',  '+1'),
    ('ES', N'España',          '+34'),
    ('IT', N'Italia',          '+39'),
    ('DE', N'Alemania',        '+49');
GO

-- ---------------------------------------------------------------------------------------------------------------------
-- departamento: pertenece a un país (los cargados son de Paraguay). En otros países es la provincia o el estado.
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE departamento ADD pais_id BIGINT NULL;
GO
UPDATE departamento SET pais_id = (SELECT id FROM pais WHERE codigo = 'PY');
ALTER TABLE departamento ALTER COLUMN pais_id BIGINT NOT NULL;
ALTER TABLE departamento ADD CONSTRAINT fk_departamento_pais FOREIGN KEY (pais_id) REFERENCES pais (id);
-- Un nombre por país; también sirve de índice para la clave foránea.
ALTER TABLE departamento ADD CONSTRAINT uq_departamento_pais_nombre UNIQUE (pais_id, nombre);
GO

-- ---------------------------------------------------------------------------------------------------------------------
-- cliente: teléfono y celular pasan a ser un solo dato, obligatorio.
--   Sin teléfono: se usa el celular. Con los dos distintos: se juntan si entran en 30 caracteres; si no, el celular
--   queda en las observaciones para no perderlo.
-- ---------------------------------------------------------------------------------------------------------------------
UPDATE cliente SET telefono = celular WHERE telefono IS NULL;
UPDATE cliente SET telefono = CONCAT(telefono, ' / ', celular)
WHERE celular IS NOT NULL AND celular <> telefono AND LEN(telefono) + 3 + LEN(celular) <= 30;
UPDATE cliente SET observaciones = CONCAT(observaciones, CASE WHEN observaciones IS NULL THEN N'' ELSE NCHAR(10) END, N'Celular: ', celular)
WHERE celular IS NOT NULL AND celular <> telefono AND telefono NOT LIKE '%' + celular;
ALTER TABLE cliente DROP CONSTRAINT ck_cliente_telefono;
ALTER TABLE cliente DROP COLUMN celular;
ALTER TABLE cliente ALTER COLUMN telefono VARCHAR(30) NOT NULL;
GO

-- ---------------------------------------------------------------------------------------------------------------------
-- Menú: Administración → Catálogos → Países, departamentos y ciudades.
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO menu (padre_id, nivel, codigo, titulo, ruta, permiso_id, orden)
SELECT padre.id, 'OPCION', 'ADMIN_UBICACIONES', N'Países, departamentos y ciudades', '/admin/ubicaciones', p.id, 5
FROM menu padre JOIN permiso p ON p.codigo = 'CATALOGOS_EDITAR'
WHERE padre.codigo = 'ADMIN_CATALOGOS';
