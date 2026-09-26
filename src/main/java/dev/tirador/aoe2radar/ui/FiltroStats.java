package dev.tirador.aoe2radar.ui;

/**
 * Filtros de Civ Stats (modo, ventana, mapa, tramo de ELO) y civilización seleccionada en su
 * tabla. Es estado de la interfaz: se lee y se escribe solo en el hilo de la UI (EDT), sin
 * sincronización propia. Lo comparten la vista de Civ Stats y la banda del Tech tree, que usan
 * los mismos filtros; es la primera pieza del estado común de la app (AppState) que llegará
 * cuando esas dos vistas salgan de SpoilerFreeRecs.
 */
public final class FiltroStats {
    private String modo, ventana, mapa, tramo;
    private String civSeleccionada;

    public FiltroStats(String modo, String ventana, String mapa, String tramo) {
        this.modo = modo;
        this.ventana = ventana;
        this.mapa = mapa;
        this.tramo = tramo;
    }

    public String modo() { return modo; }
    public void modo(String modo) { this.modo = modo; }

    public String ventana() { return ventana; }
    public void ventana(String ventana) { this.ventana = ventana; }

    public String mapa() { return mapa; }
    public void mapa(String mapa) { this.mapa = mapa; }

    public String tramo() { return tramo; }
    public void tramo(String tramo) { this.tramo = tramo; }

    public String civSeleccionada() { return civSeleccionada; }
    public void civSeleccionada(String civSeleccionada) { this.civSeleccionada = civSeleccionada; }
}
