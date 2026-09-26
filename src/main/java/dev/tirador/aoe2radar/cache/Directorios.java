package dev.tirador.aoe2radar.cache;

import java.nio.file.Path;

/** Carpeta raíz de las cachés en disco («sfrdata»: ladder, perfiles, países, mapas…). El nombre LADDER_DIR es histórico. */
public final class Directorios {
    private Directorios() {}

    public static final Path LADDER_DIR = Path.of("sfrdata");
}
