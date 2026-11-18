package com.ejemplo.plantilla.expedientes;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/** Una parte del expediente (contraparte, terceros): no son clientes del estudio. */
public class ParteForm {

    private Long id;
    private Long rolParteId;
    private String nombre;
    private String numeroDocumento;
    private String abogado;
    private String contacto;
    private String observaciones;

    public void normalizar() {
        nombre = Textos.limpio(nombre);
        numeroDocumento = Textos.limpio(numeroDocumento);
        if (numeroDocumento != null) numeroDocumento = numeroDocumento.toUpperCase();
        abogado = Textos.limpio(abogado);
        contacto = Textos.limpio(contacto);
        observaciones = Textos.multilinea(observaciones);
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rol_parte_id", rolParteId);
        m.put("nombre", nombre);
        m.put("numero_documento", numeroDocumento);
        m.put("abogado", abogado);
        m.put("contacto", contacto);
        m.put("observaciones", observaciones);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRolParteId() { return rolParteId; }
    public void setRolParteId(Long rolParteId) { this.rolParteId = rolParteId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public String getAbogado() { return abogado; }
    public void setAbogado(String abogado) { this.abogado = abogado; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
