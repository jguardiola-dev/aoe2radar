package dev.tirador.aoe2radar.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qué URLs pasan por el freno: todas las del companion, y solo esas. */
class FrenoTest {

    @Test void laApiDeDatosDelCompanionPasaPorElFreno() {
        assertTrue(Freno.aplicaA(Http.API + "/matches?profile_ids=1&page=1"));
    }

    @Test void twitchDelCompanionTambienPasaPorElFreno() {
        // En la 1.1 se saltaba el freno: el criterio era «empieza por data.aoe2companion.com/api».
        assertTrue(Freno.aplicaA("https://api.aoe2companion.com/twitch/live?game=13389"));
        assertTrue(Freno.aplicaA("https://api.aoe2companion.com/twitch/live?channel=12tirador"));
    }

    @Test void otrosHostsNoPasanPorElFreno() {
        assertFalse(Freno.aplicaA("https://raw.githubusercontent.com/jguardiola-dev/sfr-data/data/ladder.json"));
        assertFalse(Freno.aplicaA("https://aoe.ms/replay/?gameId=1&profileId=2"));
        assertFalse(Freno.aplicaA("https://api.github.com/repos/jguardiola-dev/aoe2radar/releases/latest"));
    }

    @Test void unHostQueSoloContieneElNombreNoEsDelCompanion() {
        assertFalse(Freno.aplicaA("https://aoe2companion.com.ejemplo.net/api"));
        assertFalse(Freno.aplicaA("https://falso-aoe2companion.com/api"));
    }

    @Test void casosLimiteDelHost() {
        assertTrue(Freno.aplicaA("HTTPS://API.AOE2COMPANION.COM/twitch/live"), "mayúsculas");
        assertTrue(Freno.aplicaA("https://data.aoe2companion.com:443/api/matches"), "con puerto");
        assertTrue(Freno.aplicaA("https://evil.com@data.aoe2companion.com/api"), "userinfo: el host real es el companion");
        assertFalse(Freno.aplicaA("https://x@evil.com/api"), "userinfo: el host real no lo es");
        assertFalse(Freno.aplicaA("https://104.21.0.1/api"), "IP");
        assertFalse(Freno.aplicaA("/api/matches"), "relativa: sin host");
    }

    @Test void urlsRarasNoRompenNada() {
        assertFalse(Freno.aplicaA("no es una url"));
        assertFalse(Freno.aplicaA(""));
    }
}
