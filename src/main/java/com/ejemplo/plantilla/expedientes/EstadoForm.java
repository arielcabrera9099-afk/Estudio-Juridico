package com.ejemplo.plantilla.expedientes;

import com.ejemplo.plantilla.web.Textos;

/** Cambio de estado del expediente. Fecha y motivo de cierre solo cuando el estado nuevo es de cierre. */
public class EstadoForm {

    private Long estadoId;
    private String fechaCierre;
    private String motivoCierre;
    private String observacion;

    public void normalizar() {
        fechaCierre = Textos.limpio(fechaCierre);
        motivoCierre = Textos.multilinea(motivoCierre);
        observacion = Textos.multilinea(observacion);
    }

    public Long getEstadoId() { return estadoId; }
    public void setEstadoId(Long estadoId) { this.estadoId = estadoId; }
    public String getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(String fechaCierre) { this.fechaCierre = fechaCierre; }
    public String getMotivoCierre() { return motivoCierre; }
    public void setMotivoCierre(String motivoCierre) { this.motivoCierre = motivoCierre; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
