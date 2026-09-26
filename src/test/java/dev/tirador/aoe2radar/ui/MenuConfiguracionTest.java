package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.service.ControlService;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MenuConfiguracion sin pantalla: la construcción del menú (orden de ítems, checkboxes reflejando la config y
 * el Anfitrion) corre en el EDT con invokeAndWait, igual que en la app; nunca se dispara manual=true en
 * comprobarActualizacion, porque abriría un diálogo modal real. abrirDonacion() no vive aquí: se quedó en la
 * ventana porque usa java.net.URI, que ui no puede importar. El doble de Anfitrion graba qué le pide el menú,
 * como haría la ventana de verdad con sus lambdas.
 */
class MenuConfiguracionTest {

    /** Anfitrion de mentira: sin Swing real salvo lo que el propio menú construye; graba llamadas. */
    static class AnfitrionFalso implements MenuConfiguracion.Anfitrion {
        boolean mostrarEloWatch;
        boolean fijarMostrarEloWatchLlamadoCon;
        int repintarListaJugadoresVeces;
        int reiniciarVigilanciaVeces;
        int cargarAliasesYNotasVeces;
        boolean modoTop;
        int forzarRecargaTopVeces;
        Path carpetaAElegir;
        int refiltrarPartidasVeces;
        boolean hayCarpetaSavegame;
        int mostrarMiPerfilVeces;
        int cambiarCuentaPropiaVeces;
        int mostrarAcercaDeVeces;
        String ultimoTemaAplicado;
        String ultimoEstado;
        String ultimaUrlAbierta;
        String ultimaEtiquetaVersion;

        @Override public Component padre() { return null; }
        @Override public void estado(String texto) { ultimoEstado = texto; }
        @Override public void aplicarTema(String tema) { ultimoTemaAplicado = tema; }
        @Override public void abrirUrl(String url) { ultimaUrlAbierta = url; }
        @Override public void mostrarNuevaVersion(String etiqueta) { ultimaEtiquetaVersion = etiqueta; }
        @Override public boolean mostrarEloWatch() { return mostrarEloWatch; }
        @Override public void fijarMostrarEloWatch(boolean mostrar) { fijarMostrarEloWatchLlamadoCon = mostrar; mostrarEloWatch = mostrar; }
        @Override public void repintarListaJugadores() { repintarListaJugadoresVeces++; }
        @Override public void reiniciarVigilancia() { reiniciarVigilanciaVeces++; }
        @Override public void cargarAliasesYNotas() { cargarAliasesYNotasVeces++; }
        @Override public boolean modoTop() { return modoTop; }
        @Override public void forzarRecargaTop() { forzarRecargaTopVeces++; }
        @Override public Path elegirCarpetaSavegame() { return carpetaAElegir; }
        @Override public void refiltrarPartidas() { refiltrarPartidasVeces++; }
        @Override public boolean hayCarpetaSavegame() { return hayCarpetaSavegame; }
        @Override public void mostrarMiPerfil() { mostrarMiPerfilVeces++; }
        @Override public void cambiarCuentaPropia() { cambiarCuentaPropiaVeces++; }
        @Override public void mostrarAcercaDe() { mostrarAcercaDeVeces++; }
    }

    private static MenuConfiguracion nuevo(AnfitrionFalso anfitrion) throws Exception {
        MenuConfiguracion[] caja = new MenuConfiguracion[1];
        JCheckBoxMenuItem autoSgItem = new JCheckBoxMenuItem("Enviar al juego al descargar", false);
        Transporte transporteQueNoSeUsa = url -> { throw new AssertionError("no debería llamarse en este test"); };
        ControlService controlService = new ControlService(transporteQueNoSeUsa, transporteQueNoSeUsa);
        SwingUtilities.invokeAndWait(() ->
                caja[0] = new MenuConfiguracion("sistema", autoSgItem, controlService, anfitrion));
        return caja[0];
    }

    /** Busca un JMenuItem/JMenu/JCheckBoxMenuItem por su texto exacto, bajando también dentro de los JMenu. */
    private static AbstractButton buscar(Component raiz, String texto) {
        if (raiz instanceof JMenu menu) {
            if (texto.equals(menu.getText())) return menu;
            for (Component hijo : menu.getMenuComponents()) {
                AbstractButton r = buscar(hijo, texto);
                if (r != null) return r;
            }
            return null;
        }
        if (raiz instanceof AbstractButton b && texto.equals(b.getText())) return b;
        return null;
    }

    private static AbstractButton buscarEnMenu(JPopupMenu menu, String texto) {
        for (Component c : menu.getComponents()) {
            AbstractButton r = buscar(c, texto);
            if (r != null) return r;
        }
        return null;
    }

