package dev.tirador.aoe2radar.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.parse;
import static dev.tirador.aoe2radar.util.Json.val;

/**
 * El único endpoint de Steam que usa la app (antes, dentro de nicksAnteriores): el historial de nombres
 * (ajaxaliases) de un steamId. Pasa por ApiClient.textoCon429 igual que el companion (antes httpText429 a secas),
 * pero Steam NO es un host del companion: Freno.aplicaA(url) da false para steamcommunity.com, así que un 429 de
 * Steam no cuenta al Throttle ni pausa la app global (ver ApiClientTest, «un429DeSteamNoTocaElFrenoDelCompanion»).
 * Mismo comportamiento que la 1.1: el freno decide por host, no por quién llama.
 */
public final class SteamApi {
    private final ApiClient api;

    public SteamApi(ApiClient api) { this.api = api; }

    /**
     * Los alias anteriores de un steamId, en el orden en que llegan: {nombre, cuando} (cuando puede quedar «»,
     * igual que antes). Si Steam no devuelve una lista (perfil sin historial: por ejemplo, el cuerpo «false»), la
     * lista sale vacía. Un JSON roto o un fallo de red se propagan tal cual (RuntimeException o IOException/
     * InterruptedException): la app decide qué avisar, como hacía el catch (Exception ex) de nicksAnteriores.
     */
    public List<String[]> alias(String steamId) throws IOException, InterruptedException {
        avisarSiUi("SteamApi.alias");
        Object aliases = parse(api.textoCon429("https://steamcommunity.com/profiles/" + steamId + "/ajaxaliases"));
        List<String[]> out = new ArrayList<>();
        if (aliases instanceof List<?> l)
            for (Object o : l) {
                Map<String, Object> a = obj(o);
                String n = String.valueOf(firstNonNull(val(a, "newname"), ""));
                String cuando = String.valueOf(firstNonNull(val(a, "timechanged"), ""));
                if (!n.isBlank()) out.add(new String[]{ n, cuando });
            }
        return out;
    }
}
