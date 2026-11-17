package com.ejemplo.plantilla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Plantilla de frontend: layout, sistema de diseño y componentes, sin pantallas de negocio.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PlantillaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlantillaApplication.class, args);
    }
}
