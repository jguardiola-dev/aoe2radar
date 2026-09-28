package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.Actualizador;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La franja de actualizaciones y su presentador, sin pantalla ni red: un Actualizador falso y Tareas en línea
 * (todo en el hilo del test, que es el EDT gracias a invokeAndWait).
 */
class ActualizacionesTest {

    private static void enEdt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static final class ActualizadorFalso implements Actualizador {
        Resultado comprobacion = new Resultado(Estado.AL_DIA, null), descarga = new Resultado(Estado.LISTA, "1.5");
        String[] fallida;
        boolean aplicada = true;
        final List<String> llamadas = new ArrayList<>();
        @Override public boolean activo() { return true; }
        @Override public Resultado comprobar(boolean auto) { llamadas.add("comprobar"); return comprobacion; }
        @Override public Resultado descargar() { llamadas.add("descargar"); return descarga; }
        @Override public boolean aplicarAlCerrar() { llamadas.add("aplicar"); return aplicada; }
        @Override public String[] avisoFallida() { String[] f = fallida; fallida = null; return f; }
    }

    static final class AnfitrionFalso implements Actualizaciones.Anfitrion {
        final List<String> hechos = new ArrayList<>();
        @Override public void abrirUrl(String url) { hechos.add("url " + url); }
        @Override public void estado(String texto) { hechos.add("estado " + texto); }
        @Override public void cerrarVentana() { hechos.add("cerrar"); }
        @Override public boolean relanzar() { synchronized (hechos) { hechos.add("relanzar"); } return true; }
    }

    final ActualizadorFalso act = new ActualizadorFalso();
    final AnfitrionFalso anf = new AnfitrionFalso();

    @Test void laFranjaNaceOcultaYSinAvisoNoOcupaSitio() throws Exception {
        enEdt(() -> {
            JPanel ventana = new JPanel(new BorderLayout());
            FranjaActualizacion f = new FranjaActualizacion();
            ventana.add(f, BorderLayout.NORTH);
            ventana.add(new JPanel(), BorderLayout.CENTER);
            ventana.setSize(800, 600);
            ventana.doLayout();
            assertFalse(f.isVisible(), "el harness no está instalado: nunca sale y las capturas no cambian");
            assertEquals(0, f.getHeight());
            assertFalse(f.accionBtn.isFocusable(), "no roba el foco");
            assertFalse(f.cerrarBtn.isFocusable());
        });
    }

    @Test void listaEnsenaReiniciarYReiniciarCierraPorElCierreNormal() throws Exception {
        act.comprobacion = new Actualizador.Resultado(Actualizador.Estado.LISTA, "1.5");
        enEdt(() -> {
            FranjaActualizacion f = new FranjaActualizacion();
            Actualizaciones a = new Actualizaciones(act, f, anf, Tareas.EN_LINEA);
            a.comprobar(false);
            assertTrue(f.isVisible());
            assertTrue(f.textoActual().contains("1.5"), f.textoActual());
            assertTrue(f.accionBtn.isVisible());
            assertTrue(anf.hechos.isEmpty(), "automática: nada en la barra de estado ni diálogos");
            f.accionBtn.doClick();
            assertEquals(List.of("cerrar"), anf.hechos, "sale por el cierre normal, no con System.exit");
            a.alCerrar();
            assertEquals(List.of("comprobar", "aplicar"), act.llamadas);
            assertEquals(List.of("cerrar", "relanzar"), anf.hechos, "y tras aplicar, relanza");
        });
    }

    @Test void unCierreNormalAplicaPeroNoRelanza() throws Exception {
        enEdt(() -> new Actualizaciones(act, new FranjaActualizacion(), anf, Tareas.EN_LINEA).alCerrar());
        assertEquals(List.of("aplicar"), act.llamadas);
        assertTrue(anf.hechos.isEmpty());
    }

    @Test void completaOfreceElInstaladorEnElNavegador() throws Exception {
        act.comprobacion = new Actualizador.Resultado(Actualizador.Estado.COMPLETA, "1.5");
        enEdt(() -> {
            FranjaActualizacion f = new FranjaActualizacion();
            new Actualizaciones(act, f, anf, Tareas.EN_LINEA).comprobar(false);
            assertTrue(f.isVisible());
            f.accionBtn.doClick();
            assertEquals(List.of("url " + dev.tirador.aoe2radar.util.Identidad.RELEASES_URL), anf.hechos);
        });
    }

