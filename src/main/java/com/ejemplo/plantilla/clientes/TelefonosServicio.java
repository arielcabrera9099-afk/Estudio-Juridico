package com.ejemplo.plantilla.clientes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Catalogos;
import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;

/**
 * Teléfonos del cliente (tabla cliente_telefono, ligada al cliente por su tipo y número de documento). Reglas:
 * prefijo (país) y número obligatorios; el número va sin prefijo; no se repite en el cliente; uno es el principal (el
 * primero lo es siempre) y siempre queda al menos uno.
 */
@Service
public class TelefonosServicio {

    private final NamedParameterJdbcTemplate jdbc;
    private final Catalogos catalogos;
    private final Auditoria auditoria;

    public TelefonosServicio(NamedParameterJdbcTemplate jdbc, Catalogos catalogos, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.catalogos = catalogos;
        this.auditoria = auditoria;
    }

    /** Documento del cliente (tipo y número), la clave de sus teléfonos. Vacío si el cliente no tiene documento. */
    public Optional<MapSqlParameterSource> documento(long clienteId) {
        return Filas.una(jdbc, "SELECT tipo_documento_identidad_id, numero_documento FROM cliente WHERE id = :id", Map.of("id", clienteId))
                .filter(f -> f.get("numero_documento") != null)
                .map(f -> new MapSqlParameterSource("tipoDoc", f.get("tipo_documento_identidad_id")).addValue("numero", f.get("numero_documento")));
    }

    public List<Map<String, Object>> deCliente(long clienteId) {
        return documento(clienteId).map(d -> Filas.lista(jdbc, "SELECT t.*, p.prefijo_telefonico, p.nombre AS pais FROM cliente_telefono t "
                + "JOIN pais p ON p.id = t.pais_id WHERE t.tipo_documento_identidad_id = :tipoDoc AND t.numero_documento = :numero "
                + "ORDER BY t.es_principal DESC, t.id", d)).orElse(List.of());
    }

    public Optional<TelefonoForm> telefono(long clienteId, long telefonoId) {
        return documento(clienteId).flatMap(d -> Filas.una(jdbc, "SELECT * FROM cliente_telefono WHERE id = :t "
                + "AND tipo_documento_identidad_id = :tipoDoc AND numero_documento = :numero",
                Map.of("t", telefonoId, "tipoDoc", d.getValue("tipoDoc"), "numero", d.getValue("numero")))).map(f -> {
            TelefonoForm t = new TelefonoForm();
            t.setId(telefonoId);
            t.setPaisId(((Number) f.get("pais_id")).longValue());
            t.setNumero((String) f.get("numero"));
            t.setEsPrincipal(Boolean.TRUE.equals(f.get("es_principal")));
            return t;
        });
    }

