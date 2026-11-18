package com.ejemplo.plantilla.agenda;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.Textos;

/**
 * Vencimientos (AGENDA_VER por la ruta): los plazos pendientes, del más urgente al más lejano, con los ya vencidos
 * primero. Por defecto, los vencidos y los de los próximos 7 días. Solo consulta: los cambios se hacen en el evento.
 */
@Controller
@RequestMapping("/agenda/vencimientos")
public class VencimientosController {

    private final NamedParameterJdbcTemplate jdbc;
    private final AgendaServicio agenda;
    private final ModeloComun modelo;

    public VencimientosController(NamedParameterJdbcTemplate jdbc, AgendaServicio agenda, ModeloComun modelo) {
        this.jdbc = jdbc;
        this.agenda = agenda;
        this.modelo = modelo;
    }

    /** horizonte: VENCIDOS, 7, 30 o TODOS (días hacia adelante; siempre incluye los vencidos, salvo en VENCIDOS solo esos). */
    @GetMapping
    public String lista(@RequestParam(defaultValue = "7") String horizonte, @RequestParam(required = false) Long responsable,
                        @RequestParam(required = false) String texto, Model model) {
        LocalDate hoy = LocalDate.now(Filas.ZONA);
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE te.es_plazo = 1 AND ev.estado = 'PENDIENTE'");
        LocalDate hasta = "VENCIDOS".equals(horizonte) ? hoy.minusDays(1)
                : "30".equals(horizonte) ? hoy.plusDays(30) : "TODOS".equals(horizonte) ? null : hoy.plusDays(7);
        if (hasta != null) {
            w.append(" AND ev.fecha_inicio < :hasta");
            p.addValue("hasta", Filas.momento(hasta.plusDays(1).atStartOfDay()));
        }
        if (responsable != null) {
            w.append(" AND ev.responsable_id = :responsable");
            p.addValue("responsable", responsable);
        }
        String t = Textos.limpio(texto);
        if (t != null) {
            w.append(" AND (ev.titulo LIKE :texto OR ex.numero_interno LIKE :texto OR ex.caratula LIKE :texto)");
            p.addValue("texto", "%" + t + "%");
        }
        List<Map<String, Object>> filas = Filas.lista(jdbc, "SELECT TOP 1001 ev.id, ev.titulo, ev.fecha_inicio, ev.todo_el_dia, "
                + "ev.expediente_id, ex.numero_interno, ex.caratula, CONCAT(u.apellidos, N', ', u.nombres) AS responsable, "
                + "(SELECT MIN(r.fecha_aviso) FROM recordatorio r WHERE r.evento_agenda_id = ev.id AND r.estado = 'PENDIENTE') AS proximo_aviso "
                + "FROM evento_agenda ev JOIN tipo_evento te ON te.id = ev.tipo_evento_id JOIN usuario u ON u.id = ev.responsable_id "
                + "LEFT JOIN expediente ex ON ex.id = ev.expediente_id" + w + " ORDER BY ev.fecha_inicio, ev.id", p);
        boolean recortada = filas.size() > 1000;
        if (recortada) filas = filas.subList(0, 1000);
        int vencidos = 0;
        for (Map<String, Object> f : filas) {
            long dias = ChronoUnit.DAYS.between(hoy, ((LocalDateTime) f.get("fecha_inicio")).toLocalDate());
            f.put("dias", dias);
            if (dias < 0) vencidos++;
        }
        modelo.pantalla(model, "agenda/vencimientos", "Vencimientos");
        model.addAttribute("filas", filas);
        model.addAttribute("recortada", recortada);
        model.addAttribute("vencidos", vencidos);
        model.addAttribute("horizonte", horizonte);
        model.addAttribute("responsable", responsable);
        model.addAttribute("texto", t);
        model.addAttribute("responsables", agenda.responsables(null));
        model.addAttribute("puedeEditar", UsuarioActual.tiene(AgendaServicio.EDITAR));
        model.addAttribute("yo", UsuarioActual.id().orElse(null));
        return "pantallas/agenda/vencimientos";
    }
}
