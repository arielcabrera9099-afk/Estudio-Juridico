package com.ejemplo.plantilla.auditoria;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/**
 * Auditoría (permiso AUDITORIA_VER, controlado por la ruta): lista con filtros y detalle de cada registro. Solo
 * consulta. Al entrar sin filtros muestra los últimos 7 días.
 */
@Controller
@RequestMapping("/admin/auditoria")
public class AuditoriaController {

    private static final String PAGINA = "admin/auditoria";
    private static final int DIAS_POR_DEFECTO = 7;

    private final AuditoriaServicio servicio;
    private final ModeloComun modelo;

    public AuditoriaController(AuditoriaServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    @GetMapping
    public String lista(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                        @RequestParam(required = false) Long usuario, @RequestParam(required = false) String accion,
                        @RequestParam(required = false) String tabla, @RequestParam(required = false) String registro,
                        @RequestParam Map<String, String> params, Model model) {
        if (params.isEmpty()) {
            hasta = LocalDate.now(Filas.ZONA);
            desde = hasta.minusDays(DIAS_POR_DEFECTO - 1L);
        }
        AuditoriaServicio.Filtro f = new AuditoriaServicio.Filtro();
        f.desde = desde;
        f.hasta = hasta;
        f.usuario = usuario;
        f.accion = accion;
        f.tabla = tabla;
        registro = registro == null ? null : registro.trim();
        boolean registroValido = registro == null || registro.isEmpty() || registro.matches("\\d{1,18}");
        if (registroValido && registro != null && !registro.isEmpty()) f.registro = Long.valueOf(registro);

        modelo.pantalla(model, PAGINA, "Auditoría");
        String error = desde != null && hasta != null && desde.isAfter(hasta)
                ? "La fecha desde no puede ser posterior a la fecha hasta."
                : !registroValido ? "El número de registro lleva solo dígitos." : null;
        if (error != null) {
            model.addAttribute("errorFiltro", error);
            model.addAttribute("filas", List.of());
            model.addAttribute("recortada", false);
        } else {
            List<Map<String, Object>> filas = servicio.buscar(f);
            boolean recortada = filas.size() > AuditoriaServicio.MAXIMO_FILAS;
            model.addAttribute("filas", recortada ? filas.subList(0, AuditoriaServicio.MAXIMO_FILAS) : filas);
            model.addAttribute("recortada", recortada);
        }
        model.addAttribute("maximo", AuditoriaServicio.MAXIMO_FILAS);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("usuario", usuario);
        model.addAttribute("accion", accion);
        model.addAttribute("tabla", tabla);
        model.addAttribute("registro", registro);
        model.addAttribute("usuarios", servicio.usuarios());
        model.addAttribute("tablas", servicio.tablas());
        model.addAttribute("acciones", AuditoriaServicio.ACCIONES);
        model.addAttribute("nombresTabla", AuditoriaServicio.TABLAS);
        return "pantallas/auditoria/lista";
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable long id, Model model) {
        Map<String, Object> f = servicio.ficha(id).orElseThrow(NoEncontrado::new);
        modelo.pantalla(model, PAGINA, "Registro de auditoría");
        model.addAttribute("ficha", f);
        model.addAttribute("accionTexto", AuditoriaServicio.ACCIONES.getOrDefault((String) f.get("accion"), (String) f.get("accion")));
        model.addAttribute("tablaTexto", AuditoriaServicio.nombreTabla((String) f.get("tabla")));
        model.addAttribute("enlace", servicio.enlace((String) f.get("tabla"), f.get("registro_id")).orElse(null));
        model.addAttribute("cambios", servicio.comparar((String) f.get("datos_anteriores"), (String) f.get("datos_nuevos")));
        return "pantallas/auditoria/ver";
    }
}
