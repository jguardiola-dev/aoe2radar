package dev.tirador.aoe2radar.cache;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static dev.tirador.aoe2radar.util.Config.leerConfig;

/** Catálogos de mapas y civilizaciones: semilla en el código y ampliación aprendida. */
public final class Catalogos {
    private Catalogos() {}

    // Catálogos dinámicos de mapas y civs: semilla embebida + todo lo que la
    // app va viendo en partidas reales (persistido en config). Así los
    // selectores crecen solos con cada parche.
    public static final Set<String> MAPAS_CAT = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    public static final Set<String> CIVS_CAT  = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    public static final String[] CIVS_SEMILLA = { "Armenians","Aztecs","Bengalis","Berbers","Bohemians","Britons",
            "Bulgarians","Burgundians","Burmese","Byzantines","Celts","Chinese","Cumans","Dravidians","Ethiopians",
            "Franks","Georgians","Goths","Gurjaras","Hindustanis","Huns","Incas","Italians","Japanese","Jurchens",
            "Khitans","Khmer","Koreans","Lithuanians","Magyars","Malay","Malians","Maya","Mongols","Persians",
            "Poles","Portuguese","Romans","Saracens","Shu","Sicilians","Slavs","Spanish","Tatars","Teutons",
            "Turks","Vietnamese","Vikings","Wei","Wu" };
    public static final String[] MAPAS_SEMILLA = { "Arabia","Arena","Nomad","African Clearing","Acropolis","Atacama",
            "Baltic","Black Forest","Enclosed","Four Lakes","Ghost Lake","Gold Rush","Golden Pit","Hideout",
            "Hill Fort","Islands","Lombardia","Mediterranean","Megarandom","Mountain Pass","Oasis","Serengeti",
            "Socotra","Steppe" };

    public static void cargarCatalogos() {
        MAPAS_CAT.addAll(Arrays.asList(MAPAS_SEMILLA));
        CIVS_CAT.addAll(Arrays.asList(CIVS_SEMILLA));
        for (String m : leerConfig("mapas_ranked", "").split(",")) if (!m.isBlank()) MAPAS_CAT.add(m.trim());
        for (String c : leerConfig("civs_vistas", "").split(",")) if (!c.isBlank()) CIVS_CAT.add(c.trim());
    }
}
