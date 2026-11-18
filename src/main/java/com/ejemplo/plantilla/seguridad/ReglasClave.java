package com.ejemplo.plantilla.seguridad;

import java.util.Optional;

/** Reglas de una contraseña nueva. Devuelven el motivo del rechazo, o vacío si la clave sirve. */
public final class ReglasClave {

    public static final int MINIMO = 8;
    public static final int MAXIMO = 64;
    public static final String DESCRIPCION = "Al menos " + MINIMO + " caracteres, con letras y números.";

    private ReglasClave() {
    }

    public static Optional<String> validar(String clave, String nombreUsuario) {
        if (clave == null || clave.isEmpty()) return Optional.of("La contraseña es requerida.");
        if (clave.length() < MINIMO) return Optional.of("La contraseña debe tener al menos " + MINIMO + " caracteres.");
        if (clave.length() > MAXIMO) return Optional.of("La contraseña no puede tener más de " + MAXIMO + " caracteres.");
        if (!clave.matches(".*\\p{L}.*") || !clave.matches(".*\\d.*")) {
            return Optional.of("La contraseña debe tener letras y números.");
        }
        if (nombreUsuario != null && !nombreUsuario.isBlank()
                && clave.toLowerCase().contains(nombreUsuario.trim().toLowerCase())) {
            return Optional.of("La contraseña no puede contener el nombre de usuario.");
        }
        return Optional.empty();
    }
}
