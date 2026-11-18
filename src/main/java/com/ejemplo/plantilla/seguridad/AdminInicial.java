package com.ejemplo.plantilla.seguridad;

import java.security.SecureRandom;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.config.AppProperties;

/**
 * Crea el usuario administrador si la base no tiene ningún usuario (el primer arranque). La clave inicial sale de
 * APP_ADMIN_CLAVE; si no está definida se genera una al azar y se muestra una sola vez en el log. En los dos casos el
 * sistema pide cambiarla en el primer ingreso.
 */
@Component
public class AdminInicial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminInicial.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AppProperties app;

    public AdminInicial(NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, AppProperties app) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.app = app;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer usuarios = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM usuario", Integer.class);
        if (usuarios != null && usuarios > 0) return;

        String clave = app.getAdmin().getClaveInicial();
        boolean generada = clave == null || clave.isBlank();
        if (generada) clave = generar();
        Long id = jdbc.queryForObject(
                "INSERT INTO usuario (nombre_usuario, email, clave_hash, nombres, apellidos, debe_cambiar_clave) "
                        + "OUTPUT inserted.id VALUES (:usuario, :email, :hash, N'Administrador', N'del sistema', 1)",
                Map.of("usuario", app.getAdmin().getUsuario().trim().toLowerCase(), "email", app.getAdmin().getEmail(),
                        "hash", encoder.encode(clave)), Long.class);
        jdbc.update("INSERT INTO usuario_rol (usuario_id, rol_id) SELECT :id, id FROM rol WHERE codigo = 'ADMINISTRADOR'",
                Map.of("id", id));
        if (generada) {
            LOG.warn("Se creó el usuario '{}' con la clave inicial {} (defina APP_ADMIN_CLAVE para elegirla). "
                    + "Se pedirá cambiarla en el primer ingreso.", app.getAdmin().getUsuario(), clave);
        } else {
            LOG.info("Se creó el usuario '{}' con la clave de APP_ADMIN_CLAVE. Se pedirá cambiarla en el primer ingreso.",
                    app.getAdmin().getUsuario());
        }
    }

    private static String generar() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder("A1");
        for (int i = 0; i < 14; i++) sb.append(letras.charAt(r.nextInt(letras.length())));
        return sb.toString();
    }
}
