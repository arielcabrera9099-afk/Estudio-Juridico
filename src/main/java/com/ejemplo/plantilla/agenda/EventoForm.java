package com.ejemplo.plantilla.agenda;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/**
 * Datos del formulario de un evento de agenda. Fecha y horas llegan como texto (hora local de Paraguay) para validarlas
 * con mensajes propios; el evento dura dentro de un mismo día.
 */
public class EventoForm {

    private Long id;
    private Long tipoEventoId;
    private String titulo;
    private Long expedienteId;
    private String fecha;
    private boolean todoElDia;
    private String horaInicio;
    private String horaFin;
    private String lugar;
    private Long juzgadoId;
    private Long responsableId;
    private String descripcion;

    public void normalizar() {
        titulo = Textos.limpio(titulo);
        fecha = Textos.limpio(fecha);
        horaInicio = Textos.limpio(horaInicio);
        horaFin = Textos.limpio(horaFin);
        lugar = Textos.limpio(lugar);
        descripcion = Textos.multilinea(descripcion);
        if (todoElDia) {
            horaInicio = null;
            horaFin = null;
        }
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo_evento_id", tipoEventoId);
        m.put("titulo", titulo);
        m.put("expediente_id", expedienteId);
        m.put("fecha", fecha);
        m.put("todo_el_dia", todoElDia);
        m.put("hora_inicio", horaInicio);
        m.put("hora_fin", horaFin);
        m.put("lugar", lugar);
        m.put("juzgado_id", juzgadoId);
        m.put("responsable_id", responsableId);
        m.put("descripcion", descripcion);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTipoEventoId() { return tipoEventoId; }
    public void setTipoEventoId(Long tipoEventoId) { this.tipoEventoId = tipoEventoId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public Long getExpedienteId() { return expedienteId; }
    public void setExpedienteId(Long expedienteId) { this.expedienteId = expedienteId; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public boolean isTodoElDia() { return todoElDia; }
    public void setTodoElDia(boolean todoElDia) { this.todoElDia = todoElDia; }
    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }
    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = horaFin; }
    public String getLugar() { return lugar; }
    public void setLugar(String lugar) { this.lugar = lugar; }
    public Long getJuzgadoId() { return juzgadoId; }
    public void setJuzgadoId(Long juzgadoId) { this.juzgadoId = juzgadoId; }
    public Long getResponsableId() { return responsableId; }
    public void setResponsableId(Long responsableId) { this.responsableId = responsableId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
