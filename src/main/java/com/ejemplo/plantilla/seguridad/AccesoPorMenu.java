package com.ejemplo.plantilla.seguridad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Acceso por menú: una dirección que corresponde a una opción del menú (su ruta o algo debajo, como /clientes/5/editar
 * para /clientes) solo la abre quien ve esa opción, es decir, tiene la authority MENU_ + código de la opción. Si la
 * dirección corresponde a varias opciones, decide la más específica ({@link #opcionesDe}).
 * El resto (inicio, avisos, cambiar clave) solo pide haber ingresado. Las acciones dentro de cada pantalla (modificar,
 * cerrar, eliminar) las controla cada controlador con su permiso.
 */
@Component
public class AccesoPorMenu implements AuthorizationManager<RequestAuthorizationContext> {

    /** Direcciones sin opción propia en el menú que igual son de administración. */
    private static final Map<String, String> EXTRA = Map.of("/componentes", "ADMIN_USUARIOS");

    private final NamedParameterJdbcTemplate jdbc;
    /** Ruta y código de cada opción activa. El menú solo cambia por migración: se lee una vez. */
    private volatile List<String[]> opciones;

    public AccesoPorMenu(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> autenticacion, RequestAuthorizationContext contexto) {
        Authentication a = autenticacion.get();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return new AuthorizationDecision(false);
        HttpServletRequest r = contexto.getRequest();
        List<String> requeridas = opcionesDe(r.getRequestURI().substring(r.getContextPath().length()));
        if (requeridas.isEmpty()) return new AuthorizationDecision(true);
        Set<String> tiene = a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return new AuthorizationDecision(requeridas.stream().anyMatch(c -> tiene.contains(UsuarioSesion.PREFIJO_MENU + c)));
    }

    /**
     * Códigos de las opciones del menú que habilitan la dirección: la opción más específica (la de ruta más larga que
     * la cubre: /agenda/vencimientos es "Vencimientos", no "Calendario"). Una opción "nuevo" (/expedientes/nuevo) es un
     * atajo al alta de su pantalla: también la abre quien ve esa pantalla.
     */
    List<String> opcionesDe(String ruta) {
        List<String[]> cubren = new ArrayList<>();
        for (String[] o : opciones()) {
            if (ruta.equals(o[0]) || ruta.startsWith(o[0] + "/") || ruta.startsWith(o[0] + ".")) cubren.add(o);
        }
        EXTRA.forEach((r, c) -> {
            if (ruta.equals(r) || ruta.startsWith(r + "/")) cubren.add(new String[] {r, c});
        });
        if (cubren.isEmpty()) return List.of();
        cubren.sort((a, b) -> b[0].length() - a[0].length());
        String[] masEspecifica = cubren.get(0);
        if (!masEspecifica[0].endsWith("/nuevo")) return List.of(masEspecifica[1]);
        List<String> l = new ArrayList<>();
        cubren.forEach(o -> l.add(o[1]));
        return l;
    }

    private List<String[]> opciones() {
        List<String[]> o = opciones;
        if (o == null) {
            o = jdbc.query("SELECT ruta, codigo FROM menu WHERE nivel = 'OPCION' AND activo = 1", Map.of(),
                    (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
            opciones = o;
        }
        return o;
    }
}
