package com.ejemplo.plantilla.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identidad de la aplicación en el layout (menú lateral, título y login): {@code app.*} en application.yml.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Nombre que se muestra en la franja de marca del menú y en el login. */
    private String nombre = "Plantilla";
    /** Segunda línea debajo del nombre; vacío = sin subtítulo. */
    private String subtitulo = "";
    /** Ruta del logo dentro de static/ (por ejemplo /img/logo.png); vacío = la inicial del nombre. */
    private String logo = "";

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSubtitulo() { return subtitulo; }
    public void setSubtitulo(String subtitulo) { this.subtitulo = subtitulo; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    /** Inicial del nombre, para cuando no hay logo. */
    public String getInicial() {
        String n = nombre == null ? "" : nombre.trim();
        return n.isEmpty() ? "—" : n.substring(0, 1).toUpperCase();
    }
}
