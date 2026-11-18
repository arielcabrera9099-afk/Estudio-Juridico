package com.ejemplo.plantilla.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * El usuario de la sesión: sus datos para la cabecera, sus roles y sus permisos (las authorities son los códigos de
 * {@code permiso}, como CLIENTES_VER). Los permisos se cargan al ingresar; un cambio de roles rige desde el próximo
 * ingreso.
 */
public class UsuarioSesion implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String usuario;
    private final String claveHash;
    private final String nombre;
    private final List<String> roles;
    private final Set<String> permisos;
    private final boolean activo;
    private final boolean bloqueado;
    private volatile boolean debeCambiarClave;

    public UsuarioSesion(long id, String usuario, String claveHash, String nombre, List<String> roles,
                         Set<String> permisos, boolean activo, boolean bloqueado, boolean debeCambiarClave) {
        this.id = id;
        this.usuario = usuario;
        this.claveHash = claveHash;
        this.nombre = nombre;
        this.roles = List.copyOf(roles);
        this.permisos = Set.copyOf(permisos);
        this.activo = activo;
        this.bloqueado = bloqueado;
        this.debeCambiarClave = debeCambiarClave;
    }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public List<String> getRoles() { return roles; }
    public Set<String> getPermisos() { return permisos; }
    public boolean tiene(String permiso) { return permisos.contains(permiso); }
    public boolean isDebeCambiarClave() { return debeCambiarClave; }
    public void claveCambiada() { this.debeCambiarClave = false; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permisos.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    @Override public String getPassword() { return claveHash; }
    @Override public String getUsername() { return usuario; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !bloqueado; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return activo; }
}
