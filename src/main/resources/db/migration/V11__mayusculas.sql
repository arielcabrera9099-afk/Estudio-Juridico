-- =====================================================================================================================
-- V11 · Todo texto que carga el usuario se guarda en MAYÚSCULAS (la aplicación ya lo hace al guardar). Esta migración
-- pasa a mayúsculas lo que ya estaba cargado. No se tocan: emails, usuario de ingreso, contraseñas, nombres originales
-- de archivo, rutas, hashes, códigos técnicos (colores de agenda), valores de parámetros, menú ni auditoría.
-- =====================================================================================================================

-- Clientes
UPDATE cliente SET nombres = UPPER(nombres), apellidos = UPPER(apellidos), razon_social = UPPER(razon_social),
                   numero_documento = UPPER(numero_documento), direccion = UPPER(direccion), observaciones = UPPER(observaciones);
UPDATE cliente_contacto SET nombre = UPPER(nombre), cargo = UPPER(cargo);

-- Expedientes
UPDATE expediente SET caratula = UPPER(caratula), descripcion = UPPER(descripcion), motivo_cierre = UPPER(motivo_cierre),
                      numero_expediente_judicial = UPPER(numero_expediente_judicial);
UPDATE expediente_parte SET nombre = UPPER(nombre), numero_documento = UPPER(numero_documento), abogado = UPPER(abogado),
                            contacto = UPPER(contacto), observaciones = UPPER(observaciones);
UPDATE actuacion SET descripcion = UPPER(descripcion);
UPDATE expediente_estado_historial SET observacion = UPPER(observacion);

-- Agenda y documentos
UPDATE evento_agenda SET titulo = UPPER(titulo), descripcion = UPPER(descripcion), lugar = UPPER(lugar), resultado = UPPER(resultado);
UPDATE documento SET nombre = UPPER(nombre), descripcion = UPPER(descripcion);

-- Usuarios y roles
UPDATE usuario SET nombres = UPPER(nombres), apellidos = UPPER(apellidos), matricula = UPPER(matricula);
UPDATE rol SET nombre = UPPER(nombre), descripcion = UPPER(descripcion);

-- Catálogos
UPDATE materia SET nombre = UPPER(nombre);
UPDATE tipo_proceso SET nombre = UPPER(nombre);
UPDATE estado_expediente SET nombre = UPPER(nombre);
UPDATE circunscripcion SET nombre = UPPER(nombre);
UPDATE juzgado SET nombre = UPPER(nombre), turno = UPPER(turno), direccion = UPPER(direccion);
UPDATE rol_parte SET nombre = UPPER(nombre);
UPDATE tipo_actuacion SET nombre = UPPER(nombre);
UPDATE tipo_evento SET nombre = UPPER(nombre);
UPDATE tipo_documento SET nombre = UPPER(nombre);
UPDATE tipo_documento_identidad SET nombre = UPPER(nombre);
UPDATE pais SET nombre = UPPER(nombre);
UPDATE departamento SET nombre = UPPER(nombre);
UPDATE ciudad SET nombre = UPPER(nombre);
