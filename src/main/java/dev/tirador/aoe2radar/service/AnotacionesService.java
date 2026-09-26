package dev.tirador.aoe2radar.service;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Poner, quitar y leer el alias y la nota de un jugador (antes, la parte sin diálogo de pedirAlias/pedirNota/
 * borrarNota de la app). Los mapas en memoria (alias, notas) son los MISMOS objetos que cache.Anotaciones.ALIASES/
 * NOTAS cuando esto se cablea en la app: un solo estado compartido, como en la 1.1. La persistencia (una clave de
 * config por jugador, «alias_&lt;pid&gt;» / «nota_&lt;pid&gt;») queda INYECTADA (guardarConfig), así que esta clase
 * se prueba sin disco. Los diálogos (JOptionPane, JTextArea) se quedan en la app.
 */
public final class AnotacionesService {
    private final Map<Long, String> alias;
    private final Map<Long, String> notas;
    private final BiConsumer<String, String> guardarConfig;

    public AnotacionesService(Map<Long, String> alias, Map<Long, String> notas, BiConsumer<String, String> guardarConfig) {
        this.alias = alias;
        this.notas = notas;
        this.guardarConfig = guardarConfig;
    }

    /**
     * Antes, dentro de pedirAlias: nuevo debe llegar ya recortado (trim), como hacía la app. Si nuevo está vacío o es
     * igual al original, se quita del mapa Y se borra la clave de config (arreglo de DEUDA fila 101: antes de la
     * fase 4, el caso «igual al original» dejaba «alias_&lt;pid&gt;=Original» en el archivo, así que al reiniciar la
     * app reaparecía como alias; ahora se guarda vacío, igual que hace ponerNota con las notas vacías).
     */
    public void ponerAlias(long pid, String original, String nuevo) {
        if (nuevo.isEmpty() || nuevo.equals(original)) { alias.remove(pid); guardarConfig.accept("alias_" + pid, ""); }
        else { alias.put(pid, nuevo); guardarConfig.accept("alias_" + pid, nuevo); }
    }

    /** Antes, dentro de pedirNota (y borrarNota, con nota ""): vacía quita la nota; si no, la guarda. */
    public void ponerNota(long pid, String nota) {
        if (nota.isEmpty()) notas.remove(pid); else notas.put(pid, nota);
        guardarConfig.accept("nota_" + pid, nota);
    }

    public String aliasDe(long pid) { return alias.get(pid); }

    public String notaDe(long pid) { return notas.get(pid); }
}
