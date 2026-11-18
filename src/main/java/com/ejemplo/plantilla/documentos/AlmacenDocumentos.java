package com.ejemplo.plantilla.documentos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carpeta donde viven los archivos de los documentos, fuera de la base y del proyecto (app.documentos.carpeta, por
 * defecto la variable de entorno APP_DOCUMENTOS_DIR o ~/LexNova/documentos). Cada archivo se guarda como
 * {@code expedientes/<id>/<uuid>.<extensión>}: el nombre lo pone el sistema, nunca el usuario, y toda ruta se
 * comprueba dentro de la carpeta raíz. Esta carpeta hay que respaldarla junto con la base.
 */
@Component
public class AlmacenDocumentos {

    /** Lo que se guardó: ruta relativa (con "/"), tamaño y hash SHA-256 del contenido. */
    public static final class Guardado {
        public final String ruta;
        public final long tamano;
        public final String hash;

        Guardado(String ruta, long tamano, String hash) {
            this.ruta = ruta;
            this.tamano = tamano;
            this.hash = hash;
        }
    }

    private final Path raiz;

    public AlmacenDocumentos(@Value("${app.documentos.carpeta}") String carpeta) {
        this.raiz = Path.of(carpeta).toAbsolutePath().normalize();
    }

    public Path raiz() {
        return raiz;
    }

    /** Guarda el contenido en la carpeta del expediente. Escribe primero a un temporal y lo mueve al terminar. */
    public Guardado guardar(long expedienteId, String extension, InputStream contenido) {
        String relativa = "expedientes/" + expedienteId + "/" + UUID.randomUUID() + "." + extension;
        Path destino = resolver(relativa);
        Path temporal = null;
        try {
            Files.createDirectories(destino.getParent());
            temporal = Files.createTempFile(destino.getParent(), "subida-", ".tmp");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long tamano;
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temporal), sha)) {
                tamano = contenido.transferTo(out);
            }
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            return new Guardado(relativa, tamano, hex(sha.digest()));
        } catch (IOException e) {
            borrarSilencioso(temporal);
            throw new UncheckedIOException("No se pudo guardar el archivo en " + raiz, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(String.format("%02x", b));
        return s.toString();
    }

    /** El archivo de una ruta relativa guardada. Lanza si la ruta sale de la carpeta raíz. */
    public Path resolver(String relativa) {
        Path p = raiz.resolve(relativa).normalize();
        if (!p.startsWith(raiz)) throw new IllegalArgumentException("Ruta fuera de la carpeta de documentos: " + relativa);
        return p;
    }

    /** Borra un archivo recién guardado cuando el alta no se pudo completar. Los documentos dados de baja no se borran. */
    public void descartar(String relativa) {
        borrarSilencioso(resolver(relativa));
    }

    private static void borrarSilencioso(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignorado) {
            // Queda un archivo huérfano en la carpeta; no afecta a los datos.
        }
    }
}