    public Errores validar(long clienteId, TelefonoForm t) {
        Errores e = new Errores();
        Long anterior = t.getId() == null ? null : telefono(clienteId, t.getId()).map(TelefonoForm::getPaisId).orElse(null);
        if (t.getPaisId() == null) e.campo("paisId", "Elija el prefijo.");
        else if (catalogos.prefijos(anterior).stream().noneMatch(o -> o.getId().equals(t.getPaisId()))) e.campo("paisId", "Elija el prefijo de la lista.");
        if (t.getNumero() == null) e.campo("numero", "Número es requerido.");
        else if (!t.getNumero().matches(ClientesServicio.PATRON_NUMERO)) {
            e.campo("numero", "El número va sin prefijo: dígitos, espacios y guiones (6 a 20).");
        } else if (t.getPaisId() != null) {
            MapSqlParameterSource d = documento(clienteId).orElseThrow();
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM cliente_telefono WHERE tipo_documento_identidad_id = :tipoDoc "
                    + "AND numero_documento = :numero AND pais_id = :pais AND numero = :tel AND id <> :id",
                    d.addValue("pais", t.getPaisId()).addValue("tel", t.getNumero()).addValue("id", t.getId() == null ? -1L : t.getId()), Integer.class);
            if (n != null && n > 0) e.campo("numero", "El cliente ya tiene ese teléfono.");
        }
        return e;
    }

    @Transactional
    public long crear(long clienteId, TelefonoForm t) {
        MapSqlParameterSource d = documento(clienteId).orElseThrow();
        boolean primero = deCliente(clienteId).isEmpty();
        if (t.isEsPrincipal() || primero) quitarPrincipal(d);
        Long id = jdbc.queryForObject("INSERT INTO cliente_telefono (tipo_documento_identidad_id, numero_documento, pais_id, numero, "
                + "es_principal, creado_por) OUTPUT inserted.id VALUES (:tipoDoc, :numero, :pais, :tel, :principal, :yo)",
                parametros(d, t, t.isEsPrincipal() || primero), Long.class);
        auditoria.registrar(Auditoria.ALTA, "cliente_telefono", id, null, paraAuditoria(clienteId, t));
        return id;
    }

    @Transactional
    public void modificar(long clienteId, TelefonoForm t) {
        MapSqlParameterSource d = documento(clienteId).orElseThrow();
        TelefonoForm antes = telefono(clienteId, t.getId()).orElseThrow();
        // El principal no deja de serlo desmarcándolo: se elige otro como principal.
        boolean principal = t.isEsPrincipal() || antes.isEsPrincipal();
        if (principal && !antes.isEsPrincipal()) quitarPrincipal(d);
        jdbc.update("UPDATE cliente_telefono SET pais_id = :pais, numero = :tel, es_principal = :principal, "
                + "modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                parametros(d, t, principal).addValue("id", t.getId()));
        t.setEsPrincipal(principal);
        auditoria.registrar(Auditoria.MODIFICACION, "cliente_telefono", t.getId(), paraAuditoria(clienteId, antes), paraAuditoria(clienteId, t));
    }

    /** Quita un teléfono. No se puede quitar el único; si era el principal, pasa a serlo el más antiguo. */
    @Transactional
    public Optional<String> eliminar(long clienteId, long telefonoId) {
        MapSqlParameterSource d = documento(clienteId).orElseThrow();
        TelefonoForm antes = telefono(clienteId, telefonoId).orElseThrow();
        if (deCliente(clienteId).size() <= 1) return Optional.of("El cliente debe tener al menos un teléfono: agregue otro antes de quitar este.");
        jdbc.update("DELETE FROM cliente_telefono WHERE id = :id", Map.of("id", telefonoId));
        if (antes.isEsPrincipal()) {
            jdbc.update("UPDATE cliente_telefono SET es_principal = 1 WHERE id = (SELECT MIN(id) FROM cliente_telefono "
                    + "WHERE tipo_documento_identidad_id = :tipoDoc AND numero_documento = :numero)", d);
        }
        auditoria.registrar(Auditoria.BAJA, "cliente_telefono", telefonoId, paraAuditoria(clienteId, antes), null);
        return Optional.empty();
    }

    private void quitarPrincipal(MapSqlParameterSource d) {
        jdbc.update("UPDATE cliente_telefono SET es_principal = 0 WHERE tipo_documento_identidad_id = :tipoDoc "
                + "AND numero_documento = :numero AND es_principal = 1", d);
    }

    private static MapSqlParameterSource parametros(MapSqlParameterSource d, TelefonoForm t, boolean principal) {
        return new MapSqlParameterSource("tipoDoc", d.getValue("tipoDoc")).addValue("numero", d.getValue("numero"))
                .addValue("pais", t.getPaisId()).addValue("tel", t.getNumero()).addValue("principal", principal)
                .addValue("yo", UsuarioActual.id().orElse(null));
    }

    private static Map<String, Object> paraAuditoria(long clienteId, TelefonoForm t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cliente_id", clienteId);
        m.put("pais_id", t.getPaisId());
        m.put("numero", t.getNumero());
        m.put("es_principal", t.isEsPrincipal());
        return m;
    }
}