    @Test void disponibleDescargaAlPulsarYSiFallaVuelveElBoton() throws Exception {
        act.comprobacion = new Actualizador.Resultado(Actualizador.Estado.DISPONIBLE, "1.5");
        act.descarga = new Actualizador.Resultado(Actualizador.Estado.FALLO_DESCARGA, "1.5");
        enEdt(() -> {
            FranjaActualizacion f = new FranjaActualizacion();
            Actualizaciones a = new Actualizaciones(act, f, anf, Tareas.EN_LINEA);
            a.comprobar(false);
            String disponible = f.textoActual();
            f.accionBtn.doClick();
            assertEquals(List.of("comprobar", "descargar"), act.llamadas);
            assertEquals(disponible, f.textoActual(), "el aviso sigue");
            assertTrue(f.accionBtn.isEnabled(), "y el botón vuelve a estar a mano");
            act.descarga = new Actualizador.Resultado(Actualizador.Estado.LISTA, "1.5");
            f.accionBtn.doClick();
            assertTrue(f.textoActual().contains("1.5") && !f.textoActual().equals(disponible), "ya está lista");
        });
    }

    @Test void laXLaOcultaYLaComprobacionDeCada12hNoLaResucita() throws Exception {
        act.comprobacion = new Actualizador.Resultado(Actualizador.Estado.LISTA, "1.5");
        enEdt(() -> {
            FranjaActualizacion f = new FranjaActualizacion();
            Actualizaciones a = new Actualizaciones(act, f, anf, Tareas.EN_LINEA);
            a.comprobar(false);
            f.cerrarBtn.doClick();
            assertFalse(f.isVisible());
            a.comprobar(false);
            assertFalse(f.isVisible(), "el mismo aviso no vuelve en esta sesión");
            act.comprobacion = new Actualizador.Resultado(Actualizador.Estado.LISTA, "1.6");
            a.comprobar(false);
            assertTrue(f.isVisible(), "uno distinto sí");
        });
    }

    @Test void laManualLoCuentaEnLaBarraDeEstado() throws Exception {
        enEdt(() -> {
            Actualizaciones a = new Actualizaciones(act, new FranjaActualizacion(), anf, Tareas.EN_LINEA);
            a.comprobar(true);
            act.comprobacion = new Actualizador.Resultado(Actualizador.Estado.SIN_DATOS, null);
            a.comprobar(true);
            a.comprobar(false);
        });
        assertEquals(2, anf.hechos.size(), anf.hechos.toString());
        assertTrue(anf.hechos.get(0).startsWith("estado "));
    }

    @Test void avisaUnaVezDeUnaActualizacionQueNoArranco() throws Exception {
        act.fallida = new String[]{ "1.5", "1.4" };
        enEdt(() -> {
            FranjaActualizacion f = new FranjaActualizacion();
            Actualizaciones a = new Actualizaciones(act, f, anf, Tareas.EN_LINEA);
            a.comprobar(false);
            assertTrue(f.isVisible());
            assertTrue(f.textoActual().contains("1.5") && f.textoActual().contains("1.4"), f.textoActual());
            assertFalse(f.accionBtn.isVisible(), "sin botón: solo informa");
        });
    }

    /** El guion real tras volver atrás: update.json sigue anunciando la misma versión, que ahora sale COMPLETA. Un solo
     *  aviso (el del fallo, con el botón del instalador): antes el segundo tapaba al primero en el acto. */
    @Test void trasVolverAtrasElAvisoDelFalloNoLoTapaElDeReinstalar() throws Exception {
        act.fallida = new String[]{ "1.5", "1.4" };
        act.comprobacion = new Actualizador.Resultado(Actualizador.Estado.COMPLETA, "1.5");
        enEdt(() -> {
            FranjaActualizacion f = new FranjaActualizacion();
            new Actualizaciones(act, f, anf, Tareas.EN_LINEA).comprobar(false);
            assertTrue(f.textoActual().contains("no pudo arrancar") || f.textoActual().contains("could not start"), f.textoActual());
            assertTrue(f.accionBtn.isVisible(), "con [Descargar instalador]");
            f.accionBtn.doClick();
            assertEquals(List.of("url " + dev.tirador.aoe2radar.util.Identidad.RELEASES_URL), anf.hechos);
        });
    }
}
