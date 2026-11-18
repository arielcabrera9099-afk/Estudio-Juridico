package com.ejemplo.plantilla.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Ingreso con usuario y clave (formulario /login), salida por POST /salir y CSRF en todos los formularios. Cada
 * pantalla solo la abre quien la tiene en su menú ({@link AccesoPorMenu}, según las opciones del menú de sus roles).
 * Las acciones dentro de cada pantalla (…_EDITAR, …_CERRAR, …_ELIMINAR) las controla cada controlador.
 */
@Configuration
public class SeguridadConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, ManejadorIngreso manejador, AccesoPorMenu accesoPorMenu) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .antMatchers("/login", "/error", "/css/**", "/js/**", "/fonts/**", "/img/**").permitAll()
                        .anyRequest().access(accesoPorMenu))
                .formLogin(f -> f.loginPage("/login").successHandler(manejador).failureHandler(manejador).permitAll())
                .logout(l -> l.logoutUrl("/salir").addLogoutHandler(manejador).logoutSuccessUrl("/login?salio"))
                .addFilterBefore(new FiltroCambioClave(), AuthorizationFilter.class);
        return http.build();
    }
}
