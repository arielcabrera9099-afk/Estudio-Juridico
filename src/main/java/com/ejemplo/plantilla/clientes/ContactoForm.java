package com.ejemplo.plantilla.clientes;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ejemplo.plantilla.web.Textos;

/** Persona de contacto de un cliente (gerente, apoderado…). */
public class ContactoForm {

    private Long id;
    private String nombre;
    private String cargo;
    private String email;
    private String telefono;
    private boolean esPrincipal;

    public void normalizar() {
        nombre = Textos.limpio(nombre);
        cargo = Textos.limpio(cargo);
        email = Textos.minusculas(email);
        telefono = Textos.limpio(telefono);
    }

    public Map<String, Object> paraAuditoria() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("cargo", cargo);
        m.put("email", email);
        m.put("telefono", telefono);
        m.put("es_principal", esPrincipal);
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public boolean isEsPrincipal() { return esPrincipal; }
    public void setEsPrincipal(boolean esPrincipal) { this.esPrincipal = esPrincipal; }
}
