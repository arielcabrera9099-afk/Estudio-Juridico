package com.ejemplo.plantilla.catalogos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ejemplo.plantilla.web.Opcion;

/**
 * Las opciones del menú Administración → Catálogos y los catálogos que agrupa cada una. Para sumar un catálogo: crear
 * la tabla por migración (con id, nombre y activo; codigo si corresponde) y describirlo aquí.
 */
@Component
public class Secciones {

    /** Una opción del menú: su ruta (/admin/{clave}), título, ícono y catálogos (el primero se muestra por defecto). */
    public static final class Seccion {
        private final String clave;
        private final String titulo;
        private final String subtitulo;
        private final String icono;
        private final Map<String, DefCatalogo> catalogos = new LinkedHashMap<>();

        Seccion(String clave, String titulo, String subtitulo, String icono, DefCatalogo... catalogos) {
            this.clave = clave;
            this.titulo = titulo;
            this.subtitulo = subtitulo;
            this.icono = icono;
            for (DefCatalogo c : catalogos) this.catalogos.put(c.getTabla(), c);
        }

        public String getClave() { return clave; }
        public String getTitulo() { return titulo; }
        public String getSubtitulo() { return subtitulo; }
        public String getIcono() { return icono; }
        public Map<String, DefCatalogo> getCatalogos() { return catalogos; }
        public DefCatalogo getPrimero() { return catalogos.values().iterator().next(); }
    }

    private static final String PATRON_TELEFONO = "^[0-9+()\\s-]{6,30}$";

    /** Colores de la agenda: tokens del sistema de diseño (static/css/tokens/tokens.css). */
    static final List<Opcion> COLORES = List.of(
            new Opcion("wwp-info", "Azul"),
            new Opcion("wwp-danger", "Rojo"),
            new Opcion("wwp-success", "Verde"),
            new Opcion("wwp-warning", "Ámbar"),
            new Opcion("wwp-base", "Grafito"));

    private final Map<String, Seccion> secciones = new LinkedHashMap<>();

    public Secciones() {
        agregar(new Seccion("materias", "Materias y tipos de proceso",
                "Ramas del derecho y los procesos de cada una, para clasificar los expedientes", "scale",
                new DefCatalogo("materia", "Materias", "materia", true)
                        .descripcion("Al dar de baja una materia dejan de ofrecerse sus tipos de proceso y sus juzgados en las altas.")
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista()),
                new DefCatalogo("tipo_proceso", "Tipos de proceso", "tipo de proceso", false)
                        .orden("r_materia_id.nombre, t.nombre")
                        .campo(Campo.texto("nombre", "Nombre", 80).requerido().enLista())
                        .campo(Campo.referencia("materia_id", "Materia", "materia", "la materia").requerido().filtro()
                                .fijoTrasAlta().ayuda("No se cambia después del alta: los expedientes guardan el par materia y tipo."))
                        .unicoPor("Ya existe un tipo de proceso con ese nombre en la materia.", "materia_id")));

