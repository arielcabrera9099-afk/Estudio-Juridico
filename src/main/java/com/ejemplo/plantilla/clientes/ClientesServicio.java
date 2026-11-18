package com.ejemplo.plantilla.clientes;

import java.time.LocalDate;
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
 * Clientes del estudio (personas físicas o jurídicas), sus contactos y su historial de casos. Reglas:
 * <ul>
 *   <li>Persona física: nombres y apellidos; fecha de nacimiento opcional, no futura. Persona jurídica: razón social.</li>
 *   <li>Tipo y número de documento obligatorios; formato según el tipo (cédula: solo números; RUC: número,
 *       guion y dígito verificador; pasaporte: letras y números) y único por tipo.</li>
 *   <li>Teléfono obligatorio (con prefijo de país si no es de Paraguay). Email con formato válido.</li>
 *   <li>La baja es lógica (el cliente conserva su historial) y no se permite con casos activos.</li>
 *   <li>Un solo contacto principal por cliente; el primer contacto lo es siempre.</li>
 * </ul>
 */
@Service
public class ClientesServicio {

    public static final int MAXIMO_FILAS = 500;
    static final String PATRON_EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    /** Números, espacios, +, guiones, barras y paréntesis: "+595 981 123 456", "021 600 000 / 0981 000 000". */
    static final String PATRON_TELEFONO = "^[0-9+()/\\s-]{6,30}$";

    private static final String NOMBRE = "CASE WHEN c.tipo_persona = 'J' THEN c.razon_social "
            + "ELSE CONCAT(c.apellidos, N', ', c.nombres) END";

    /** Teléfono principal del cliente (de cliente_telefono, por su documento): "+595 0981 123 456", su país y su número. */
    static final String TELEFONO_PRINCIPAL = " OUTER APPLY (SELECT TOP 1 CONCAT(pt.prefijo_telefonico, ' ', t.numero) AS telefono, "
            + "t.pais_id, t.numero FROM cliente_telefono t JOIN pais pt ON pt.id = t.pais_id "
            + "WHERE t.tipo_documento_identidad_id = c.tipo_documento_identidad_id AND t.numero_documento = c.numero_documento "
            + "ORDER BY t.es_principal DESC, t.id) tel";
    /** Número sin el prefijo: empieza con un dígito; dígitos, espacios y guiones (6 a 20). */
    static final String PATRON_NUMERO = "^[0-9][0-9 -]{5,19}$";

    private final NamedParameterJdbcTemplate jdbc;
    private final Catalogos catalogos;
    private final Auditoria auditoria;

