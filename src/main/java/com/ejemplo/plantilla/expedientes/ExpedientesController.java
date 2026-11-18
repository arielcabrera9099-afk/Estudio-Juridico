package com.ejemplo.plantilla.expedientes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

/**
 * Expedientes (ver: EXPEDIENTES_VER por la ruta; cambios: EXPEDIENTES_EDITAR; cerrar y reabrir: EXPEDIENTES_CERRAR).
 * Lista con filtros y, desde ella, alta, ficha (con partes, actuaciones e historial de estados), modificación y
 * cambio de estado. Un expediente cerrado es de solo lectura hasta reabrirlo.
 */
@Controller
@RequestMapping("/expedientes")
public class ExpedientesController {

    private static final String PAGINA = "expedientes";
    private static final String EDITAR = "EXPEDIENTES_EDITAR";

    private final ExpedientesServicio servicio;
    private final ModeloComun modelo;

    public ExpedientesController(ExpedientesServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    // ------------------------------------------------------------------------------------------------ lista

    @GetMapping
    public String lista(@RequestParam(required = false) String texto, @RequestParam(defaultValue = "EN_CURSO") String situacion,
                        @RequestParam(required = false) Long estado, @RequestParam(required = false) Long materia,
                        @RequestParam(required = false) Long abogado, @RequestParam(required = false) Long cliente, Model model) {
        ExpedientesServicio.Filtro f = new ExpedientesServicio.Filtro();
        f.texto = texto;
        f.situacion = situacion;
        f.estado = estado;
        f.materia = materia;
        f.abogado = abogado;
        f.cliente = cliente;
        List<Map<String, Object>> filas = servicio.buscar(f);
        boolean recortada = filas.size() > ExpedientesServicio.MAXIMO_FILAS;
        modelo.pantalla(model, PAGINA, "Expedientes");
        model.addAttribute("filas", recortada ? filas.subList(0, ExpedientesServicio.MAXIMO_FILAS) : filas);
        model.addAttribute("recortada", recortada);
        model.addAttribute("maximo", ExpedientesServicio.MAXIMO_FILAS);
        model.addAttribute("f", f);
        model.addAttribute("estados", servicio.estadosFiltro());
        model.addAttribute("materias", servicio.materiasFiltro());
        model.addAttribute("abogados", servicio.abogadosFiltro());
        model.addAttribute("puedeEditar", UsuarioActual.tiene(EDITAR));
        return "pantallas/expedientes/lista";
    }

    // ------------------------------------------------------------------------------------------------ expediente

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) Long cliente, Model model) {
        UsuarioActual.exigir(EDITAR);
        ExpedienteForm form = new ExpedienteForm();
        form.setClienteId(cliente);
        form.setFechaInicio(LocalDate.now(Filas.ZONA).toString());
        // Si quien carga es abogado, queda como responsable; el estado inicial es el primero de la lista.
        Long yo = UsuarioActual.id().orElse(null);
        if (servicio.abogados(null).stream().anyMatch(o -> o.getId().equals(yo))) form.setAbogadoId(yo);
        servicio.estadosIniciales().stream().findFirst().ifPresent(o -> form.setEstadoId((Long) o.getId()));
        return formulario(model, form, "alta", new Errores());
    }

    @PostMapping
    public String crear(@ModelAttribute("form") ExpedienteForm form, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        form.setId(null);
        form.normalizar();
        ExpedientesServicio.Valores v = new ExpedientesServicio.Valores();
        Errores errores = servicio.validar(form, v);
        if (errores.hay()) return formulario(model, form, "alta", errores);
        long id = servicio.crear(form, v);
        redirect.addAttribute("ok", "Se dio de alta el expediente " + form.getNumeroInterno() + ".");
        return "redirect:/expedientes/" + id;
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable long id, Model model) {
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "ver", new Errores());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable long id, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "editar", new Errores());
    }

    @PostMapping("/{id}")
    public String modificar(@PathVariable long id, @ModelAttribute("form") ExpedienteForm form, Model model,
                            RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        ExpedienteForm actual = servicio.formulario(id).orElseThrow(NoEncontrado::new);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        form.setId(id);
        form.setNumeroInterno(actual.getNumeroInterno());
        form.setEstadoId(actual.getEstadoId());
        form.normalizar();
        ExpedientesServicio.Valores v = new ExpedientesServicio.Valores();
        Errores errores = servicio.validar(form, v);
        if (errores.hay()) return formulario(model, form, "editar", errores);
        servicio.modificar(form, v);
        redirect.addAttribute("ok", "Se guardaron los cambios del expediente " + actual.getNumeroInterno() + ".");
        return "redirect:/expedientes/" + id;
    }

    // ------------------------------------------------------------------------------------------------ estado

    @GetMapping("/{id}/estado")
    public String estado(@PathVariable long id, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Map<String, Object> ficha = servicio.ficha(id).orElseThrow(NoEncontrado::new);
        if (servicio.cerrado(ficha) && !UsuarioActual.tiene(ExpedientesServicio.CERRAR)) {
            return volver(id, "Reabrir un expediente requiere el permiso de cierre.", redirect);
        }
        EstadoForm form = new EstadoForm();
        form.setFechaCierre(LocalDate.now(Filas.ZONA).toString());
        return pantallaEstado(model, ficha, form, new Errores());
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable long id, @ModelAttribute("form") EstadoForm form, Model model,
                                RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Map<String, Object> ficha = servicio.ficha(id).orElseThrow(NoEncontrado::new);
        form.normalizar();
        Errores errores = servicio.cambiarEstado(id, form, UsuarioActual.tiene(ExpedientesServicio.CERRAR));
        if (errores.hay()) return pantallaEstado(model, ficha, form, errores);
        Map<String, Object> nueva = servicio.ficha(id).orElseThrow();
        redirect.addAttribute("ok", "El expediente " + ficha.get("numero_interno") + " pasó a " + nueva.get("estado") + ".");
        return "redirect:/expedientes/" + id;
    }

    private String pantallaEstado(Model model, Map<String, Object> ficha, EstadoForm form, Errores errores) {
        long id = ((Number) ficha.get("id")).longValue();
        modelo.pantalla(model, PAGINA, "Cambiar estado");
        model.addAttribute("ficha", ficha);
        model.addAttribute("form", form);
        model.addAttribute("errores", errores);
        List<Opcion> estados = servicio.estadosDestino(((Number) ficha.get("estado_expediente_id")).longValue(),
                UsuarioActual.tiene(ExpedientesServicio.CERRAR));
        model.addAttribute("estados", estados);
        model.addAttribute("idsCierre", estados.stream().filter(o -> "CIERRE".equals(o.getCodigo()))
                .map(o -> String.valueOf(o.getId())).collect(Collectors.joining("|")));
        model.addAttribute("reabre", servicio.cerrado(ficha));
        model.addAttribute("expedienteId", id);
        return "pantallas/expedientes/estado";
    }

    // ------------------------------------------------------------------------------------------------ partes

    @GetMapping("/{id}/partes/nuevo")
    public String nuevaParte(@PathVariable long id, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        return pantallaParte(model, id, new ParteForm(), null, new Errores());
    }

    @PostMapping("/{id}/partes")
    public String crearParte(@PathVariable long id, @ModelAttribute("parte") ParteForm parte, Model model,
                             RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        parte.setId(null);
        parte.normalizar();
        Errores errores = servicio.validar(parte, null);
        if (errores.hay()) return pantallaParte(model, id, parte, null, errores);
        servicio.crearParte(id, parte);
        redirect.addAttribute("ok", "Se agregó la parte " + parte.getNombre() + ".");
        return "redirect:/expedientes/" + id + "#partes";
    }

    @GetMapping("/{id}/partes/{parteId}/editar")
    public String editarParte(@PathVariable long id, @PathVariable long parteId, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        ParteForm p = servicio.parte(id, parteId).orElseThrow(NoEncontrado::new);
        return pantallaParte(model, id, p, p.getRolParteId(), new Errores());
    }

    @PostMapping("/{id}/partes/{parteId}")
    public String modificarParte(@PathVariable long id, @PathVariable long parteId, @ModelAttribute("parte") ParteForm parte,
                                 Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        ParteForm antes = servicio.parte(id, parteId).orElseThrow(NoEncontrado::new);
        parte.setId(parteId);
        parte.normalizar();
        Errores errores = servicio.validar(parte, antes.getRolParteId());
        if (errores.hay()) return pantallaParte(model, id, parte, antes.getRolParteId(), errores);
        servicio.modificarParte(id, parte);
        redirect.addAttribute("ok", "Se guardaron los cambios de la parte " + parte.getNombre() + ".");
        return "redirect:/expedientes/" + id + "#partes";
    }

    @PostMapping("/{id}/partes/{parteId}/eliminar")
    public String eliminarParte(@PathVariable long id, @PathVariable long parteId, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        ParteForm p = servicio.parte(id, parteId).orElseThrow(NoEncontrado::new);
        servicio.eliminarParte(id, parteId);
        redirect.addAttribute("ok", "Se quitó la parte " + p.getNombre() + ".");
        return "redirect:/expedientes/" + id + "#partes";
    }

    private String pantallaParte(Model model, long id, ParteForm parte, Long rolAnterior, Errores errores) {
        modelo.pantalla(model, PAGINA, parte.getId() == null ? "Nueva parte" : "Modificar parte");
        model.addAttribute("ficha", servicio.ficha(id).orElseThrow(NoEncontrado::new));
        model.addAttribute("parte", parte);
        model.addAttribute("roles", servicio.rolesParte(rolAnterior));
        model.addAttribute("errores", errores);
        return "pantallas/expedientes/parte";
    }

    // ------------------------------------------------------------------------------------------------ actuaciones

    @GetMapping("/{id}/actuaciones/nuevo")
    public String nuevaActuacion(@PathVariable long id, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        ActuacionForm a = new ActuacionForm();
        a.setFecha(LocalDate.now(Filas.ZONA).toString());
        return pantallaActuacion(model, id, a, null, new Errores());
    }

    @PostMapping("/{id}/actuaciones")
    public String crearActuacion(@PathVariable long id, @ModelAttribute("actuacion") ActuacionForm a, Model model,
                                 RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        a.setId(null);
        a.normalizar();
        LocalDate[] fecha = new LocalDate[1];
        Errores errores = servicio.validar(a, null, inicio(id), fecha);
        if (errores.hay()) return pantallaActuacion(model, id, a, null, errores);
        servicio.crearActuacion(id, a, fecha[0]);
        redirect.addAttribute("ok", "Se registró la actuación en la bitácora.");
        return "redirect:/expedientes/" + id + "#actuaciones";
    }

    @GetMapping("/{id}/actuaciones/{actuacionId}/editar")
    public String editarActuacion(@PathVariable long id, @PathVariable long actuacionId, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        ActuacionForm a = servicio.actuacion(id, actuacionId).orElseThrow(NoEncontrado::new);
        return pantallaActuacion(model, id, a, a.getTipoActuacionId(), new Errores());
    }

    @PostMapping("/{id}/actuaciones/{actuacionId}")
    public String modificarActuacion(@PathVariable long id, @PathVariable long actuacionId,
                                     @ModelAttribute("actuacion") ActuacionForm a, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        ActuacionForm antes = servicio.actuacion(id, actuacionId).orElseThrow(NoEncontrado::new);
        a.setId(actuacionId);
        a.normalizar();
        LocalDate[] fecha = new LocalDate[1];
        Errores errores = servicio.validar(a, antes.getTipoActuacionId(), inicio(id), fecha);
        if (errores.hay()) return pantallaActuacion(model, id, a, antes.getTipoActuacionId(), errores);
        servicio.modificarActuacion(id, a, fecha[0]);
        redirect.addAttribute("ok", "Se guardaron los cambios de la actuación.");
        return "redirect:/expedientes/" + id + "#actuaciones";
    }

    @PostMapping("/{id}/actuaciones/{actuacionId}/eliminar")
    public String eliminarActuacion(@PathVariable long id, @PathVariable long actuacionId, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        Optional<String> cerrado = siCerrado(id);
        if (cerrado.isPresent()) return volver(id, cerrado.get(), redirect);
        servicio.actuacion(id, actuacionId).orElseThrow(NoEncontrado::new);
        servicio.eliminarActuacion(id, actuacionId).ifPresentOrElse(
                motivo -> redirect.addAttribute("error", motivo),
                () -> redirect.addAttribute("ok", "Se eliminó la actuación de la bitácora."));
        return "redirect:/expedientes/" + id + "#actuaciones";
    }

    private String pantallaActuacion(Model model, long id, ActuacionForm a, Long tipoAnterior, Errores errores) {
        modelo.pantalla(model, PAGINA, a.getId() == null ? "Nueva actuación" : "Modificar actuación");
        model.addAttribute("ficha", servicio.ficha(id).orElseThrow(NoEncontrado::new));
        model.addAttribute("actuacion", a);
        model.addAttribute("tipos", servicio.tiposActuacion(tipoAnterior));
        model.addAttribute("hoy", LocalDate.now(Filas.ZONA));
        model.addAttribute("errores", errores);
        return "pantallas/expedientes/actuacion";
    }

    // ------------------------------------------------------------------------------------------------ auxiliares

    private LocalDate inicio(long id) {
        return (LocalDate) servicio.ficha(id).orElseThrow(NoEncontrado::new).get("fecha_inicio");
    }

    private Optional<String> siCerrado(long id) {
        Map<String, Object> f = servicio.ficha(id).orElseThrow(NoEncontrado::new);
        return servicio.cerrado(f)
                ? Optional.of("El expediente " + f.get("numero_interno") + " está cerrado: para modificarlo, primero reábralo con Cambiar estado.")
                : Optional.empty();
    }

    private static String volver(long id, String error, RedirectAttributes redirect) {
        redirect.addAttribute("error", error);
        return "redirect:/expedientes/" + id;
    }

    private String formulario(Model model, ExpedienteForm form, String modo, Errores errores) {
        boolean alta = "alta".equals(modo);
        modelo.pantalla(model, alta ? "expedientes/nuevo" : PAGINA,
                alta ? "Nuevo expediente" : "ver".equals(modo) ? "Expediente " + form.getNumeroInterno() : "Modificar expediente");
        model.addAttribute("form", form);
        model.addAttribute("modo", modo);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("errores", errores);
        model.addAttribute("puedeEditar", UsuarioActual.tiene(EDITAR));
        model.addAttribute("puedeCerrar", UsuarioActual.tiene(ExpedientesServicio.CERRAR));
        ExpedienteForm guardado = form.getId() == null ? new ExpedienteForm() : servicio.formulario(form.getId()).orElseThrow();
        model.addAttribute("clientes", servicio.clientes(guardado.getClienteId()));
        model.addAttribute("abogados", servicio.abogados(guardado.getAbogadoId()));
        model.addAttribute("materias", servicio.materias(guardado.getMateriaId()));
        model.addAttribute("tiposProceso", servicio.tiposProceso(guardado.getTipoProcesoId()));
        model.addAttribute("juzgados", servicio.juzgados(guardado.getJuzgadoId()));
        model.addAttribute("estadosIniciales", servicio.estadosIniciales());
        if (form.getId() != null) {
            Map<String, Object> ficha = servicio.ficha(form.getId()).orElseThrow(NoEncontrado::new);
            model.addAttribute("ficha", ficha);
            model.addAttribute("cerrado", servicio.cerrado(ficha));
            if ("ver".equals(modo)) {
                model.addAttribute("partes", servicio.partes(form.getId()));
                model.addAttribute("actuaciones", servicio.actuaciones(form.getId()));
                model.addAttribute("historial", servicio.historial(form.getId()));
            }
        }
        return "pantallas/expedientes/form";
    }
}
