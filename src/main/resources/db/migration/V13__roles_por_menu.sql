-- =====================================================================================================================
-- V13 · Roles por menú. Un rol se define por las pantallas (opciones del menú) que ve, y por las acciones que puede
-- hacer en ellas. Los permisos de "ver" (CLIENTES_VER, REPORTES_VER…) se reemplazan por las opciones del menú; quedan
-- como permisos solo las acciones, cada una colgada de su pantalla.
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- rol_menu: pantallas que ve cada rol (opciones del menú)
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE rol_menu (
    rol_id   BIGINT NOT NULL,
    menu_id  BIGINT NOT NULL,
    CONSTRAINT pk_rol_menu PRIMARY KEY (rol_id, menu_id),
    CONSTRAINT fk_rol_menu_rol FOREIGN KEY (rol_id) REFERENCES rol (id),
    CONSTRAINT fk_rol_menu_menu FOREIGN KEY (menu_id) REFERENCES menu (id)
);
CREATE INDEX ix_rol_menu_menu_id ON rol_menu (menu_id);
GO

-- Cada rol ve lo mismo que veía: las opciones cuyo permiso tenía.
INSERT INTO rol_menu (rol_id, menu_id)
SELECT DISTINCT rp.rol_id, m.id
FROM menu m JOIN rol_permiso rp ON rp.permiso_id = m.permiso_id
WHERE m.nivel = 'OPCION';
GO

-- ---------------------------------------------------------------------------------------------------------------------
-- permiso: solo acciones, cada una de una pantalla
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE permiso ADD menu_id BIGINT NULL;
GO
UPDATE p SET menu_id = m.id
FROM permiso p JOIN (VALUES
    ('CLIENTES_EDITAR',     'CLIENTES_LISTA',     N'Agregar y modificar clientes, contactos y teléfonos'),
    ('EXPEDIENTES_EDITAR',  'EXPEDIENTES_LISTA',  N'Agregar y modificar expedientes, partes y actuaciones'),
    ('EXPEDIENTES_CERRAR',  'EXPEDIENTES_LISTA',  N'Cerrar y reabrir expedientes'),
    ('AGENDA_EDITAR',       'AGENDA_CALENDARIO',  N'Agregar, modificar y cambiar el estado de los eventos'),
    ('DOCUMENTOS_SUBIR',    'DOCUMENTOS_BUSCAR',  N'Cargar documentos y modificar sus datos'),
    ('DOCUMENTOS_ELIMINAR', 'DOCUMENTOS_BUSCAR',  N'Eliminar y restaurar documentos')
) AS a (permiso, menu, descripcion) ON a.permiso = p.codigo
JOIN menu m ON m.codigo = a.menu;

UPDATE p SET descripcion = a.descripcion
FROM permiso p JOIN (VALUES
    ('CLIENTES_EDITAR',     N'Agregar y modificar clientes, contactos y teléfonos'),
    ('EXPEDIENTES_EDITAR',  N'Agregar y modificar expedientes, partes y actuaciones'),
    ('EXPEDIENTES_CERRAR',  N'Cerrar y reabrir expedientes'),
    ('AGENDA_EDITAR',       N'Agregar, modificar y cambiar el estado de los eventos'),
    ('DOCUMENTOS_SUBIR',    N'Cargar documentos y modificar sus datos'),
    ('DOCUMENTOS_ELIMINAR', N'Eliminar y restaurar documentos')
) AS a (permiso, descripcion) ON a.permiso = p.codigo;

-- El menú ya no lleva permiso: la opción es en sí lo que se asigna al rol.
ALTER TABLE menu DROP CONSTRAINT ck_menu_opcion;
ALTER TABLE menu DROP CONSTRAINT fk_menu_permiso;
DROP INDEX ix_menu_permiso_id ON menu;
ALTER TABLE menu DROP COLUMN permiso_id;
ALTER TABLE menu ADD CONSTRAINT ck_menu_opcion CHECK ((nivel = 'OPCION' AND ruta IS NOT NULL) OR (nivel <> 'OPCION' AND ruta IS NULL));

-- Los permisos de "ver" dejan de existir (los reemplaza rol_menu).
DELETE rp FROM rol_permiso rp JOIN permiso p ON p.id = rp.permiso_id WHERE p.menu_id IS NULL;
DELETE FROM permiso WHERE menu_id IS NULL;
ALTER TABLE permiso ALTER COLUMN menu_id BIGINT NOT NULL;
ALTER TABLE permiso ADD CONSTRAINT fk_permiso_menu FOREIGN KEY (menu_id) REFERENCES menu (id);
CREATE INDEX ix_permiso_menu_id ON permiso (menu_id);
