package com.ejemplo.plantilla.seguridad;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mientras el usuario deba cambiar su clave (usuario nuevo o clave reiniciada), cualquier pantalla lo lleva a
 * /cambiar-clave. Solo quedan libres esa pantalla, la salida y los recursos estáticos.
 */
public class FiltroCambioClave extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean debe = UsuarioActual.sesion().map(UsuarioSesion::isDebeCambiarClave).orElse(false);
        if (debe && !libre(request.getRequestURI().substring(request.getContextPath().length()))) {
            response.sendRedirect(request.getContextPath() + "/cambiar-clave");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean libre(String ruta) {
        return ruta.equals("/cambiar-clave") || ruta.equals("/salir") || ruta.equals("/error")
                || ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/fonts/") || ruta.startsWith("/img/");
    }
}
