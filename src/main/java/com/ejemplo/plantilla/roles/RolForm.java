package com.ejemplo.plantilla.roles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/** Datos del formulario de rol (alta y modificación). El código no cambia después del alta. */
public class RolForm {

    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private List<Long> permisos = new ArrayList<>();
    private boolean activo = true;

    /** Código en mayúsculas con guion bajo en vez de espacios; textos sin espacios de más. */
    public void normalizar() {
        codigo = Textos.limpio(codigo);
        if (codigo != null) codigo = codigo.toUpperCase().replace(' ', '_');
        nombre = Textos.limpio(nombre);
        descripcion = Textos.multilinea(descripcion);
        if (permisos == null) permisos = new ArrayList<>();
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("nombre", nombre);
        m.put("descripcion", descripcion);
        m.put("permisos", permisos);
        m.put("activo", activo);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public List<Long> getPermisos() { return permisos; }
    public void setPermisos(List<Long> permisos) { this.permisos = permisos; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
