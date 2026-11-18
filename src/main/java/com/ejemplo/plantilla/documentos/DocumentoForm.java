package com.ejemplo.plantilla.documentos;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/** Datos de un documento (sin el archivo, que llega aparte en el alta). La fecha llega como texto. */
public class DocumentoForm {

    private Long id;
    private Long expedienteId;
    private Long tipoDocumentoId;
    private String nombre;
    private String descripcion;
    private String fechaDocumento;
    private Long actuacionId;

    public void normalizar() {
        nombre = Textos.limpio(nombre);
        descripcion = Textos.multilinea(descripcion);
        fechaDocumento = Textos.limpio(fechaDocumento);
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expediente_id", expedienteId);
        m.put("tipo_documento_id", tipoDocumentoId);
        m.put("nombre", nombre);
        m.put("descripcion", descripcion);
        m.put("fecha_documento", fechaDocumento);
        m.put("actuacion_id", actuacionId);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExpedienteId() { return expedienteId; }
    public void setExpedienteId(Long expedienteId) { this.expedienteId = expedienteId; }
    public Long getTipoDocumentoId() { return tipoDocumentoId; }
    public void setTipoDocumentoId(Long tipoDocumentoId) { this.tipoDocumentoId = tipoDocumentoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getFechaDocumento() { return fechaDocumento; }
    public void setFechaDocumento(String fechaDocumento) { this.fechaDocumento = fechaDocumento; }
    public Long getActuacionId() { return actuacionId; }
    public void setActuacionId(Long actuacionId) { this.actuacionId = actuacionId; }
}
