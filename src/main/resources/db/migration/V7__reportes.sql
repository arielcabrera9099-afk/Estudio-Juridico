-- =====================================================================================================================
-- V7 · Reportes: vistas sobre las tablas anteriores. Devuelven filas por expediente o por evento; los filtros de la
-- pantalla (fechas, materia, responsable…) y los totales se aplican en la consulta que hace la aplicación.
-- Las fechas locales usan la hora de Paraguay ('Paraguay Standard Time' en Windows).
-- Cada CREATE VIEW debe ir solo en su lote: el separador GO lo entienden Flyway, sqlcmd y SSMS.
-- =====================================================================================================================

-- Expedientes con su estado y su agrupación activo/cerrado, por materia y por abogado.
-- Ejemplo: SELECT situacion, estado_nombre, COUNT(*) FROM v_expedientes_por_estado
--          WHERE fecha_inicio BETWEEN @desde AND @hasta GROUP BY situacion, estado_nombre, estado_orden ORDER BY estado_orden;
CREATE VIEW v_expedientes_por_estado AS
SELECT
    e.id                      AS expediente_id,
    e.numero_interno,
    e.caratula,
    e.fecha_inicio,
    e.fecha_cierre,
    ee.id                     AS estado_expediente_id,
    ee.codigo                 AS estado_codigo,
    ee.nombre                 AS estado_nombre,
    ee.orden                  AS estado_orden,
    ee.es_activo,
    CASE WHEN ee.es_activo = 1 THEN 'ACTIVO' ELSE 'CERRADO' END AS situacion,
    m.id                      AS materia_id,
    m.nombre                  AS materia_nombre,
    u.id                      AS abogado_responsable_id,
    CONCAT(u.apellidos, N', ', u.nombres) AS abogado_nombre
FROM expediente e
JOIN estado_expediente ee ON ee.id = e.estado_expediente_id
JOIN materia m            ON m.id  = e.materia_id
JOIN usuario u            ON u.id  = e.abogado_responsable_id;
GO

-- Eventos de la agenda con su expediente, juzgado y responsable. La semana va de lunes a domingo.
CREATE VIEW v_agenda AS
SELECT
    ev.id                     AS evento_agenda_id,
    ev.fecha_inicio,
    ev.fecha_fin,
    CAST(ev.fecha_inicio AT TIME ZONE 'Paraguay Standard Time' AS DATETIME2(0)) AS fecha_inicio_local,
    CAST(ev.fecha_inicio AT TIME ZONE 'Paraguay Standard Time' AS DATE)         AS dia_local,
    ev.todo_el_dia,
    te.id                     AS tipo_evento_id,
    te.codigo                 AS tipo_evento_codigo,
    te.nombre                 AS tipo_evento_nombre,
    te.color                  AS tipo_evento_color,
    te.es_plazo,
    ev.titulo,
    ev.lugar,
    ex.id                     AS expediente_id,
    ex.numero_interno,
    ex.caratula,
    j.id                      AS juzgado_id,
    j.nombre                  AS juzgado_nombre,
    u.id                      AS responsable_id,
    CONCAT(u.apellidos, N', ', u.nombres) AS responsable_nombre,
    ev.estado
FROM evento_agenda ev
JOIN tipo_evento te      ON te.id = ev.tipo_evento_id
JOIN usuario u           ON u.id  = ev.responsable_id
LEFT JOIN expediente ex  ON ex.id = ev.expediente_id
LEFT JOIN juzgado j      ON j.id  = ev.juzgado_id;
GO

-- Expedientes de cada cliente con su estado y la próxima fecha pendiente de agenda.
CREATE VIEW v_historial_cliente AS
SELECT
    e.cliente_id,
    e.id                      AS expediente_id,
    e.numero_interno,
    e.caratula,
    m.nombre                  AS materia_nombre,
    ee.nombre                 AS estado_nombre,
    ee.es_activo,
    e.fecha_inicio,
    e.fecha_cierre,
    prox.proxima_fecha
FROM expediente e
JOIN materia m            ON m.id  = e.materia_id
JOIN estado_expediente ee ON ee.id = e.estado_expediente_id
OUTER APPLY (
    SELECT MIN(ev.fecha_inicio) AS proxima_fecha
    FROM evento_agenda ev
    WHERE ev.expediente_id = e.id
      AND ev.estado = 'PENDIENTE'
      AND ev.fecha_inicio >= SYSDATETIMEOFFSET()
) prox;
GO

-- Plazos pendientes con los días que faltan (negativos si ya vencieron). La pantalla filtra
-- dias_restantes BETWEEN 0 AND @dias (7 por defecto) y ordena por fecha_inicio.
CREATE VIEW v_vencimientos_proximos AS
SELECT
    ev.id                     AS evento_agenda_id,
    ev.fecha_inicio,
    CAST(ev.fecha_inicio AT TIME ZONE 'Paraguay Standard Time' AS DATETIME2(0)) AS fecha_inicio_local,
    DATEDIFF(DAY,
             CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'Paraguay Standard Time' AS DATE),
             CAST(ev.fecha_inicio AT TIME ZONE 'Paraguay Standard Time' AS DATE)) AS dias_restantes,
    ev.titulo,
    ex.id                     AS expediente_id,
    ex.numero_interno,
    ex.caratula,
    u.id                      AS responsable_id,
    CONCAT(u.apellidos, N', ', u.nombres) AS responsable_nombre
FROM evento_agenda ev
JOIN tipo_evento te      ON te.id = ev.tipo_evento_id
JOIN usuario u           ON u.id  = ev.responsable_id
LEFT JOIN expediente ex  ON ex.id = ev.expediente_id
WHERE te.es_plazo = 1
  AND ev.estado = 'PENDIENTE';
GO
