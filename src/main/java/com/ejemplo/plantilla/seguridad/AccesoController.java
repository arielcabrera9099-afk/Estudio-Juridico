package com.ejemplo.plantilla.seguridad;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Parametros;
import com.ejemplo.plantilla.web.Errores;

/** Pantalla de ingreso y cambio de la propia contraseña (obligatorio en el primer ingreso). */
@Controller
public class AccesoController {

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Auditoria auditoria;
    private final Parametros parametros;

    public AccesoController(NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, Auditoria auditoria,
                            Parametros parametros) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.parametros = parametros;
    }

    @GetMapping("/login")
    public String login(Model model) {
        if (UsuarioActual.sesion().isPresent()) return "redirect:/";
        model.addAttribute("minutosBloqueo", parametros.numero("seguridad.minutos_bloqueo", 15));
        return "pantallas/login";
    }

    @GetMapping("/cambiar-clave")
    public String cambiarClave(Model model) {
        model.addAttribute("obligatorio", UsuarioActual.sesion().map(UsuarioSesion::isDebeCambiarClave).orElse(false));
        model.addAttribute("errores", new Errores());
        model.addAttribute("reglas", ReglasClave.DESCRIPCION);
        return "pantallas/cambiar-clave";
    }

    @PostMapping("/cambiar-clave")
    @Transactional
    public String guardarClave(@RequestParam(defaultValue = "") String actual, @RequestParam(defaultValue = "") String nueva,
                               @RequestParam(defaultValue = "") String repetir, Model model, RedirectAttributes redirect) {
        UsuarioSesion u = UsuarioActual.sesion().orElseThrow();
        Errores errores = new Errores();
        String hash = jdbc.queryForObject("SELECT clave_hash FROM usuario WHERE id = :id", Map.of("id", u.getId()), String.class);
        if (actual.isEmpty()) errores.campo("actual", "La contraseña actual es requerida.");
        else if (!encoder.matches(actual, hash)) errores.campo("actual", "La contraseña actual no es correcta.");
        ReglasClave.validar(nueva, u.getUsername()).ifPresent(m -> errores.campo("nueva", m));
        if (!errores.tiene("nueva") && nueva.equals(actual)) errores.campo("nueva", "La contraseña nueva debe ser distinta de la actual.");
        if (!nueva.equals(repetir)) errores.campo("repetir", "Las contraseñas no coinciden.");
        if (errores.hay()) {
            model.addAttribute("obligatorio", u.isDebeCambiarClave());
            model.addAttribute("errores", errores);
            model.addAttribute("reglas", ReglasClave.DESCRIPCION);
            return "pantallas/cambiar-clave";
        }
        jdbc.update("UPDATE usuario SET clave_hash = :hash, debe_cambiar_clave = 0, modificado_en = SYSUTCDATETIME(), "
                + "modificado_por = :id WHERE id = :id", Map.of("hash", encoder.encode(nueva), "id", u.getId()));
        auditoria.registrar(Auditoria.MODIFICACION, "usuario", u.getId(), null, Map.of("clave", "cambiada por el usuario"));
        u.claveCambiada();
        redirect.addAttribute("ok", "La contraseña se cambió correctamente.");
        return "redirect:/";
    }
}
