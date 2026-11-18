package com.ejemplo.plantilla.expedientes;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/** Una actuación de la bitácora del expediente. La fecha llega como texto para validarla con mensaje propio. */
public class ActuacionForm {

    private Long id;
    private Long tipoActuacionId;
    private String fecha;
    private String descripcion;

    public void normalizar() {
        fecha = Textos.limpio(fecha);
        descripcion = Textos.multilinea(descripcion);
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo_actuacion_id", tipoActuacionId);
        m.put("fecha", fecha);
        m.put("descripcion", descripcion);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTipoActuacionId() { return tipoActuacionId; }
    public void setTipoActuacionId(Long tipoActuacionId) { this.tipoActuacionId = tipoActuacionId; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
