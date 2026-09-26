package dev.tirador.aoe2radar.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * El «estado de la app» del que habla docs/ARQUITECTURA.md (apartado {@code app}): qué vista está activa y el
 * historial de navegación entre pestañas (Directos, Ratings, Civ Stats, Live now, Tech tree, Perfil…), con
 * oyentes para que quien pinte (hoy, {@link Navegador}) se entere de los cambios sin que este objeto conozca
 * Swing. Antes eran cinco campos sueltos de SpoilerFreeRecs ({@code historial}, {@code historialPos},
 * {@code navegandoAtras} y el record {@code Destino}); juntarlos aquí es la idea de «estado observable»: un
 * único dueño de la verdad, y quien lo necesita se suscribe en vez de leer (y mutar) campos ajenos a mano.
 */
public final class AppState {

    /** Un sitio del historial: la vista y, si aplica, el jugador o la civ que tenía abiertos. */
    public record Destino(String vista, long pid, String nombre, String civ) { }

    private final List<Destino> historial = new ArrayList<>();
    private int historialPos = -1;
    private boolean navegandoAtras;
    private final List<Runnable> oyentes = new ArrayList<>();

    /** Se llama cada vez que cambia el historial (p. ej., para refrescar los botones de atrás/adelante). */
    public void agregarOyente(Runnable oyente) { oyentes.add(oyente); }

    private void avisar() { for (Runnable o : oyentes) o.run(); }

    /** La vista activa: el último destino registrado, o {@code null} si el historial está vacío. */
    public Destino actual() { return historialPos >= 0 && historialPos < historial.size() ? historial.get(historialPos) : null; }

    /** Posición actual dentro del historial (-1 si está vacío). Visible para RegresionCapturas. */
    public int historialPos() { return historialPos; }

    /** Nº de destinos guardados. Visible para RegresionCapturas. */
    public int tamanoHistorial() { return historial.size(); }

    public boolean navegandoAtras() { return navegandoAtras; }

    public boolean puedeVolver() { return historialPos > 0; }

    public boolean puedeAvanzar() { return historialPos >= 0 && historialPos < historial.size() - 1; }

    /** Añade un destino nuevo, salvo que sea el mismo que el activo o que estemos navegando por el historial
     *  (volverAtras/irAdelante ya mueven la posición: no hay que añadir nada). Tope de 60 entradas y, como en
     *  cualquier navegador, ir a un sitio nuevo corta el «adelante». */
    public void registrarDestino(Destino d) {
        if (navegandoAtras) return;
        if (historialPos >= 0 && historialPos < historial.size()) {
            Destino u = historial.get(historialPos);
            if (u.vista().equals(d.vista()) && u.pid() == d.pid() && Objects.equals(u.civ(), d.civ())) return;
        }
        while (historial.size() > historialPos + 1) historial.remove(historial.size() - 1);   // una ruta nueva borra el «adelante»
        historial.add(d);
        while (historial.size() > 60) historial.remove(0);
        historialPos = historial.size() - 1;
        avisar();
    }

    /** Mueve la posición una casilla atrás y devuelve el destino al que hay que ir, o {@code null} si no se
     *  puede. Marca «navegando atrás» para que el registrarDestino que dispare esa vista no añada una entrada
     *  nueva: quien llama debe cerrar con {@link #dejarDeNavegar()} (en un {@code finally}, como hacía el
     *  método original). */
    public Destino prepararAtras() {
        if (!puedeVolver()) return null;
        historialPos--;
        navegandoAtras = true;
        return historial.get(historialPos);
    }

    /** Simétrico de {@link #prepararAtras()} para «adelante». */
    public Destino prepararAdelante() {
        if (!puedeAvanzar()) return null;
        historialPos++;
        navegandoAtras = true;
        return historial.get(historialPos);
    }

    /** Cierra un volverAtras()/irAdelante() en curso (ver prepararAtras/prepararAdelante). No avisa a los
     *  oyentes: quien navega decide cuándo hacerlo (mismo orden que el código original). */
    public void dejarDeNavegar() { navegandoAtras = false; }
}