    @Test void elMenuTieneLosDiecisieteItemsDeNivelSuperiorEnOrden() throws Exception {
        MenuConfiguracion mc = nuevo(new AnfitrionFalso());
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu menu = mc.menu();
            assertEquals(17, menu.getComponentCount());
            String[] textosEsperados = {
                    "Idioma", "Tema", "Letra", null /*separador*/,
                    "Buscar al abrir", "Ejecutar al iniciar Windows", "Iniciar minimizada",
                    "Mostrar ELO en la Watchlist", "Enviar al juego al descargar",
                    "Usar CaptureAge", "Cambiar ruta de CaptureAge…", "Vigilancia de vivos",
                    "Top ladder", "Cambiar carpeta savegame…", null /*separador*/,
                    "Buscar actualizaciones…", "Acerca de…"
            };
            for (int i = 0; i < textosEsperados.length; i++) {
                Component c = menu.getComponent(i);
                if (textosEsperados[i] == null) assertInstanceOf(JSeparator.class, c, "posición " + i);
                else assertEquals(textosEsperados[i], ((AbstractButton) c).getText(), "posición " + i);
            }
        });
    }

    @Test void eloWatchItemArrancaConElValorDelAnfitrionYAlPulsarloAvisaYRepinta() throws Exception {
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        anfitrion.mostrarEloWatch = true;
        MenuConfiguracion mc = nuevo(anfitrion);
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton item = buscarEnMenu(mc.menu(), "Mostrar ELO en la Watchlist");
            assertTrue(item.isSelected(), "el checkbox debe arrancar como diga el Anfitrion");
            item.doClick();   // el usuario lo desmarca
            assertFalse(item.isSelected());
            assertFalse(anfitrion.fijarMostrarEloWatchLlamadoCon, "debe avisar al Anfitrion del nuevo valor");
            assertEquals(1, anfitrion.repintarListaJugadoresVeces);
        });
    }

    @Test void aboutItemLlamaAMostrarAcercaDe() throws Exception {
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        MenuConfiguracion mc = nuevo(anfitrion);
        SwingUtilities.invokeAndWait(() -> {
            buscarEnMenu(mc.menu(), "Acerca de…").doClick();
            assertEquals(1, anfitrion.mostrarAcercaDeVeces);
        });
    }

    @Test void vigilanciaDeVivosAlElegirUnIntervaloReiniciaLaVigilancia() throws Exception {
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        MenuConfiguracion mc = nuevo(anfitrion);
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton item = buscarEnMenu(mc.menu(), "Cada 2 min");
            item.doClick();
            assertEquals(1, anfitrion.reiniciarVigilanciaVeces);
        });
    }

    @Test void topLadderSoloFuerzaLaRecargaSiElAnfitrionEstaEnModoTop() throws Exception {
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        anfitrion.modoTop = false;
        MenuConfiguracion mc = nuevo(anfitrion);
        SwingUtilities.invokeAndWait(() -> {
            buscarEnMenu(mc.menu(), "Top 100").doClick();
            assertEquals(0, anfitrion.forzarRecargaTopVeces, "sin modo top, no hace falta recargar");
        });
        anfitrion.modoTop = true;
        SwingUtilities.invokeAndWait(() -> {
            buscarEnMenu(mc.menu(), "Top 25").doClick();
            assertEquals(1, anfitrion.forzarRecargaTopVeces);
        });
    }

    @Test void carpetaSavegameAvisaDelEstadoYRefiltraSoloSiSeEligioCarpeta() throws Exception {
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        anfitrion.carpetaAElegir = null;   // el usuario cancela el selector
        MenuConfiguracion mc = nuevo(anfitrion);
        SwingUtilities.invokeAndWait(() -> {
            buscarEnMenu(mc.menu(), "Cambiar carpeta savegame…").doClick();
            assertEquals(0, anfitrion.refiltrarPartidasVeces, "cancelar no debe refiltrar");
        });
        anfitrion.carpetaAElegir = Path.of("C:\\savegame");
        SwingUtilities.invokeAndWait(() -> {
            buscarEnMenu(mc.menu(), "Cambiar carpeta savegame…").doClick();
            assertEquals(1, anfitrion.refiltrarPartidasVeces);
            assertTrue(anfitrion.ultimoEstado.contains("C:\\savegame"), "el estado debe mencionar la carpeta elegida");
        });
    }

    @Test void autoSgItemNoSeGuardaMarcadoSiNoHayCarpetaDeSavegame() throws Exception {
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        anfitrion.hayCarpetaSavegame = false;
        MenuConfiguracion mc = nuevo(anfitrion);
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton item = buscarEnMenu(mc.menu(), "Enviar al juego al descargar");
            item.doClick();   // el usuario intenta marcarlo
            assertFalse(item.isSelected(), "sin carpeta de savegame, el ítem vuelve a desmarcarse solo");
        });
    }

    @Test void miPerfilEnLaEsquinaLlamaAMostrarMiPerfil() throws Exception {
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        MenuConfiguracion mc = nuevo(anfitrion);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(2, mc.esquina().getComponentCount(), "esquina: Mi perfil + Configuración ▾");
            AbstractButton miPerfil = (AbstractButton) mc.esquina().getComponent(0);
            assertEquals("Mi perfil", miPerfil.getText());
            miPerfil.doClick();
            assertEquals(1, anfitrion.mostrarMiPerfilVeces);
        });
    }

    @Test void comprobarActualizacionAutomaticaAvisaAlAnfitrionSiHayVersionNueva() throws Exception {
        AtomicBoolean llamado = new AtomicBoolean();
        CountDownLatch listo = new CountDownLatch(1);
        AnfitrionFalso anfitrion = new AnfitrionFalso() {
            @Override public void mostrarNuevaVersion(String etiqueta) { super.mostrarNuevaVersion(etiqueta); llamado.set(true); listo.countDown(); }
        };
        JCheckBoxMenuItem autoSgItem = new JCheckBoxMenuItem("x", false);
        Transporte version = url -> new Transporte.Respuesta(200, "{\"tag_name\":\"v99.0\"}");
        ControlService controlService = new ControlService(version, version);
        MenuConfiguracion[] caja = new MenuConfiguracion[1];
        SwingUtilities.invokeAndWait(() ->
                caja[0] = new MenuConfiguracion("sistema", autoSgItem, controlService, anfitrion));
        // manual=false: nunca abre diálogos ni navega, solo puede avisar al botón "Nueva versión" si toca.
        caja[0].comprobarActualizacion(false);
        assertTrue(listo.await(3, TimeUnit.SECONDS), "el hilo 'actualizaciones' debía terminar y avisar por el EDT");
        assertTrue(llamado.get());
        assertTrue(anfitrion.ultimaEtiquetaVersion.contains("99.0"));
    }
}
