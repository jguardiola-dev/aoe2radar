package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.Freno;
import dev.tirador.aoe2radar.api.Transporte;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.parse;
import static dev.tirador.aoe2radar.util.Json.val;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * El «mando a distancia» (control.json en sfr-data) y la comprobación de versión (GitHub Releases): dos
 * lecturas de red de solo texto, sin Swing ni pantalla. Cada una usa su propio Transporte inyectable porque
 * en la 1.1 tenían timeouts distintos (control.json, 20 s; la comprobación de versión, la del cliente normal);
 * así el servicio se prueba sin red y sin cambiar ese detalle.
 */
public final class ControlService {
    private final Transporte transporteControl;
    private final Transporte transporteVersion;

    public ControlService(Transporte transporteControl, Transporte transporteVersion) {
        this.transporteControl = transporteControl;
        this.transporteVersion = transporteVersion;
    }

    /**
     * Baja control.json y lo aplica a Freno.CONTROL (multiplicadores e interruptores; los valores nulos se
     * ignoran). Devuelve el mensaje nuevo a mostrar en la barra de estado, o null si no hay nada que enseñar
     * (sin mensaje, ya visto, estado distinto de 200 o cuerpo que no es un objeto) o si falló la red.
     * visto: el texto ya mostrado la última vez (config «control_msg_visto»); marcarVisto: cómo guardarlo si
     * hay uno nuevo. No lanza.
     */
    public String cargarControl(String visto, Consumer<String> marcarVisto) {
        avisarSiUi("ControlService.cargarControl");
        try {
            Transporte.Respuesta r = transporteControl.get(Freno.CONTROL_URL);
            if (r.estado() != 200) return null;
            Object root = parse(r.cuerpo());
            if (!(root instanceof Map<?, ?> m)) return null;
            Freno.CONTROL.clear();
            for (Map.Entry<?, ?> en : m.entrySet()) if (en.getValue() != null) Freno.CONTROL.put(String.valueOf(en.getKey()), en.getValue());
            log("control.json: " + Freno.CONTROL);
            Object msg = Freno.CONTROL.get("mensaje");
            if (msg instanceof String txt && !txt.isBlank() && !txt.equals(visto)) {
                marcarVisto.accept(txt);
                return txt;
            }
            return null;
        } catch (Exception ex) {
            log("control.json: " + causa(ex));
            return null;
        }
    }

    /**
     * Última versión publicada en GitHub Releases (el «tag_name», p. ej. «v1.2»), o null si no se pudo consultar.
     * Un estado que no es 2xx se anota en el log como «actualizaciones: HTTP nnn», igual que en la 1.1 (ahí
     * httpText delegaba en ApiClient.texto, que lanzaba esa misma IOException).
     */
    public String ultimaVersion(String releasesApi) {
        avisarSiUi("ControlService.ultimaVersion");
        try {
            Transporte.Respuesta r = transporteVersion.get(releasesApi);
            if (r.estado() / 100 != 2) throw new IOException("HTTP " + r.estado());
            Object t = val(obj(parse(r.cuerpo())), "tag_name");
            return t == null ? null : String.valueOf(t).trim();
        } catch (Exception ex) {
            log("actualizaciones: " + causa(ex));
            return null;
        }
    }
}
