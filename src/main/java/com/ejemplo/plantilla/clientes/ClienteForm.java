package com.ejemplo.plantilla.clientes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/** Datos del formulario de cliente: persona física (F) o jurídica (J). */
public class ClienteForm {

    private Long id;
    private String tipoPersona = "F";
    private String nombres;
    private String apellidos;
    private String razonSocial;
    private Long tipoDocumentoIdentidadId;
    private String numeroDocumento;
    private LocalDate fechaNacimiento;
    private String email;
    private String telefono;
    /** País y departamento: solo para elegir la ciudad en cascada; se guarda la ciudad. */
    /** País del prefijo del teléfono principal (+595…). */
    private Long telefonoPaisId;
    private Long paisId;
    private Long departamentoId;
    private String direccion;
    private Long ciudadId;
    private String observaciones;
    private boolean activo = true;

    /**
     * Limpia los textos y deja solo los datos del tipo de persona: una persona jurídica no tiene nombres, apellidos ni
     * fecha de nacimiento, y una física no tiene razón social. El número de documento pierde puntos y espacios
     * (1.234.567 → 1234567).
     */
    public void normalizar() {
        tipoPersona = tipoPersona == null ? null : tipoPersona.trim().toUpperCase();
        nombres = Textos.limpio(nombres);
        apellidos = Textos.limpio(apellidos);
        razonSocial = Textos.limpio(razonSocial);
        numeroDocumento = Textos.limpio(numeroDocumento);
        if (numeroDocumento != null) numeroDocumento = numeroDocumento.replaceAll("[.\\s]", "").toUpperCase();
        if (numeroDocumento != null && numeroDocumento.isEmpty()) numeroDocumento = null;
        email = Textos.minusculas(email);
        telefono = Textos.limpio(telefono);
        direccion = Textos.limpio(direccion);
        observaciones = Textos.multilinea(observaciones);
        if ("J".equals(tipoPersona)) {
            nombres = null;
            apellidos = null;
            fechaNacimiento = null;
        } else if ("F".equals(tipoPersona)) {
            razonSocial = null;
        }
    }

    /** El nombre para mostrar: "Apellidos, Nombres" o la razón social. */
    public String getNombreMostrar() {
        return "J".equals(tipoPersona) ? razonSocial : apellidos + ", " + nombres;
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo_persona", tipoPersona);
        m.put("nombres", nombres);
        m.put("apellidos", apellidos);
        m.put("razon_social", razonSocial);
        m.put("tipo_documento_identidad_id", tipoDocumentoIdentidadId);
        m.put("numero_documento", numeroDocumento);
        m.put("fecha_nacimiento", fechaNacimiento);
        m.put("email", email);
        m.put("telefono_pais_id", telefonoPaisId);
        m.put("telefono", telefono);
        m.put("direccion", direccion);
        m.put("ciudad_id", ciudadId);
        m.put("observaciones", observaciones);
        m.put("activo", activo);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTipoPersona() { return tipoPersona; }
    public void setTipoPersona(String tipoPersona) { this.tipoPersona = tipoPersona; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public Long getTipoDocumentoIdentidadId() { return tipoDocumentoIdentidadId; }
    public void setTipoDocumentoIdentidadId(Long v) { this.tipoDocumentoIdentidadId = v; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Long getTelefonoPaisId() { return telefonoPaisId; }
    public void setTelefonoPaisId(Long telefonoPaisId) { this.telefonoPaisId = telefonoPaisId; }
    public Long getPaisId() { return paisId; }
    public void setPaisId(Long paisId) { this.paisId = paisId; }
    public Long getDepartamentoId() { return departamentoId; }
    public void setDepartamentoId(Long departamentoId) { this.departamentoId = departamentoId; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public Long getCiudadId() { return ciudadId; }
    public void setCiudadId(Long ciudadId) { this.ciudadId = ciudadId; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
