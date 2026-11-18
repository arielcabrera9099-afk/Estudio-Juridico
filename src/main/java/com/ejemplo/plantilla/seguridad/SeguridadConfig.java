package com.ejemplo.plantilla.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Ingreso con usuario y clave (formulario /login), salida por POST /salir, CSRF en todos los formularios y permisos
 * por módulo según las rutas del menú. Los permisos de edición (…_EDITAR, …_CERRAR, …_ELIMINAR) los controla cada
 * pantalla.
 */
@Configuration
public class SeguridadConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, ManejadorIngreso manejador) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .antMatchers("/login", "/error", "/css/**", "/js/**", "/fonts/**", "/img/**").permitAll()
                        .antMatchers("/clientes/**").hasAuthority("CLIENTES_VER")
                        .antMatchers("/expedientes/**").hasAuthority("EXPEDIENTES_VER")
                        .antMatchers("/agenda/**").hasAuthority("AGENDA_VER")
                        .antMatchers("/documentos/**").hasAuthority("DOCUMENTOS_VER")
                        .antMatchers("/reportes/**").hasAuthority("REPORTES_VER")
                        .antMatchers("/admin/auditoria/**").hasAuthority("AUDITORIA_VER")
                        .antMatchers("/admin/usuarios/**", "/admin/roles/**", "/admin/parametros/**", "/componentes")
                        .hasAuthority("SEGURIDAD_ADMINISTRAR")
                        .antMatchers("/admin/**").hasAuthority("CATALOGOS_EDITAR")
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").successHandler(manejador).failureHandler(manejador).permitAll())
                .logout(l -> l.logoutUrl("/salir").addLogoutHandler(manejador).logoutSuccessUrl("/login?salio"))
                .addFilterBefore(new FiltroCambioClave(), AuthorizationFilter.class);
        return http.build();
    }
}
