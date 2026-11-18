package com.ejemplo.plantilla.expedientes;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/**
 * Datos del formulario de expediente (alta y modificación). Las fechas, el año y el monto llegan como texto para
 * validarlos con mensajes propios. El estado solo se elige en el alta; después se cambia con "Cambiar estado".
 */
public class ExpedienteForm {

    private Long id;
    private String numeroInterno;
    private String caratula;
    private Long clienteId;
    private Long abogadoId;
    private Long materiaId;
    private Long tipoProcesoId;
    private Long juzgadoId;
    private String numeroJudicial;
    private String anioJudicial;
    private Long estadoId;
    private String fechaInicio;
    private String descripcion;
    private String monto;
    private String moneda = "PYG";

    public void normalizar() {
        caratula = Textos.limpio(caratula);
        numeroJudicial = Textos.limpio(numeroJudicial);
        anioJudicial = Textos.limpio(anioJudicial);
        fechaInicio = Textos.limpio(fechaInicio);
        descripcion = Textos.multilinea(descripcion);
        monto = Textos.limpio(monto);
        moneda = Textos.limpio(moneda);
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numero_interno", numeroInterno);
        m.put("caratula", caratula);
        m.put("cliente_id", clienteId);
        m.put("abogado_responsable_id", abogadoId);
        m.put("materia_id", materiaId);
        m.put("tipo_proceso_id", tipoProcesoId);
        m.put("juzgado_id", juzgadoId);
        m.put("numero_expediente_judicial", numeroJudicial);
        m.put("anio_judicial", anioJudicial);
        m.put("fecha_inicio", fechaInicio);
        m.put("descripcion", descripcion);
        m.put("monto_reclamado", monto);
        m.put("moneda", moneda);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNumeroInterno() { return numeroInterno; }
    public void setNumeroInterno(String numeroInterno) { this.numeroInterno = numeroInterno; }
    public String getCaratula() { return caratula; }
    public void setCaratula(String caratula) { this.caratula = caratula; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public Long getAbogadoId() { return abogadoId; }
    public void setAbogadoId(Long abogadoId) { this.abogadoId = abogadoId; }
    public Long getMateriaId() { return materiaId; }
    public void setMateriaId(Long materiaId) { this.materiaId = materiaId; }
    public Long getTipoProcesoId() { return tipoProcesoId; }
    public void setTipoProcesoId(Long tipoProcesoId) { this.tipoProcesoId = tipoProcesoId; }
    public Long getJuzgadoId() { return juzgadoId; }
    public void setJuzgadoId(Long juzgadoId) { this.juzgadoId = juzgadoId; }
    public String getNumeroJudicial() { return numeroJudicial; }
    public void setNumeroJudicial(String numeroJudicial) { this.numeroJudicial = numeroJudicial; }
    public String getAnioJudicial() { return anioJudicial; }
    public void setAnioJudicial(String anioJudicial) { this.anioJudicial = anioJudicial; }
    public Long getEstadoId() { return estadoId; }
    public void setEstadoId(Long estadoId) { this.estadoId = estadoId; }
    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getMonto() { return monto; }
    public void setMonto(String monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
}
