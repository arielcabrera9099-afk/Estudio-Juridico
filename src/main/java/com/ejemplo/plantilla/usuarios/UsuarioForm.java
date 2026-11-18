package com.ejemplo.plantilla.usuarios;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Datos del formulario de usuario (alta y modificación). La clave solo se usa en el alta. */
public class UsuarioForm {

    private Long id;
    private String nombreUsuario;
    private String email;
    private String nombres;
    private String apellidos;
    private String telefono;
    private String matricula;
    private List<Long> roles = new ArrayList<>();
    private boolean activo = true;
    private String clave;
    private String claveRepetir;

    /** Quita espacios de más, pasa a minúsculas el usuario y el email, y deja en null los textos vacíos. */
    public void normalizar() {
        nombreUsuario = minusculas(nombreUsuario);
        email = minusculas(email);
        nombres = limpio(nombres);
        apellidos = limpio(apellidos);
        telefono = limpio(telefono);
        matricula = limpio(matricula);
        if (roles == null) roles = new ArrayList<>();
    }

    /** Los datos para la auditoría (sin la clave). */
    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre_usuario", nombreUsuario);
        m.put("email", email);
        m.put("nombres", nombres);
        m.put("apellidos", apellidos);
        m.put("telefono", telefono);
        m.put("matricula", matricula);
        m.put("roles", roles);
        m.put("activo", activo);
        return m;
    }

    static String limpio(String s) {
        return com.ejemplo.plantilla.web.Textos.limpio(s);
    }

    private static String minusculas(String s) {
        return com.ejemplo.plantilla.web.Textos.minusculas(s);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }
    public List<Long> getRoles() { return roles; }
    public void setRoles(List<Long> roles) { this.roles = roles; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getClaveRepetir() { return claveRepetir; }
    public void setClaveRepetir(String claveRepetir) { this.claveRepetir = claveRepetir; }
}
