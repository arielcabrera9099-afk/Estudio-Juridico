package com.ejemplo.plantilla.documentos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;
import com.ejemplo.plantilla.web.Textos;

/**
 * Documentos (ver y descargar: DOCUMENTOS_VER por la ruta; cargar y modificar: DOCUMENTOS_SUBIR; eliminar y restaurar:
 * DOCUMENTOS_ELIMINAR). Buscador con filtros y, desde él, la carga, la ficha, la modificación de datos, la descarga y
 * la baja lógica. Cada descarga queda en la auditoría.
 */
@Controller
@RequestMapping("/documentos")
public class DocumentosController {

    private static final String PAGINA = "documentos";

    private final DocumentosServicio servicio;
    private final AlmacenDocumentos almacen;
    private final ModeloComun modelo;

    public DocumentosController(DocumentosServicio servicio, AlmacenDocumentos almacen, ModeloComun modelo) {
        this.servicio = servicio;
        this.almacen = almacen;
        this.modelo = modelo;
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String texto, @RequestParam(required = false) Long tipo,
                        @RequestParam(required = false) String expediente,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                        @RequestParam(defaultValue = "VIGENTES") String estado, Model model) {
        DocumentosServicio.Filtro f = new DocumentosServicio.Filtro();
        f.texto = Textos.limpio(texto);
        f.tipo = tipo;
        f.expediente = Textos.limpio(expediente);
        f.desde = desde;
        f.hasta = hasta;
        f.estado = UsuarioActual.tiene(DocumentosServicio.ELIMINAR) ? estado : "VIGENTES";
        String error = desde != null && hasta != null && desde.isAfter(hasta) ? "La fecha desde no puede ser posterior a la fecha hasta." : null;
        List<Map<String, Object>> filas = error == null ? servicio.buscar(f) : List.of();
        boolean recortada = filas.size() > DocumentosServicio.MAXIMO_FILAS;
        modelo.pantalla(model, PAGINA, "Buscar documentos");
        model.addAttribute("filas", recortada ? filas.subList(0, DocumentosServicio.MAXIMO_FILAS) : filas);
        model.addAttribute("recortada", recortada);
        model.addAttribute("maximo", DocumentosServicio.MAXIMO_FILAS);
        model.addAttribute("f", f);
        model.addAttribute("errorFiltro", error);
        model.addAttribute("tipos", servicio.tiposFiltro());
        model.addAttribute("visibles", DocumentosServicio.VISIBLES);
        permisos(model);
        return "pantallas/documentos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) Long expediente, Model model) {
        UsuarioActual.exigir(DocumentosServicio.SUBIR);
        DocumentoForm form = new DocumentoForm();
        form.setExpedienteId(expediente);
        model.addAttribute("volver", expediente != null ? "expediente" : "");
        return formulario(model, form, "alta", new Errores());
    }

