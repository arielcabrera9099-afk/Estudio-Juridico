package com.ejemplo.plantilla;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Arranque dentro de un Tomcat externo (el WAR copiado a webapps/). Con {@code java -jar} no se usa: arranca
 * {@link PlantillaApplication#main}.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder app) {
        return app.sources(PlantillaApplication.class);
    }
}