        agregar(new Seccion("juzgados", "Juzgados y circunscripciones",
                "Juzgados y tribunales donde tramitan los expedientes, por circunscripción judicial", "landmark",
                new DefCatalogo("juzgado", "Juzgados", "juzgado", false).sinCodigo()
                        .orden("r_circunscripcion_id.nombre, t.nombre, t.turno")
                        .campo(Campo.texto("nombre", "Nombre", 150).requerido().enLista())
                        .campo(Campo.referencia("circunscripcion_id", "Circunscripción", "circunscripcion", "la circunscripción").requerido().filtro())
                        .campo(Campo.referencia("materia_id", "Materia", "materia", "la materia").requerido().filtro())
                        .campo(Campo.texto("turno", "Turno (n.º de juzgado)", 30).enLista()
                                .ayuda("No es un horario: distingue a los juzgados del mismo fuero (Primer turno, Segundo turno…)."))
                        .campo(Campo.texto("direccion", "Dirección", 200))
                        .campo(Campo.texto("telefono", "Teléfono", 30).enLista().patron(PATRON_TELEFONO,
                                "El teléfono solo admite números, espacios, +, guiones y paréntesis (6 a 30 caracteres)."))
                        .unicoPor("Ya existe un juzgado con ese nombre y turno en la circunscripción.", "circunscripcion_id", "turno"),
                new DefCatalogo("circunscripcion", "Circunscripciones", "circunscripción", true)
                        .descripcion("Al dar de baja una circunscripción dejan de ofrecerse sus juzgados en las altas.")
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista())));

        // Ubicaciones: una opción del menú para cada nivel (país, departamento, ciudad).
        agregar(new Seccion("paises", "Países", "Países con su prefijo telefónico, para domicilios y teléfonos de los clientes", "route",
                new DefCatalogo("pais", "Países", "país", false)
                        .descripcion("Código ISO de dos letras (PY, AR, BR…). Al dar de baja un país dejan de ofrecerse sus departamentos y ciudades.")
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista())
                        .campo(Campo.texto("prefijo_telefonico", "Prefijo telefónico", 5).requerido().enLista()
                                .patron("^\\+\\d{1,4}$", "El prefijo es + y de 1 a 4 dígitos, por ejemplo +595.")
                                .ayuda("Por ejemplo: +595 (Paraguay), +54 (Argentina)."))));
        agregar(new Seccion("departamentos", "Departamentos", "Departamentos de cada país (en otros países, provincias o estados)", "route",
                new DefCatalogo("departamento", "Departamentos", "departamento", false)
                        .orden("r_pais_id.nombre, t.nombre")
                        .descripcion("En otros países, la provincia o el estado. Al dar de baja un departamento dejan de ofrecerse sus ciudades.")
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista())
                        .campo(Campo.referencia("pais_id", "País", "pais", "el país").requerido().filtro())
                        .unicoPor("Ya existe un departamento con ese nombre en el país.", "pais_id")));
        agregar(new Seccion("ciudades", "Ciudades", "Ciudades de cada departamento, para el domicilio de los clientes", "route",
                new DefCatalogo("ciudad", "Ciudades", "ciudad", true).sinCodigo()
                        .orden("r_departamento_id.nombre, t.nombre")
                        .campo(Campo.texto("nombre", "Nombre", 80).requerido().enLista())
                        .campo(Campo.referencia("departamento_id", "Departamento", "departamento", "el departamento").requerido().filtro())
                        .unicoPor("Ya existe una ciudad con ese nombre en el departamento.", "departamento_id")));

        agregar(new Seccion("estados", "Estados del expediente",
                "Etapas por las que pasa un expediente y cuáles cuentan como caso activo o cerrado", "workflow",
                new DefCatalogo("estado_expediente", "Estados del expediente", "estado", false)
                        .orden("t.orden, t.nombre")
                        .descripcion("Siempre debe quedar al menos un estado activo que cuente como caso activo y uno de cierre.")
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista())
                        .campo(Campo.entero("orden", "Orden", 0, 999).requerido().enLista()
                                .ayuda("Posición en los combos y reportes (0 a 999)."))
                        .campo(Campo.sino("es_activo", "Cuenta como caso activo").enLista())
                        .campo(Campo.sino("es_cierre", "Cierra el expediente").enLista())
                        .regla(ReglasEstado::validar)
                        .reglaBaja(ReglasEstado::motivoBaja)));

        agregar(new Seccion("tipos", "Tipos y roles de parte",
                "Tipos de documento, de evento de agenda y de actuación, y roles de las partes", "list-checks",
                new DefCatalogo("tipo_documento", "Tipos de documento", "tipo de documento", false)
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista()),
                new DefCatalogo("tipo_evento", "Tipos de evento", "tipo de evento", false)
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista())
                        .campo(Campo.opciones("color", "Color en la agenda", COLORES).requerido().enLista())
                        .campo(Campo.sino("es_plazo", "Es un plazo (vencimiento)").enLista())
                        .campo(Campo.entero("aviso_dias_defecto", "Días de aviso", 0, 60).enLista()
                                .ayuda("Cuántos días antes se avisa por defecto (0 a 60). Vacío: sin aviso.")),
                new DefCatalogo("tipo_actuacion", "Tipos de actuación", "tipo de actuación", false)
                        .campo(Campo.texto("nombre", "Nombre", 80).requerido().enLista()),
                new DefCatalogo("rol_parte", "Roles de parte", "rol de parte", false)
                        .campo(Campo.texto("nombre", "Nombre", 60).requerido().enLista())));
    }

    private void agregar(Seccion s) {
        secciones.put(s.getClave(), s);
    }

    public Optional<Seccion> seccion(String clave) {
        return Optional.ofNullable(secciones.get(clave));
    }

    /** La sección (materias, juzgados…) donde se administra la tabla de catálogo dada. */
    public Optional<String> seccionDe(String tabla) {
        return secciones.values().stream().filter(s -> s.getCatalogos().containsKey(tabla)).map(Seccion::getClave).findFirst();
    }
}
