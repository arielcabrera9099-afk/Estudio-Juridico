package com.ejemplo.plantilla.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identidad de la aplicación en el layout (menú lateral, título y login) y el administrador inicial:
 * {@code app.*} en application.yml.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Nombre que se muestra en la franja de marca del menú y en el login. */
    private String nombre = "Plantilla";
    /** Segunda línea debajo del nombre; vacío = sin subtítulo. */
    private String subtitulo = "";
    /** Ruta del logo dentro de static/ (por ejemplo /img/logo.png); vacío = la inicial del nombre. */
    private String logo = "";
    /** Administrador que se crea al arrancar si la base no tiene usuarios. */
    private final Admin admin = new Admin();

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSubtitulo() { return subtitulo; }
    public void setSubtitulo(String subtitulo) { this.subtitulo = subtitulo; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public Admin getAdmin() { return admin; }

    /** Inicial del nombre, para cuando no hay logo. */
    public String getInicial() {
        String n = nombre == null ? "" : nombre.trim();
        return n.isEmpty() ? "—" : n.substring(0, 1).toUpperCase();
    }

    /** {@code app.admin.*}: el primer usuario del sistema. */
    public static class Admin {
        private String usuario = "admin";
        private String email = "admin@estudio.local";
        /** Clave inicial (variable de entorno APP_ADMIN_CLAVE). Vacía: se genera una al azar y se muestra en el log. */
        private String claveInicial = "";

        public String getUsuario() { return usuario; }
        public void setUsuario(String usuario) { this.usuario = usuario; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getClaveInicial() { return claveInicial; }
        public void setClaveInicial(String claveInicial) { this.claveInicial = claveInicial; }
    }
}
