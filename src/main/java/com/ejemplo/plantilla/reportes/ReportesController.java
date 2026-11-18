package com.ejemplo.plantilla.reportes;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.ModeloComun;

/**
 * Reportes (REPORTES_VER por la ruta): casos activos y cerrados, y agenda semanal o mensual. Cada uno con filtros,
 * resumen y detalle; se imprime (la página tiene su versión de impresión) y se exporta a Excel (CSV con ";").
 */
@Controller
@RequestMapping("/reportes")
public class ReportesController {

    private static final Locale ES = new Locale("es", "PY");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReportesServicio servicio;
    private final ModeloComun modelo;

    public ReportesController(ReportesServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    // ------------------------------------------------------------------------------------------------ casos

    private ReportesServicio.FiltroCasos filtroCasos(LocalDate desde, LocalDate hasta, Long materia, Long abogado,
                                                     String situacion, String agrupar) {
        ReportesServicio.FiltroCasos f = new ReportesServicio.FiltroCasos();
        f.desde = desde;
        f.hasta = hasta;
        f.materia = materia;
        f.abogado = abogado;
        f.situacion = situacion;
        f.agrupar = List.of("ESTADO", "MATERIA", "ABOGADO").contains(agrupar) ? agrupar : "ESTADO";
        return f;
    }

    @GetMapping("/expedientes")
    public String casos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                        @RequestParam(required = false) Long materia, @RequestParam(required = false) Long abogado,
                        @RequestParam(defaultValue = "TODOS") String situacion, @RequestParam(defaultValue = "ESTADO") String agrupar,
                        Model model) {
        ReportesServicio.FiltroCasos f = filtroCasos(desde, hasta, materia, abogado, situacion, agrupar);
        String error = desde != null && hasta != null && desde.isAfter(hasta) ? "La fecha desde no puede ser posterior a la fecha hasta." : null;
        List<Map<String, Object>> resumen = error == null ? servicio.resumenCasos(f) : List.of();
        List<Map<String, Object>> detalle = error == null ? servicio.detalleCasos(f) : List.of();
        boolean recortado = detalle.size() > ReportesServicio.MAXIMO_FILAS;
        long activos = resumen.stream().mapToLong(r -> ((Number) r.get("activos")).longValue()).sum();
        long cerrados = resumen.stream().mapToLong(r -> ((Number) r.get("cerrados")).longValue()).sum();
        modelo.pantalla(model, "reportes/expedientes", "Casos activos y cerrados");
        model.addAttribute("f", f);
        model.addAttribute("errorFiltro", error);
        model.addAttribute("resumen", resumen);
        model.addAttribute("detalle", recortado ? detalle.subList(0, ReportesServicio.MAXIMO_FILAS) : detalle);
        model.addAttribute("recortado", recortado);
        model.addAttribute("activos", activos);
        model.addAttribute("cerrados", cerrados);
        model.addAttribute("total", activos + cerrados);
        model.addAttribute("materias", servicio.materias());
        model.addAttribute("abogados", servicio.abogados());
        model.addAttribute("descripcionFiltros", describirCasos(f));
        encabezado(model);
        return "pantallas/reportes/casos";
    }

