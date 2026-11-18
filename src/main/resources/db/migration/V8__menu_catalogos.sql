-- =====================================================================================================================
-- V8 · Títulos del menú de catálogos: cada opción agrupa más de un catálogo.
--   Juzgados incluye las circunscripciones; Tipos incluye documento, evento, actuación y roles de parte.
-- =====================================================================================================================
UPDATE menu SET titulo = N'Juzgados y circunscripciones' WHERE codigo = 'ADMIN_JUZGADOS';
UPDATE menu SET titulo = N'Tipos y roles de parte' WHERE codigo = 'ADMIN_TIPOS';
