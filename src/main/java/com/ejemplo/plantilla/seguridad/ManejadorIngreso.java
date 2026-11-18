package com.ejemplo.plantilla.seguridad;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Parametros;

/**
 * Lo que pasa al ingresar y al salir:
 * <ul>
 *   <li>Ingreso correcto: reinicia los intentos fallidos, guarda el último acceso, audita y, si debe cambiar la clave,
 *       lo lleva a esa pantalla.</li>
 *   <li>Ingreso fallido: suma un intento; al llegar a {@code seguridad.max_intentos} bloquea al usuario
 *       {@code seguridad.minutos_bloqueo} minutos. Audita el intento, también con un usuario inexistente.</li>
 *   <li>Salida: audita.</li>
 * </ul>
 */
@Component
public class ManejadorIngreso extends SavedRequestAwareAuthenticationSuccessHandler
        implements AuthenticationFailureHandler, LogoutHandler {

    private final NamedParameterJdbcTemplate jdbc;
    private final Auditoria auditoria;
    private final Parametros parametros;

    public ManejadorIngreso(NamedParameterJdbcTemplate jdbc, Auditoria auditoria, Parametros parametros) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.parametros = parametros;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication auth)
            throws IOException, javax.servlet.ServletException {
        UsuarioSesion u = (UsuarioSesion) auth.getPrincipal();
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_acceso = SYSUTCDATETIME() "
                + "WHERE id = :id", Map.of("id", u.getId()));
        auditoria.registrar(u.getId(), Auditoria.INGRESO, "usuario", u.getId(), null, null);
        if (u.isDebeCambiarClave()) {
            getRedirectStrategy().sendRedirect(request, response, "/cambiar-clave");
            return;
        }
        super.onAuthenticationSuccess(request, response, auth);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException e) throws IOException {
        String nombre = request.getParameter("username");
        nombre = nombre == null ? "" : nombre.trim().toLowerCase();
        if (nombre.length() > 30) nombre = nombre.substring(0, 30);
        List<Long> ids = jdbc.queryForList("SELECT id FROM usuario WHERE nombre_usuario = :u", Map.of("u", nombre), Long.class);
        Long id = ids.isEmpty() ? null : ids.get(0);
        String motivo;
        if (e instanceof LockedException) {
            motivo = "bloqueado";
        } else if (e instanceof DisabledException) {
            motivo = "inactivo";
        } else {
            motivo = "credenciales";
            if (id != null && sumarIntento(id)) motivo = "bloqueado";
        }
        auditoria.registrar(id, Auditoria.INGRESO_FALLIDO, "usuario", id, null, Map.of("usuario", nombre, "motivo", motivo));
        getRedirectStrategy().sendRedirect(request, response, "/login?error=" + motivo);
    }

    /** Suma un intento fallido y bloquea al llegar al máximo. Devuelve true si el usuario quedó bloqueado. */
    private boolean sumarIntento(long id) {
        int max = Math.max(1, parametros.numero("seguridad.max_intentos", 5));
        int minutos = Math.max(1, parametros.numero("seguridad.minutos_bloqueo", 15));
        Integer intentos = jdbc.queryForObject(
                "UPDATE usuario SET intentos_fallidos = intentos_fallidos + 1 OUTPUT inserted.intentos_fallidos "
                        + "WHERE id = :id", Map.of("id", id), Integer.class);
        if (intentos != null && intentos >= max) {
            jdbc.update("UPDATE usuario SET intentos_fallidos = 0, "
                    + "bloqueado_hasta = DATEADD(MINUTE, :minutos, SYSUTCDATETIME()) WHERE id = :id",
                    Map.of("id", id, "minutos", minutos));
            return true;
        }
        return false;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion) {
            long id = ((UsuarioSesion) auth.getPrincipal()).getId();
            auditoria.registrar(id, Auditoria.SALIDA, "usuario", id, null, null);
        }
    }
}