    @GetMapping("/expedientes.csv")
    public ResponseEntity<byte[]> casosCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                           @RequestParam(required = false) Long materia, @RequestParam(required = false) Long abogado,
                                           @RequestParam(defaultValue = "TODOS") String situacion) {
        ReportesServicio.FiltroCasos f = filtroCasos(desde, hasta, materia, abogado, situacion, "ESTADO");
        Csv csv = new Csv("NÚMERO", "CARÁTULA", "CLIENTE", "MATERIA", "RESPONSABLE", "ESTADO", "SITUACIÓN", "INICIO", "CIERRE");
        for (Map<String, Object> r : servicio.detalleCasos(f)) {
            csv.fila(r.get("numero_interno"), r.get("caratula"), r.get("cliente"), r.get("materia_nombre"), r.get("abogado_nombre"),
                    r.get("estado_nombre"), r.get("situacion"), fecha(r.get("fecha_inicio")), fecha(r.get("fecha_cierre")));
        }
        return descarga(csv, "casos-activos-y-cerrados");
    }

    private String describirCasos(ReportesServicio.FiltroCasos f) {
        List<String> d = new ArrayList<>();
        d.add(f.desde == null && f.hasta == null ? "Inicio: todas las fechas"
                : "Inicio: " + (f.desde == null ? "…" : f.desde.format(DIA)) + " a " + (f.hasta == null ? "…" : f.hasta.format(DIA)));
        d.add("Situación: " + ("ACTIVO".equals(f.situacion) ? "activos" : "CERRADO".equals(f.situacion) ? "cerrados" : "todos"));
        if (f.materia != null) d.add("Materia: " + servicio.nombre("materia", f.materia));
        if (f.abogado != null) d.add("Responsable: " + servicio.nombre("usuario", f.abogado));
        return String.join(" · ", d);
    }

    // ------------------------------------------------------------------------------------------------ agenda

    /** Período: SEMANA (de lunes a domingo) o MES, que contiene el día de referencia. */
    private static LocalDate[] periodo(String periodo, LocalDate dia) {
        if ("MES".equals(periodo)) {
            YearMonth ym = YearMonth.from(dia);
            return new LocalDate[] {ym.atDay(1), ym.atEndOfMonth()};
        }
        LocalDate lunes = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[] {lunes, lunes.plusDays(6)};
    }

    private ReportesServicio.FiltroAgenda filtroAgenda(String periodo, LocalDate dia, Long tipo, Long responsable, String estado) {
        LocalDate[] r = periodo(periodo, dia);
        ReportesServicio.FiltroAgenda f = new ReportesServicio.FiltroAgenda();
        f.desde = r[0];
        f.hasta = r[1];
        f.tipo = tipo;
        f.responsable = responsable;
        f.estado = estado;
        return f;
    }

    @GetMapping("/agenda")
    public String agenda(@RequestParam(defaultValue = "SEMANA") String periodo,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                         @RequestParam(required = false) Long tipo, @RequestParam(required = false) Long responsable,
                         @RequestParam(defaultValue = "TODOS") String estado, @RequestParam(defaultValue = "TIPO") String agrupar,
                         Model model) {
        periodo = "MES".equals(periodo) ? "MES" : "SEMANA";
        LocalDate referencia = dia != null ? dia : LocalDate.now(Filas.ZONA);
        ReportesServicio.FiltroAgenda f = filtroAgenda(periodo, referencia, tipo, responsable, estado);
        boolean porResponsable = "RESPONSABLE".equals(agrupar);
        List<Map<String, Object>> detalle = servicio.detalleAgenda(f);
        boolean recortado = detalle.size() > ReportesServicio.MAXIMO_FILAS;
        LocalDate anterior = "MES".equals(periodo) ? referencia.minusMonths(1) : referencia.minusWeeks(1);
        LocalDate siguiente = "MES".equals(periodo) ? referencia.plusMonths(1) : referencia.plusWeeks(1);
        modelo.pantalla(model, "reportes/agenda", "Agenda semanal y mensual");
        model.addAttribute("periodo", periodo);
        model.addAttribute("dia", referencia);
        model.addAttribute("tipo", tipo);
        model.addAttribute("responsable", responsable);
        model.addAttribute("estado", estado);
        model.addAttribute("agrupar", porResponsable ? "RESPONSABLE" : "TIPO");
        model.addAttribute("f", f);
        model.addAttribute("tituloPeriodo", tituloPeriodo(periodo, f));
        model.addAttribute("anterior", anterior);
        model.addAttribute("siguiente", siguiente);
        model.addAttribute("resumen", servicio.resumenAgenda(f, porResponsable));
        model.addAttribute("detalle", recortado ? detalle.subList(0, ReportesServicio.MAXIMO_FILAS) : detalle);
        model.addAttribute("recortado", recortado);
        model.addAttribute("tipos", servicio.tiposEvento());
        model.addAttribute("responsables", servicio.responsables());
        model.addAttribute("descripcionFiltros", describirAgenda(f));
        encabezado(model);
        return "pantallas/reportes/agenda";
    }

    @GetMapping("/agenda.csv")
    public ResponseEntity<byte[]> agendaCsv(@RequestParam(defaultValue = "SEMANA") String periodo,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                                            @RequestParam(required = false) Long tipo, @RequestParam(required = false) Long responsable,
                                            @RequestParam(defaultValue = "TODOS") String estado) {
        ReportesServicio.FiltroAgenda f = filtroAgenda("MES".equals(periodo) ? "MES" : "SEMANA",
                dia != null ? dia : LocalDate.now(Filas.ZONA), tipo, responsable, estado);
        Csv csv = new Csv("FECHA", "HORA", "TIPO", "TÍTULO", "EXPEDIENTE", "CARÁTULA", "LUGAR O JUZGADO", "RESPONSABLE", "ESTADO");
        DateTimeFormatter hora = DateTimeFormatter.ofPattern("HH:mm");
        for (Map<String, Object> r : servicio.detalleAgenda(f)) {
            LocalDateTime inicio = (LocalDateTime) r.get("fecha_inicio");
            csv.fila(inicio.format(DIA), Boolean.TRUE.equals(r.get("todo_el_dia")) ? "TODO EL DÍA" : inicio.format(hora),
                    r.get("tipo_evento_nombre"), r.get("titulo"), r.get("numero_interno"), r.get("caratula"),
                    r.get("juzgado_nombre") != null ? r.get("juzgado_nombre") : r.get("lugar"), r.get("responsable_nombre"), r.get("estado"));
        }
        return descarga(csv, "agenda-" + f.desde + "-a-" + f.hasta);
    }

    private static String tituloPeriodo(String periodo, ReportesServicio.FiltroAgenda f) {
        if ("MES".equals(periodo)) {
            String mes = f.desde.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES));
            return Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
        }
        return "Semana del " + f.desde.format(DIA) + " al " + f.hasta.format(DIA);
    }

    private String describirAgenda(ReportesServicio.FiltroAgenda f) {
        List<String> d = new ArrayList<>();
        d.add("Período: " + f.desde.format(DIA) + " a " + f.hasta.format(DIA));
        d.add("Estado: " + (List.of("PENDIENTE", "REALIZADO", "SUSPENDIDO", "CANCELADO").contains(f.estado) ? f.estado.toLowerCase(ES) : "todos"));
        if (f.tipo != null) d.add("Tipo: " + servicio.nombre("tipo_evento", f.tipo));
        if (f.responsable != null) d.add("Responsable: " + servicio.nombre("usuario", f.responsable));
        return String.join(" · ", d);
    }

    // ------------------------------------------------------------------------------------------------ auxiliares

    /** Datos del encabezado impreso: quién y cuándo lo emitió. */
    private static void encabezado(Model model) {
        model.addAttribute("emitido", LocalDateTime.now(Filas.ZONA));
        model.addAttribute("emisor", UsuarioActual.sesion().map(u -> u.getNombre()).orElse(""));
    }

    private static String fecha(Object o) {
        return o == null ? "" : ((LocalDate) o).format(DIA);
    }

    private static ResponseEntity<byte[]> descarga(Csv csv, String nombre) {
        String archivo = nombre + "-" + LocalDate.now(Filas.ZONA) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo, StandardCharsets.UTF_8).build().toString())
                .body(csv.bytes());
    }

    /** CSV para Excel en español: separador ";", UTF-8 con BOM (para que respete los acentos) y comillas donde hace falta. */
    static final class Csv {
        private final StringBuilder s = new StringBuilder("﻿");

        Csv(String... columnas) {
            fila((Object[]) columnas);
        }

        void fila(Object... valores) {
            for (int i = 0; i < valores.length; i++) {
                if (i > 0) s.append(';');
                String v = valores[i] == null ? "" : valores[i].toString().replace("\r", " ").replace("\n", " ");
                // Evita que Excel interprete el texto como fórmula.
                if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
                if (v.contains(";") || v.contains("\"")) v = "\"" + v.replace("\"", "\"\"") + "\"";
                s.append(v);
            }
            s.append("\r\n");
        }

        byte[] bytes() {
            return s.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
