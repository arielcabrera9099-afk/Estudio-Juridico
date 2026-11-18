package com.ejemplo.plantilla.agenda;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;
import com.ejemplo.plantilla.web.Opcion;
import com.ejemplo.plantilla.web.Textos;

/**
 * Agenda (ver: AGENDA_VER por la ruta; cambios: AGENDA_EDITAR). Lista con filtros (por defecto, los pendientes de los
 * próximos 30 días) o calendario del mes, y desde ellos el ABM de eventos y el cambio de estado. No hay baja: un
 * evento se cancela.
 */
@Controller
@RequestMapping("/agenda")
public class AgendaController {

    private static final String PAGINA = "agenda";
    private static final int DIAS_POR_DEFECTO = 30;
    private static final Locale ES = new Locale("es", "PY");

    private final AgendaServicio servicio;
    private final ModeloComun modelo;

    public AgendaController(AgendaServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    /** Un día del calendario mensual con sus eventos. */
    public static final class Dia {
        private final LocalDate fecha;
        private final boolean delMes;
        private final boolean hoy;
        private final List<Map<String, Object>> eventos;

        Dia(LocalDate fecha, boolean delMes, boolean hoy, List<Map<String, Object>> eventos) {
            this.fecha = fecha;
            this.delMes = delMes;
            this.hoy = hoy;
            this.eventos = eventos;
        }

        public LocalDate getFecha() { return fecha; }
        public boolean isDelMes() { return delMes; }
        public boolean isHoy() { return hoy; }
        public List<Map<String, Object>> getEventos() { return eventos; }
    }

    @GetMapping
    public String lista(@RequestParam(defaultValue = "LISTA") String vista,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                        @RequestParam(required = false) String mes, @RequestParam(required = false) Long tipo,
                        @RequestParam(required = false) Long responsable, @RequestParam(defaultValue = "PENDIENTE") String estado,
                        @RequestParam(required = false) String texto, @RequestParam Map<String, String> params, Model model) {
        LocalDate hoy = LocalDate.now(Filas.ZONA);
        boolean calendario = "MES".equals(vista);
        AgendaServicio.Filtro f = new AgendaServicio.Filtro();
        f.tipo = tipo;
        f.responsable = responsable;
        f.estado = estado;
        f.texto = Textos.limpio(texto);
        String error = null;
        YearMonth ym = YearMonth.from(hoy);
        if (calendario) {
            if (mes != null && !mes.isBlank()) {
                try {
                    ym = YearMonth.parse(mes);
                } catch (DateTimeParseException ex) {
                    error = "El mes no es válido.";
                }
            }
            f.desde = ym.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            f.hasta = ym.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        } else {
            boolean sinFiltros = params.isEmpty() || (params.size() == 1 && params.containsKey("vista"));
            if (sinFiltros) {
                desde = hoy;
                hasta = hoy.plusDays(DIAS_POR_DEFECTO);
            }
            if (desde != null && hasta != null && desde.isAfter(hasta)) error = "La fecha desde no puede ser posterior a la fecha hasta.";
            f.desde = desde;
            f.hasta = hasta;
        }

        List<Map<String, Object>> filas = error == null ? servicio.buscar(f) : List.of();
        boolean recortada = filas.size() > AgendaServicio.MAXIMO_FILAS;
        if (recortada) filas = filas.subList(0, AgendaServicio.MAXIMO_FILAS);

        modelo.pantalla(model, PAGINA, "Agenda");
        model.addAttribute("vista", calendario ? "MES" : "LISTA");
        model.addAttribute("filas", filas);
        model.addAttribute("recortada", recortada);
        model.addAttribute("maximo", AgendaServicio.MAXIMO_FILAS);
        model.addAttribute("errorFiltro", error);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("tipo", tipo);
        model.addAttribute("responsable", responsable);
        model.addAttribute("estado", estado);
        model.addAttribute("texto", f.texto);
        model.addAttribute("hoy", hoy);
        model.addAttribute("tipos", servicio.tiposFiltro());
        model.addAttribute("responsables", servicio.responsables(null));
        model.addAttribute("puedeEditar", UsuarioActual.tiene(AgendaServicio.EDITAR));
        if (calendario) {
            model.addAttribute("mes", ym.toString());
            model.addAttribute("mesTitulo", Textos.limpio(ym.atDay(1).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES))));
            model.addAttribute("mesAnterior", ym.minusMonths(1).toString());
            model.addAttribute("mesSiguiente", ym.plusMonths(1).toString());
            model.addAttribute("semanas", semanas(ym, f.desde, f.hasta, filas, hoy));
        }
        return "pantallas/agenda/lista";
    }

    private static List<List<Dia>> semanas(YearMonth ym, LocalDate desde, LocalDate hasta, List<Map<String, Object>> filas, LocalDate hoy) {
        Map<LocalDate, List<Map<String, Object>>> porDia = filas.stream()
                .collect(Collectors.groupingBy(x -> ((LocalDateTime) x.get("fecha_inicio")).toLocalDate()));
        List<List<Dia>> semanas = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.MONDAY) semanas.add(new ArrayList<>());
            semanas.get(semanas.size() - 1).add(new Dia(d, YearMonth.from(d).equals(ym), d.equals(hoy), porDia.getOrDefault(d, List.of())));
        }
        return semanas;
    }

    // ------------------------------------------------------------------------------------------------ evento

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) Long expediente,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha, Model model) {
        UsuarioActual.exigir(AgendaServicio.EDITAR);
        EventoForm form = new EventoForm();
        form.setFecha((fecha != null ? fecha : LocalDate.now(Filas.ZONA)).toString());
        form.setResponsableId(UsuarioActual.id().orElse(null));
        if (expediente != null) {
            form.setExpedienteId(expediente);
            servicio.juzgadoDe(expediente).ifPresent(form::setJuzgadoId);
        }
        return formulario(model, form, "alta", new Errores());
    }

    @PostMapping
    public String crear(@ModelAttribute("form") EventoForm form, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(AgendaServicio.EDITAR);
        form.setId(null);
        form.normalizar();
        AgendaServicio.Momentos m = new AgendaServicio.Momentos();
        Errores errores = servicio.validar(form, m);
        if (errores.hay()) return formulario(model, form, "alta", errores);
        long id = servicio.crear(form, m);
        redirect.addAttribute("ok", "Se agendó «" + form.getTitulo() + "».");
        return "redirect:/agenda/" + id;
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable long id, Model model) {
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "ver", new Errores());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable long id, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(AgendaServicio.EDITAR);
        if (!modificable(id)) return volver(id, redirect);
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "editar", new Errores());
    }

    @PostMapping("/{id}")
    public String modificar(@PathVariable long id, @ModelAttribute("form") EventoForm form, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(AgendaServicio.EDITAR);
        if (!modificable(id)) return volver(id, redirect);
        form.setId(id);
        form.normalizar();
        AgendaServicio.Momentos m = new AgendaServicio.Momentos();
        Errores errores = servicio.validar(form, m);
        if (errores.hay()) return formulario(model, form, "editar", errores);
        servicio.modificar(form, m);
        redirect.addAttribute("ok", "Se guardaron los cambios de «" + form.getTitulo() + "». Los avisos se recalcularon.");
        return "redirect:/agenda/" + id;
    }

    @GetMapping("/{id}/estado")
    public String estado(@PathVariable long id, @RequestParam(required = false) String nuevo, Model model) {
        UsuarioActual.exigir(AgendaServicio.EDITAR);
        return pantallaEstado(model, id, nuevo, null, new Errores());
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable long id, @RequestParam(required = false) String estado,
                                @RequestParam(required = false) String resultado, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(AgendaServicio.EDITAR);
        servicio.ficha(id).orElseThrow(NoEncontrado::new);
        String texto = Textos.multilinea(resultado);
        Errores errores = servicio.cambiarEstado(id, estado, texto);
        if (errores.hay()) return pantallaEstado(model, id, estado, texto, errores);
        redirect.addAttribute("ok", "El evento quedó " + estado.toLowerCase() + ".");
        return "redirect:/agenda/" + id;
    }

    private String pantallaEstado(Model model, long id, String nuevo, String resultado, Errores errores) {
        Map<String, Object> ficha = servicio.ficha(id).orElseThrow(NoEncontrado::new);
        modelo.pantalla(model, PAGINA, "Cambiar estado del evento");
        model.addAttribute("ficha", ficha);
        model.addAttribute("estados", AgendaServicio.destinos((String) ficha.get("estado")));
        model.addAttribute("nuevo", nuevo);
        model.addAttribute("resultado", resultado);
        model.addAttribute("errores", errores);
        return "pantallas/agenda/estado";
    }

    // ------------------------------------------------------------------------------------------------ auxiliares

    private boolean modificable(long id) {
        return AgendaServicio.modificable(servicio.ficha(id).orElseThrow(NoEncontrado::new));
    }

    private static String volver(long id, RedirectAttributes redirect) {
        redirect.addAttribute("error", "El evento ya está realizado o cancelado: no se modifica.");
        return "redirect:/agenda/" + id;
    }

    private String formulario(Model model, EventoForm form, String modo, Errores errores) {
        String titulo = "alta".equals(modo) ? "Nuevo evento" : "ver".equals(modo) ? "Evento" : "Modificar evento";
        modelo.pantalla(model, PAGINA, titulo);
        EventoForm guardado = form.getId() == null ? new EventoForm() : servicio.formulario(form.getId()).orElseThrow();
        List<Opcion> tipos = servicio.tipos(guardado.getTipoEventoId());
        model.addAttribute("form", form);
        model.addAttribute("modo", modo);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("errores", errores);
        model.addAttribute("tipos", tipos);
        model.addAttribute("idsSinPlazo", tipos.stream().filter(o -> !"PLAZO".equals(o.getCodigo()))
                .map(o -> String.valueOf(o.getId())).collect(Collectors.joining("|")));
        model.addAttribute("expedientes", servicio.expedientes(guardado.getExpedienteId()));
        model.addAttribute("responsables", servicio.responsables(guardado.getResponsableId()));
        model.addAttribute("juzgados", servicio.juzgados(guardado.getJuzgadoId()));
        model.addAttribute("puedeEditar", UsuarioActual.tiene(AgendaServicio.EDITAR));
        if (form.getId() != null) {
            Map<String, Object> ficha = servicio.ficha(form.getId()).orElseThrow(NoEncontrado::new);
            model.addAttribute("ficha", ficha);
            model.addAttribute("modificable", AgendaServicio.modificable(ficha));
            model.addAttribute("recordatorios", servicio.recordatorios(form.getId()));
        }
        return "pantallas/agenda/form";
    }
}
