package com.ejemplo.plantilla.roles;

import java.util.ArrayList;
import java.util.List;

/**
 * El menú completo como árbol, para elegir en un rol qué pantallas ve y qué acciones hace en cada una:
 * módulo → sección (puede no tener rótulo) → pantalla → acciones.
 */
public final class ArbolMenu {

    private ArbolMenu() {
    }

    public static final class Modulo {
        private final long id;
        private final String titulo;
        private final String icono;
        private final List<Seccion> secciones = new ArrayList<>();

        Modulo(long id, String titulo, String icono) {
            this.id = id;
            this.titulo = titulo;
            this.icono = icono;
        }

        public long getId() { return id; }
        public String getTitulo() { return titulo; }
        public String getIcono() { return icono; }
        public List<Seccion> getSecciones() { return secciones; }

        public List<Pantalla> getPantallas() {
            List<Pantalla> l = new ArrayList<>();
            secciones.forEach(s -> l.addAll(s.getPantallas()));
            return l;
        }
    }

    public static final class Seccion {
        private final String titulo;
        private final List<Pantalla> pantallas = new ArrayList<>();

        Seccion(String titulo) {
            this.titulo = titulo;
        }

        public String getTitulo() { return titulo; }
        public List<Pantalla> getPantallas() { return pantallas; }
    }

    public static final class Pantalla {
        private final long id;
        private final String titulo;
        private final String ruta;
        private final List<Accion> acciones = new ArrayList<>();

        Pantalla(long id, String titulo, String ruta) {
            this.id = id;
            this.titulo = titulo;
            this.ruta = ruta;
        }

        public long getId() { return id; }
        public String getTitulo() { return titulo; }
        public String getRuta() { return ruta; }
        public List<Accion> getAcciones() { return acciones; }
    }

    public static final class Accion {
        private final long id;
        private final String codigo;
        private final String descripcion;

        Accion(long id, String codigo, String descripcion) {
            this.id = id;
            this.codigo = codigo;
            this.descripcion = descripcion;
        }

        public long getId() { return id; }
        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
    }
}