    @PostMapping
    public String crear(@ModelAttribute("form") DocumentoForm form, @RequestParam(value = "archivo", required = false) MultipartFile archivo,
                        @RequestParam(required = false) String volver, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(DocumentosServicio.SUBIR);
        form.setId(null);
        form.normalizar();
        LocalDate[] fecha = new LocalDate[1];
        Errores errores = servicio.validar(form, archivo, fecha);
        if (!errores.hay()) errores = servicio.crear(form, archivo, fecha[0], new long[1]);
        if (errores.hay()) {
            model.addAttribute("volver", "expediente".equals(volver) ? "expediente" : "");
            return formulario(model, form, "alta", errores);
        }
        redirect.addAttribute("ok", "Se cargó el documento «" + form.getNombre() + "».");
        // Si se cargó desde la ficha del expediente, vuelve a ella; si no, a la grilla.
        return "expediente".equals(volver) ? "redirect:/expedientes/" + form.getExpedienteId() + "#documentos" : "redirect:/documentos";
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable long id, Model model) {
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "ver", new Errores());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable long id, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(DocumentosServicio.SUBIR);
        Map<String, Object> ficha = servicio.ficha(id).orElseThrow(NoEncontrado::new);
        if (ficha.get("eliminado_en") != null) return volver(redirect, "El documento está eliminado: restáurelo para modificarlo.");
        return formulario(model, servicio.formulario(id).orElseThrow(), "editar", new Errores());
    }

    @PostMapping("/{id}")
    public String modificar(@PathVariable long id, @ModelAttribute("form") DocumentoForm form, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(DocumentosServicio.SUBIR);
        DocumentoForm actual = servicio.formulario(id).orElseThrow(NoEncontrado::new);
        if (servicio.ficha(id).orElseThrow().get("eliminado_en") != null) return volver(redirect, "El documento está eliminado: restáurelo para modificarlo.");
        form.setId(id);
        form.setExpedienteId(actual.getExpedienteId());
        form.normalizar();
        LocalDate[] fecha = new LocalDate[1];
        Errores errores = servicio.validar(form, null, fecha);
        if (errores.hay()) return formulario(model, form, "editar", errores);
        servicio.modificar(form, fecha[0]);
        redirect.addAttribute("ok", "Se guardaron los cambios del documento «" + form.getNombre() + "».");
        return "redirect:/documentos";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable long id, RedirectAttributes redirect) {
        UsuarioActual.exigir(DocumentosServicio.ELIMINAR);
        String nombre = (String) servicio.ficha(id).orElseThrow(NoEncontrado::new).get("nombre");
        servicio.eliminar(id);
        redirect.addAttribute("ok", "Se eliminó el documento «" + nombre + "». El archivo se conserva y puede restaurarse.");
        return "redirect:/documentos";
    }

    @PostMapping("/{id}/restaurar")
    public String restaurar(@PathVariable long id, RedirectAttributes redirect) {
        UsuarioActual.exigir(DocumentosServicio.ELIMINAR);
        String nombre = (String) servicio.ficha(id).orElseThrow(NoEncontrado::new).get("nombre");
        servicio.restaurar(id);
        redirect.addAttribute("ok", "Se restauró el documento «" + nombre + "».");
        return "redirect:/documentos";
    }

    /** Descarga el archivo con su nombre original. */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable long id) {
        return archivo(id, false);
    }

    /** Lo muestra en el navegador (solo PDF e imágenes); el resto se descarga. */
    @GetMapping("/{id}/ver")
    public ResponseEntity<Resource> mostrar(@PathVariable long id) {
        return archivo(id, true);
    }

    private ResponseEntity<Resource> archivo(long id, boolean enLinea) {
        Map<String, Object> f = servicio.ficha(id).orElseThrow(NoEncontrado::new);
        if (f.get("eliminado_en") != null && !UsuarioActual.tiene(DocumentosServicio.ELIMINAR)) throw new NoEncontrado();
        Path p = almacen.resolver((String) f.get("ruta_almacenamiento"));
        if (!Files.isRegularFile(p)) throw new ArchivoFaltante();
        String nombre = (String) f.get("nombre_archivo");
        boolean visible = enLinea && DocumentosServicio.VISIBLES.contains(DocumentosServicio.extension(nombre));
        servicio.registrarDescarga(id, visible ? "VER" : "DESCARGAR");
        ContentDisposition cd = (visible ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(nombre, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) f.get("tipo_mime")))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(((Number) f.get("tamano_bytes")).longValue())
                .body(new FileSystemResource(p));
    }

    // ------------------------------------------------------------------------------------------------ auxiliares

    private static String volver(RedirectAttributes redirect, String error) {
        redirect.addAttribute("error", error);
        return "redirect:/documentos";
    }

    private static void permisos(Model model) {
        model.addAttribute("puedeSubir", UsuarioActual.tiene(DocumentosServicio.SUBIR));
        model.addAttribute("puedeEliminar", UsuarioActual.tiene(DocumentosServicio.ELIMINAR));
    }

    private String formulario(Model model, DocumentoForm form, String modo, Errores errores) {
        modelo.pantalla(model, PAGINA,
                "alta".equals(modo) ? "Cargar documento" : "ver".equals(modo) ? "Documento" : "Modificar documento");
        DocumentoForm guardado = form.getId() == null ? new DocumentoForm() : servicio.formulario(form.getId()).orElseThrow();
        model.addAttribute("form", form);
        model.addAttribute("modo", modo);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("errores", errores);
        model.addAttribute("tipos", servicio.tipos(guardado.getTipoDocumentoId()));
        model.addAttribute("expedientes", servicio.expedientes(form.getId() == null ? null : guardado.getExpedienteId()));
        model.addAttribute("actuaciones", servicio.actuaciones(form.getId() == null ? form.getExpedienteId() : guardado.getExpedienteId()));
        model.addAttribute("extensiones", servicio.extensionesPermitidas());
        model.addAttribute("maximoMb", servicio.tamanoMaximoMb());
        model.addAttribute("visibles", DocumentosServicio.VISIBLES);
        permisos(model);
        if (form.getId() != null) model.addAttribute("ficha", servicio.ficha(form.getId()).orElseThrow(NoEncontrado::new));
        return "pantallas/documentos/form";
    }
}
