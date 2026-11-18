-- =====================================================================================================================
-- V10 · Teléfonos del cliente en su propia tabla, ligados al cliente por su tipo y número de documento. Cada teléfono
-- tiene su prefijo (el país) y su número; uno es el principal.
-- =====================================================================================================================

-- El documento del cliente pasa a ser su clave para los teléfonos: restricción única (no filtrada) sobre el par.
-- La aplicación exige documento a todo cliente; un cliente antiguo sin documento no tiene teléfonos en la tabla.
DROP INDEX uq_cliente_documento ON cliente;
ALTER TABLE cliente ADD CONSTRAINT uq_cliente_tipo_numero_documento UNIQUE (tipo_documento_identidad_id, numero_documento);
GO

CREATE TABLE cliente_telefono (
    id                           BIGINT IDENTITY(1,1) NOT NULL,
    tipo_documento_identidad_id  BIGINT         NOT NULL,
    numero_documento             VARCHAR(20)    NOT NULL,
    pais_id                      BIGINT         NOT NULL,
    numero                       VARCHAR(20)    NOT NULL,
    es_principal                 BIT            NOT NULL CONSTRAINT df_cliente_telefono_es_principal DEFAULT 0,
    creado_en                    DATETIMEOFFSET(3) NOT NULL CONSTRAINT df_cliente_telefono_creado_en DEFAULT SYSUTCDATETIME(),
    creado_por                   BIGINT         NULL,
    modificado_en                DATETIMEOFFSET(3) NULL,
    modificado_por               BIGINT         NULL,
    CONSTRAINT pk_cliente_telefono PRIMARY KEY (id),
    -- El número sin el prefijo: dígitos, espacios y guiones, de 6 a 20 caracteres.
    CONSTRAINT ck_cliente_telefono_numero CHECK (LEN(numero) >= 6 AND numero NOT LIKE '%[^0-9 -]%'),
    -- Si cambia el documento del cliente, sus teléfonos lo siguen.
    CONSTRAINT fk_cliente_telefono_cliente FOREIGN KEY (tipo_documento_identidad_id, numero_documento)
        REFERENCES cliente (tipo_documento_identidad_id, numero_documento) ON UPDATE CASCADE,
    CONSTRAINT fk_cliente_telefono_pais FOREIGN KEY (pais_id) REFERENCES pais (id),
    CONSTRAINT fk_cliente_telefono_creado_por FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT fk_cliente_telefono_modificado_por FOREIGN KEY (modificado_por) REFERENCES usuario (id),
    -- El mismo número no se repite en un cliente; también sirve de índice para la clave foránea.
    CONSTRAINT uq_cliente_telefono UNIQUE (tipo_documento_identidad_id, numero_documento, pais_id, numero)
);
-- Un solo teléfono principal por cliente.
CREATE UNIQUE INDEX uq_cliente_telefono_principal ON cliente_telefono (tipo_documento_identidad_id, numero_documento)
    WHERE es_principal = 1;
CREATE INDEX ix_cliente_telefono_pais_id ON cliente_telefono (pais_id);
GO

-- Paso de los teléfonos actuales (cliente.telefono) a la tabla, todos con el prefijo de Paraguay. Si había varios
-- separados por "/", cada uno es un teléfono; el primero queda como principal. Se dejan solo dígitos, espacios y guiones.
WITH partes AS (
    SELECT c.tipo_documento_identidad_id, c.numero_documento, s.ordinal,
           LTRIM(RTRIM(TRANSLATE(s.value, '()+.', '    '))) AS numero
    FROM cliente c
    CROSS APPLY STRING_SPLIT(c.telefono, '/', 1) s
    WHERE c.numero_documento IS NOT NULL AND c.telefono IS NOT NULL
), validas AS (
    SELECT tipo_documento_identidad_id, numero_documento, LEFT(numero, 20) AS numero,
           ROW_NUMBER() OVER (PARTITION BY tipo_documento_identidad_id, numero_documento ORDER BY ordinal) AS orden
    FROM partes
    WHERE LEN(numero) >= 6 AND numero NOT LIKE '%[^0-9 -]%'
)
INSERT INTO cliente_telefono (tipo_documento_identidad_id, numero_documento, pais_id, numero, es_principal)
SELECT DISTINCT v.tipo_documento_identidad_id, v.numero_documento, p.id, v.numero, CASE WHEN v.orden = 1 THEN 1 ELSE 0 END
FROM validas v CROSS JOIN pais p
WHERE p.codigo = 'PY';

-- Lo que no pudo pasar (cliente sin documento, o un texto que no es un número) queda en las observaciones.
UPDATE c SET observaciones = CONCAT(c.observaciones, CASE WHEN c.observaciones IS NULL THEN N'' ELSE NCHAR(10) END,
                                    N'Teléfono anterior: ', c.telefono)
FROM cliente c
WHERE c.telefono IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM cliente_telefono t
                  WHERE t.tipo_documento_identidad_id = c.tipo_documento_identidad_id AND t.numero_documento = c.numero_documento);

ALTER TABLE cliente DROP COLUMN telefono;
