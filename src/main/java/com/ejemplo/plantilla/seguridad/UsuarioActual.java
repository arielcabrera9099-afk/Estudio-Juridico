package com.ejemplo.plantilla.seguridad;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al usuario de la sesión desde cualquier parte del código. */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static Optional<UsuarioSesion> sesion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UsuarioSesion
                ? Optional.of((UsuarioSesion) a.getPrincipal()) : Optional.empty();
    }

    public static Optional<Long> id() {
        return sesion().map(UsuarioSesion::getId);
    }

    public static boolean tiene(String permiso) {
        return sesion().map(u -> u.tiene(permiso)).orElse(false);
    }

    /** Corta con 403 (página "Sin permiso") si el usuario no tiene el permiso: para las acciones de edición. */
    public static void exigir(String permiso) {
        if (!tiene(permiso)) throw new AccessDeniedException("Falta el permiso " + permiso);
    }
}