    public ClientesServicio(NamedParameterJdbcTemplate jdbc, Catalogos catalogos, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.catalogos = catalogos;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    /** tipo: F, J o vacío; estado: ACTIVOS, INACTIVOS o vacío (todos). */
    public List<Map<String, Object>> buscar(String texto, String tipo, String estado) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (texto != null && !texto.isBlank()) {
            String t = texto.trim();
            w.append(" AND (c.nombres LIKE :texto OR c.apellidos LIKE :texto OR c.razon_social LIKE :texto "
                    + "OR CONCAT(c.nombres, ' ', c.apellidos) LIKE :texto OR CONCAT(c.apellidos, ' ', c.nombres) LIKE :texto "
                    + "OR c.email LIKE :texto OR c.numero_documento LIKE :documento)");
            p.addValue("texto", "%" + t + "%");
            p.addValue("documento", "%" + t.replaceAll("[.\\s]", "") + "%");
        }
        if ("F".equals(tipo) || "J".equals(tipo)) {
            w.append(" AND c.tipo_persona = :tipo");
            p.addValue("tipo", tipo);
        }
        if ("ACTIVOS".equals(estado)) w.append(" AND c.activo = 1");
        else if ("INACTIVOS".equals(estado)) w.append(" AND c.activo = 0");
        return Filas.lista(jdbc, "SELECT TOP " + (MAXIMO_FILAS + 1) + " c.id, c.tipo_persona, " + NOMBRE + " AS nombre, "
                + "td.codigo AS documento_tipo, c.numero_documento, tel.telefono, c.email, ci.nombre AS ciudad, "
                + "c.activo, "
                + "(SELECT COUNT(*) FROM expediente e JOIN estado_expediente ee ON ee.id = e.estado_expediente_id "
                + " WHERE e.cliente_id = c.id AND ee.es_activo = 1) AS casos_activos, "
                + "(SELECT COUNT(*) FROM expediente e WHERE e.cliente_id = c.id) AS casos "
                + "FROM cliente c LEFT JOIN tipo_documento_identidad td ON td.id = c.tipo_documento_identidad_id "
                + "LEFT JOIN ciudad ci ON ci.id = c.ciudad_id" + TELEFONO_PRINCIPAL + w + " ORDER BY " + NOMBRE, p);
    }

    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, "SELECT c.*, " + NOMBRE + " AS nombre, td.nombre AS documento_tipo_nombre, "
                + "CONCAT(ci.nombre, N' (', d.nombre, N', ', pa.nombre, N')') AS ciudad, d.id AS departamento_ui, d.pais_id AS pais_ui, "
                + "tel.telefono, tel.pais_id AS telefono_pais_id, tel.numero AS telefono_numero, "
                + "uc.nombre_usuario AS creado_por_usuario, um.nombre_usuario AS modificado_por_usuario "
                + "FROM cliente c LEFT JOIN tipo_documento_identidad td ON td.id = c.tipo_documento_identidad_id "
                + "LEFT JOIN ciudad ci ON ci.id = c.ciudad_id LEFT JOIN departamento d ON d.id = ci.departamento_id "
                + "LEFT JOIN pais pa ON pa.id = d.pais_id "
                + "LEFT JOIN usuario uc ON uc.id = c.creado_por LEFT JOIN usuario um ON um.id = c.modificado_por"
                + TELEFONO_PRINCIPAL + " "
                + "WHERE c.id = :id", Map.of("id", id));
    }

    public Optional<ClienteForm> formulario(long id) {
        return ficha(id).map(f -> {
            ClienteForm c = new ClienteForm();
            c.setId(id);
            c.setTipoPersona((String) f.get("tipo_persona"));
            c.setNombres((String) f.get("nombres"));
            c.setApellidos((String) f.get("apellidos"));
            c.setRazonSocial((String) f.get("razon_social"));
            c.setTipoDocumentoIdentidadId((Long) f.get("tipo_documento_identidad_id"));
            c.setNumeroDocumento((String) f.get("numero_documento"));
            c.setFechaNacimiento((LocalDate) f.get("fecha_nacimiento"));
            c.setEmail((String) f.get("email"));
            c.setTelefono((String) f.get("telefono_numero"));
            c.setTelefonoPaisId((Long) f.get("telefono_pais_id"));
            c.setDepartamentoId((Long) f.get("departamento_ui"));
            c.setPaisId((Long) f.get("pais_ui"));
            c.setDireccion((String) f.get("direccion"));
            c.setCiudadId((Long) f.get("ciudad_id"));
            c.setObservaciones((String) f.get("observaciones"));
            c.setActivo(Boolean.TRUE.equals(f.get("activo")));
            return c;
        });
    }

    /** Expedientes del cliente (vista v_historial_cliente), los más nuevos primero. */
    public List<Map<String, Object>> historial(long clienteId) {
        return Filas.lista(jdbc, "SELECT * FROM v_historial_cliente WHERE cliente_id = :id ORDER BY fecha_inicio DESC, expediente_id DESC",
                Map.of("id", clienteId));
    }

    public List<Map<String, Object>> contactos(long clienteId) {
        return Filas.lista(jdbc, "SELECT * FROM cliente_contacto WHERE cliente_id = :id ORDER BY es_principal DESC, nombre",
                Map.of("id", clienteId));
    }

    public Optional<ContactoForm> contacto(long clienteId, long contactoId) {
        return Filas.una(jdbc, "SELECT * FROM cliente_contacto WHERE id = :id AND cliente_id = :cliente",
                Map.of("id", contactoId, "cliente", clienteId)).map(f -> {
                    ContactoForm c = new ContactoForm();
                    c.setId(contactoId);
                    c.setNombre((String) f.get("nombre"));
                    c.setCargo((String) f.get("cargo"));
                    c.setEmail((String) f.get("email"));
                    c.setTelefono((String) f.get("telefono"));
                    c.setEsPrincipal(Boolean.TRUE.equals(f.get("es_principal")));
                    return c;
                });
    }

    // ------------------------------------------------------------------------------------------------ validación

    public Errores validar(ClienteForm f) {
        Errores e = new Errores();
        if (!"F".equals(f.getTipoPersona()) && !"J".equals(f.getTipoPersona())) {
            e.campo("tipoPersona", "Elija si es persona física o jurídica.");
            return e;
        }
        if ("F".equals(f.getTipoPersona())) {
            requerido(e, "nombres", "Nombres", f.getNombres(), 80);
            requerido(e, "apellidos", "Apellidos", f.getApellidos(), 80);
            if (f.getFechaNacimiento() != null) {
                if (f.getFechaNacimiento().isAfter(LocalDate.now())) e.campo("fechaNacimiento", "La fecha de nacimiento no puede ser futura.");
                else if (f.getFechaNacimiento().getYear() < 1900) e.campo("fechaNacimiento", "La fecha de nacimiento no es válida.");
            }
        } else {
            requerido(e, "razonSocial", "Razón social", f.getRazonSocial(), 150);
        }

        Long tipoDoc = f.getTipoDocumentoIdentidadId();
        String numero = f.getNumeroDocumento();
        if (tipoDoc == null) e.campo("tipoDocumentoIdentidadId", "Tipo de documento es requerido.");
        else if (numero == null) e.campo("numeroDocumento", "Número de documento es requerido.");
        else {
            String codigo = codigoDocumento(tipoDoc);
            if (codigo == null) e.campo("tipoDocumentoIdentidadId", "El tipo de documento no existe.");
            else formatoDocumento(codigo, numero).ifPresentOrElse(m -> e.campo("numeroDocumento", m), () -> {
                if (documentoRepetido(tipoDoc, numero, f.getId())) {
                    e.campo("numeroDocumento", "Ya existe un cliente con ese documento.");
                }
            });
        }

        if (f.getEmail() != null && (f.getEmail().length() > 120 || !f.getEmail().matches(PATRON_EMAIL))) {
            e.campo("email", "El email no es válido.");
        }
        if (f.getTelefono() == null) e.campo("telefono", "Teléfono es requerido.");
        else if (!f.getTelefono().matches(PATRON_NUMERO)) {
            e.campo("telefono", "El número va sin prefijo: dígitos, espacios y guiones (6 a 20).");
        }
        if (f.getTelefonoPaisId() == null) e.campo("telefono", "Elija el prefijo del teléfono.");
        else if (catalogos.prefijos(f.getTelefonoPaisId()).stream().noneMatch(o -> o.getId().equals(f.getTelefonoPaisId()))) {
            e.campo("telefono", "Elija el prefijo de la lista.");
        }
        if (f.getDireccion() != null && f.getDireccion().length() > 200) e.campo("direccion", "Dirección admite hasta 200 caracteres.");
        if (f.getCiudadId() != null && catalogos.ciudades(ciudadAnterior(f.getId())).stream().noneMatch(o -> o.getId().equals(f.getCiudadId()))) {
            e.campo("ciudadId", "Elija la ciudad de la lista.");
        }
        if (f.getObservaciones() != null && f.getObservaciones().length() > 4000) {
            e.campo("observaciones", "Observaciones admite hasta 4000 caracteres.");
        }
        if (f.getId() != null && !f.isActivo() && casosActivos(f.getId()) > 0) {
            e.campo("activo", "No se puede dar de baja: el cliente tiene casos activos.");
        }
        return e;
    }

    public Errores validar(ContactoForm f) {
        Errores e = new Errores();
        requerido(e, "nombre", "Nombre", f.getNombre(), 120);
        if (f.getCargo() != null && f.getCargo().length() > 60) e.campo("cargo", "Cargo admite hasta 60 caracteres.");
        if (f.getEmail() != null && (f.getEmail().length() > 120 || !f.getEmail().matches(PATRON_EMAIL))) {
            e.campo("email", "El email no es válido.");
        }
        telefono(e, "telefono", f.getTelefono());
        if (f.getEmail() == null && f.getTelefono() == null) e.campo("telefono", "Ingrese al menos un teléfono o un email.");
        return e;
    }

    /** Formato del número según el tipo de documento; vacío si es correcto. */
    static Optional<String> formatoDocumento(String codigo, String numero) {
        if (numero.length() > 20) return Optional.of("El número de documento admite hasta 20 caracteres.");
        switch (codigo) {
            case "CI":
                return numero.matches("\\d{3,9}") ? Optional.empty()
                        : Optional.of("La cédula debe tener solo números (sin puntos), de 3 a 9 dígitos.");
            case "RUC":
                return numero.matches("\\d{3,8}-\\d") ? Optional.empty()
                        : Optional.of("El RUC debe tener el número, un guion y el dígito verificador (por ejemplo, 80012345-6).");
            default:
                return numero.matches("[A-Z0-9]{5,20}") ? Optional.empty()
                        : Optional.of("El pasaporte debe tener de 5 a 20 letras o números.");
        }
    }

    private static void requerido(Errores e, String campo, String nombre, String valor, int maximo) {
        if (valor == null) e.campo(campo, nombre + " es requerido.");
        else if (valor.length() > maximo) e.campo(campo, nombre + " admite hasta " + maximo + " caracteres.");
    }

    /** La ciudad guardada del cliente (se conserva aunque se haya dado de baja). */
    private Long ciudadAnterior(Long clienteId) {
        if (clienteId == null) return null;
        return jdbc.query("SELECT ciudad_id FROM cliente WHERE id = :id", Map.of("id", clienteId),
                rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null);
    }

    private static void telefono(Errores e, String campo, String valor) {
        if (valor != null && !valor.matches(PATRON_TELEFONO)) {
            e.campo(campo, "Solo números, espacios, +, guiones, barras y paréntesis (6 a 30 caracteres).");
        }
    }

    private String codigoDocumento(long tipoId) {
        List<String> l = jdbc.queryForList("SELECT codigo FROM tipo_documento_identidad WHERE id = :id AND activo = 1",
                Map.of("id", tipoId), String.class);
        return l.isEmpty() ? null : l.get(0);
    }

    private boolean documentoRepetido(long tipoId, String numero, Long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM cliente WHERE tipo_documento_identidad_id = :tipo "
                + "AND numero_documento = :numero AND id <> :id",
                Map.of("tipo", tipoId, "numero", numero, "id", id == null ? -1L : id), Integer.class);
        return n != null && n > 0;
    }

    public int casosActivos(long clienteId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM expediente e JOIN estado_expediente ee "
                + "ON ee.id = e.estado_expediente_id WHERE e.cliente_id = :id AND ee.es_activo = 1",
                Map.of("id", clienteId), Integer.class);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------------------------------------ cambios

    @Transactional
    public long crear(ClienteForm f) {
        Long id = jdbc.queryForObject("INSERT INTO cliente (tipo_persona, nombres, apellidos, razon_social, "
                + "tipo_documento_identidad_id, numero_documento, fecha_nacimiento, email, direccion, "
                + "ciudad_id, observaciones, activo, creado_por) OUTPUT inserted.id VALUES (:tipo, :nombres, :apellidos, "
                + ":razon, :tipoDoc, :numero, :nacimiento, :email, :direccion, :ciudad, :obs, 1, :yo)",
                parametros(f), Long.class);
        guardarTelefonoPrincipal(f);
        auditoria.registrar(Auditoria.ALTA, "cliente", id, null, f.paraAuditoria());
        return id;
    }

    /**
     * El teléfono del formulario es el principal del cliente: si ya tiene uno, se modifica; si no, se agrega. Va
     * después de guardar el cliente, con su documento ya actualizado (los teléfonos lo siguen por la clave foránea).
     */
    private void guardarTelefonoPrincipal(ClienteForm f) {
        MapSqlParameterSource p = new MapSqlParameterSource("tipoDoc", f.getTipoDocumentoIdentidadId())
                .addValue("numero", f.getNumeroDocumento()).addValue("pais", f.getTelefonoPaisId())
                .addValue("tel", f.getTelefono()).addValue("yo", UsuarioActual.id().orElse(null));
        // Si el número ya estaba como secundario, ese pasa a ser el principal.
        jdbc.update("DELETE FROM cliente_telefono WHERE tipo_documento_identidad_id = :tipoDoc AND numero_documento = :numero "
                + "AND pais_id = :pais AND numero = :tel AND es_principal = 0", p);
        int n = jdbc.update("UPDATE cliente_telefono SET pais_id = :pais, numero = :tel, modificado_en = SYSUTCDATETIME(), "
                + "modificado_por = :yo WHERE tipo_documento_identidad_id = :tipoDoc AND numero_documento = :numero AND es_principal = 1", p);
        if (n == 0) {
            jdbc.update("INSERT INTO cliente_telefono (tipo_documento_identidad_id, numero_documento, pais_id, numero, es_principal, "
                    + "creado_por) VALUES (:tipoDoc, :numero, :pais, :tel, 1, :yo)", p);
        }
    }

    @Transactional
    public void modificar(ClienteForm f) {
        ClienteForm antes = formulario(f.getId()).orElseThrow();
        jdbc.update("UPDATE cliente SET tipo_persona = :tipo, nombres = :nombres, apellidos = :apellidos, "
                + "razon_social = :razon, tipo_documento_identidad_id = :tipoDoc, numero_documento = :numero, "
                + "fecha_nacimiento = :nacimiento, email = :email, "
                + "direccion = :direccion, ciudad_id = :ciudad, observaciones = :obs, activo = :activo, "
                + "modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                parametros(f).addValue("id", f.getId()).addValue("activo", f.isActivo()));
        guardarTelefonoPrincipal(f);
        auditoria.registrar(Auditoria.MODIFICACION, "cliente", f.getId(), antes.paraAuditoria(), f.paraAuditoria());
    }

    /** Baja lógica. Devuelve el motivo si no se puede. */
    @Transactional
    public Optional<String> darDeBaja(long id) {
        int activos = casosActivos(id);
        if (activos > 0) {
            return Optional.of("No se puede dar de baja: el cliente tiene " + activos
                    + (activos == 1 ? " caso activo." : " casos activos.") + " Ciérrelos antes.");
        }
        cambiarActivo(id, false);
        return Optional.empty();
    }

    @Transactional
    public void reactivar(long id) {
        cambiarActivo(id, true);
    }

    private void cambiarActivo(long id, boolean activo) {
        jdbc.update("UPDATE cliente SET activo = :activo, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                Map.of("id", id, "activo", activo, "yo", UsuarioActual.id().orElseThrow()));
        auditoria.registrar(activo ? Auditoria.MODIFICACION : Auditoria.BAJA, "cliente", id,
                Map.of("activo", !activo), Map.of("activo", activo));
    }

    // ------------------------------------------------------------------------------------------------ contactos

    @Transactional
    public long crearContacto(long clienteId, ContactoForm f) {
        if (contactos(clienteId).isEmpty()) f.setEsPrincipal(true);
        if (f.isEsPrincipal()) quitarPrincipal(clienteId);
        Long id = jdbc.queryForObject("INSERT INTO cliente_contacto (cliente_id, nombre, cargo, email, telefono, es_principal, "
                + "creado_por) OUTPUT inserted.id VALUES (:cliente, :nombre, :cargo, :email, :telefono, :principal, :yo)",
                parametros(f).addValue("cliente", clienteId), Long.class);
        auditoria.registrar(Auditoria.ALTA, "cliente_contacto", id, null, f.paraAuditoria());
        return id;
    }

    @Transactional
    public void modificarContacto(long clienteId, ContactoForm f) {
        ContactoForm antes = contacto(clienteId, f.getId()).orElseThrow();
        // El principal no se puede desmarcar directamente: se elige otro contacto como principal.
        if (antes.isEsPrincipal()) f.setEsPrincipal(true);
        if (f.isEsPrincipal() && !antes.isEsPrincipal()) quitarPrincipal(clienteId);
        jdbc.update("UPDATE cliente_contacto SET nombre = :nombre, cargo = :cargo, email = :email, telefono = :telefono, "
                + "es_principal = :principal, modificado_en = SYSUTCDATETIME(), modificado_por = :yo "
                + "WHERE id = :id AND cliente_id = :cliente",
                parametros(f).addValue("id", f.getId()).addValue("cliente", clienteId));
        auditoria.registrar(Auditoria.MODIFICACION, "cliente_contacto", f.getId(), antes.paraAuditoria(), f.paraAuditoria());
    }

    /** Elimina el contacto; si era el principal, el primero que queda pasa a serlo. */
    @Transactional
    public void eliminarContacto(long clienteId, long contactoId) {
        ContactoForm antes = contacto(clienteId, contactoId).orElseThrow();
        jdbc.update("DELETE FROM cliente_contacto WHERE id = :id AND cliente_id = :cliente",
                Map.of("id", contactoId, "cliente", clienteId));
        if (antes.isEsPrincipal()) {
            jdbc.update("UPDATE cliente_contacto SET es_principal = 1 WHERE id = (SELECT TOP 1 id FROM cliente_contacto "
                    + "WHERE cliente_id = :cliente ORDER BY nombre)", Map.of("cliente", clienteId));
        }
        auditoria.registrar(Auditoria.BAJA, "cliente_contacto", contactoId, antes.paraAuditoria(), null);
    }

    private void quitarPrincipal(long clienteId) {
        jdbc.update("UPDATE cliente_contacto SET es_principal = 0 WHERE cliente_id = :cliente AND es_principal = 1",
                Map.of("cliente", clienteId));
    }

    private static MapSqlParameterSource parametros(ClienteForm f) {
        return new MapSqlParameterSource()
                .addValue("tipo", f.getTipoPersona())
                .addValue("nombres", f.getNombres())
                .addValue("apellidos", f.getApellidos())
                .addValue("razon", f.getRazonSocial())
                .addValue("tipoDoc", f.getTipoDocumentoIdentidadId())
                .addValue("numero", f.getNumeroDocumento())
                .addValue("nacimiento", f.getFechaNacimiento())
                .addValue("email", f.getEmail())
                .addValue("telefono", f.getTelefono())
                .addValue("direccion", f.getDireccion())
                .addValue("ciudad", f.getCiudadId())
                .addValue("obs", f.getObservaciones())
                .addValue("yo", UsuarioActual.id().orElse(null));
    }

    private static MapSqlParameterSource parametros(ContactoForm f) {
        return new MapSqlParameterSource()
                .addValue("nombre", f.getNombre())
                .addValue("cargo", f.getCargo())
                .addValue("email", f.getEmail())
                .addValue("telefono", f.getTelefono())
                .addValue("principal", f.isEsPrincipal())
                .addValue("yo", UsuarioActual.id().orElse(null));
    }
}
