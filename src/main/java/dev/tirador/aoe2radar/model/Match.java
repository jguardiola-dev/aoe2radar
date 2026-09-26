package dev.tirador.aoe2radar.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Match {
    public long id;
    public Instant started, finished;
    public String map = "?", mode = "?";
    public List<MatchPlayer> players = new ArrayList<>();
    public String estado = "";
    public long refId;          // jugador seguido de referencia (primer nombre del archivo)
    public volatile boolean enDisco;     // se escribe también desde el hilo de la descarga
    public boolean azar;                 // vino de «Al azar por ELO…»: el enfrentamiento muestra el ELO del momento
    public volatile boolean enJuego;     // copia presente en la carpeta savegame (se escribe también desde el hilo de la descarga/copia)
    public boolean fantasma;             // «en curso» según la API pero imposible: el mismo jugador tiene otra partida posterior
    public String mapaClave;             // clave del mapa (rm_arabia) cuando viene de un paquete de sfr-data
    public int gte;                      // > 0: partida «Guess the ELO» nº gte (anónima en la app)     // la rec ya existe en ./recs

    public String civDe(long pid) {
        if (gte > 0) return "";
        for (MatchPlayer p : players) if (p.id == pid) return p.civ == null ? "" : p.civ;
        return "";
    }

    /** Columna Civ rival: solo en 1v1 (en equipos, el detalle va en el tooltip). */
    public String civRival() {
        if (gte > 0 || players.size() != 2) return "";
        for (MatchPlayer p : players) if (p.id != refId) return p.civ == null ? "" : p.civ;
        return "";
    }

    public boolean tieneJugador(long pid) {
        for (MatchPlayer p : players) if (p.id == pid) return true;
        return false;
    }

    public int povsConRec() {
        int n = 0;
        for (MatchPlayer p : players) if (Boolean.TRUE.equals(p.replay)) n++;
        return n;
    }
}
