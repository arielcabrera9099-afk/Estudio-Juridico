-- =====================================================================================================================
-- V12 · Países, departamentos y ciudades: una opción del menú para cada uno (antes compartían una sola pantalla).
-- =====================================================================================================================
UPDATE menu SET codigo = 'ADMIN_PAISES', titulo = N'Países', ruta = '/admin/paises', orden = 5
WHERE codigo = 'ADMIN_UBICACIONES';

INSERT INTO menu (padre_id, nivel, codigo, titulo, ruta, permiso_id, orden)
SELECT padre.id, 'OPCION', o.codigo, o.titulo, o.ruta, p.id, o.orden
FROM (VALUES
    ('ADMIN_DEPARTAMENTOS', N'Departamentos', '/admin/departamentos', 6),
    ('ADMIN_CIUDADES',      N'Ciudades',      '/admin/ciudades',      7)
) AS o (codigo, titulo, ruta, orden)
JOIN menu padre ON padre.codigo = 'ADMIN_CATALOGOS'
JOIN permiso p ON p.codigo = 'CATALOGOS_EDITAR';
