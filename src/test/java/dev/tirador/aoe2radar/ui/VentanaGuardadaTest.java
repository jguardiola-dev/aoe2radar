package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometría de la ventana (VentanaGuardada.aplicar/guardar) con una config temporal: el fichero
 * que usa Config en los tests vive en target/harness (workingDirectory de Surefire, ver pom.xml)
 * y se borra antes y después de cada test para que no queden restos de un test a otro. El JFrame
 * nunca se muestra (invokeAndWait, sin setVisible), como pide la tarea: no hace falta pantalla.
 */
class VentanaGuardadaTest {

    @BeforeEach
    @AfterEach
    void limpiarConfig() throws Exception {
        Files.deleteIfExists(CONFIG_FILE);
    }

    private Properties leerConfigDeDisco() throws Exception {
        Properties p = new Properties();
        try (var in = Files.newInputStream(CONFIG_FILE)) {
            p.load(in);
        }
        return p;
    }

    @Test
    void aplicarSinConfigPreviaUsaElTamanoPorDefecto() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                VentanaGuardada.aplicar(f);
                assertEquals(1180, f.getWidth());
                assertEquals(680, f.getHeight());
                assertEquals(0, f.getExtendedState() & JFrame.MAXIMIZED_BOTH);
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void aplicarRestauraPosicionYTamanoGuardados() throws Exception {
        Files.writeString(CONFIG_FILE, "ventana=100,120,900,600\n");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                VentanaGuardada.aplicar(f);
                assertEquals(new Rectangle(100, 120, 900, 600), f.getBounds());
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void aplicarMaximizadoRestauraElExtendedState() throws Exception {
        Files.writeString(CONFIG_FILE, "ventana_max=true\n");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                VentanaGuardada.aplicar(f);
                assertEquals(JFrame.MAXIMIZED_BOTH, f.getExtendedState() & JFrame.MAXIMIZED_BOTH);
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void aplicarConPosicionFueraDePantallaSeCentra() throws Exception {
        Files.writeString(CONFIG_FILE, "ventana=-100000,-100000,900,600\n");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                VentanaGuardada.aplicar(f);
                assertNotEquals(-100000, f.getX());   // posición absurda: se ignora y se centra
                assertEquals(900, f.getWidth());
                assertEquals(600, f.getHeight());
            } finally {
                f.dispose();
            }
        });
    }

    // ----- F11 (revisión 1.3): guardar al cambiar, no solo al cerrar -----

    /** Espera (como mucho 3 s) a que el hilo «ventana-guarda» deje la clave en disco. */
    private Properties esperarClave(String clave) throws Exception {
        for (int i = 0; i < 60; i++) {
            if (Files.exists(CONFIG_FILE)) {
                Properties p = leerConfigDeDisco();
                if (p.getProperty(clave) != null) return p;
            }
            Thread.sleep(50);
        }
        return Files.exists(CONFIG_FILE) ? leerConfigDeDisco() : new Properties();
    }

    @Test
    void moverLaVentanaProgramaElGuardadoYSeGuardaSinCerrarla() throws Exception {
        javax.swing.Timer[] t = new javax.swing.Timer[1];
        JFrame[] f = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            f[0] = new JFrame();
            JSplitPane split = new JSplitPane();
            split.setDividerLocation(300);
            t[0] = VentanaGuardada.guardarAlCambiar(f[0], split);
            f[0].setBounds(30, 40, 800, 500);
            for (java.awt.event.ComponentListener l : f[0].getComponentListeners())
                l.componentMoved(new java.awt.event.ComponentEvent(f[0], java.awt.event.ComponentEvent.COMPONENT_MOVED));
            assertTrue(t[0].isRunning(), "mover la ventana programa el guardado");
            t[0].stop();
            for (java.awt.event.ActionListener al : t[0].getActionListeners()) al.actionPerformed(null);   // «pasa» 1,5 s
        });
        Properties p = esperarClave("ventana");
        // Los eventos que el setBounds dejó en la cola ya se han procesado al llegar aquí: parar el Timer ahora
        // evita que dispare dentro de otro test y le escriba la config.
        SwingUtilities.invokeAndWait(() -> { t[0].stop(); f[0].dispose(); });
        assertEquals("30,40,800,500", p.getProperty("ventana"), "guardada sin pasar por windowClosing");
        assertEquals("false", p.getProperty("ventana_max"));
        assertEquals("300", p.getProperty("divisor"));
    }

    @Test
    void cerrarMinimizadaNoPisaLaPosicionGuardada() throws Exception {
        // F11 (revisión 1.3): el cierre (p. ej. «Cerrar ventana» desde la barra de tareas) sigue la misma regla que el
        // guardado al cambiar: minimizada, se conserva la última posición buena.
        Files.writeString(CONFIG_FILE, "ventana=100,120,900,600\n");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                f.setBounds(-32000, -32000, 160, 28);
                f.setExtendedState(JFrame.ICONIFIED);
                VentanaGuardada.guardar(f, null);
            } finally {
                f.dispose();
            }
        });
        Properties p = leerConfigDeDisco();
        assertEquals("100,120,900,600", p.getProperty("ventana"));
        assertEquals("false", p.getProperty("ventana_max"));
    }

    @Test
    void moverElDivisorTambienProgramaElGuardado() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            JSplitPane split = new JSplitPane();
            try {
                javax.swing.Timer t = VentanaGuardada.guardarAlCambiar(f, split);
                split.setDividerLocation(250);
                assertTrue(t.isRunning());
                t.stop();
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void minimizadaNoSeGuardaLaPosicion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                f.setBounds(-32000, -32000, 160, 28);   // lo que Windows da a una ventana minimizada
                f.setExtendedState(JFrame.ICONIFIED);
                VentanaGuardada.guardarEnSegundoPlano(f, null);
            } finally {
                f.dispose();
            }
        });
        Thread.sleep(400);   // si hubiera arrancado el hilo de guardado, ya habría escrito
        assertFalse(Files.exists(CONFIG_FILE) && leerConfigDeDisco().getProperty("ventana") != null,
                "minimizada no se pisa la posición buena");
    }

    // ----- F9 (revisión 1.3): varios monitores, sin pantallas reales -----
    private static final Rectangle PRINCIPAL = new Rectangle(0, 0, 1920, 1040);          // sin la barra de tareas
    private static final Rectangle SEGUNDO = new Rectangle(1920, -200, 2560, 1400);      // a la derecha, más alto

    @Test
    void ubicarEnElSegundoMonitorLaDejaAhiSiSigueConectado() {
        VentanaGuardada.Ubicacion u = VentanaGuardada.ubicar(2100, 0, 2400, 1300, List.of(PRINCIPAL, SEGUNDO));
        assertEquals(new VentanaGuardada.Ubicacion(2100, 0, 2400, 1300, false), u,
                "misma posición y tamaño: cabe en el segundo monitor");
    }

    @Test
    void ubicarEnUnMonitorDesconectadoLaCentraEnLaPrincipal() {
        VentanaGuardada.Ubicacion u = VentanaGuardada.ubicar(2100, 0, 2400, 1300, List.of(PRINCIPAL));
        assertTrue(u.centrar(), "el segundo monitor ya no está: a la principal");
        assertEquals(1920, u.ancho());
        assertEquals(1040, u.alto());
    }

    @Test
    void ubicarConUnSoloMonitorEsLaCuentaDeSiempre() {
        assertEquals(new VentanaGuardada.Ubicacion(100, 120, 900, 600, false),
                VentanaGuardada.ubicar(100, 120, 900, 600, List.of(PRINCIPAL)));
        assertEquals(new VentanaGuardada.Ubicacion(-8, -8, 1920, 1040, false),
                VentanaGuardada.ubicar(-8, -8, 3000, 2000, List.of(PRINCIPAL)), "margen de 8 px y tamaño recortado");
        assertTrue(VentanaGuardada.ubicar(1830, 100, 900, 600, List.of(PRINCIPAL)).centrar(), "menos de 100 px visibles");
    }

    @Test
    void minimizarNoOlvidaQueEstabaMaximizada() throws Exception {
        // F8 (revisión 1.3): «Iniciar minimizada» + ventana maximizada. El estado debe conservar los dos bits.
        Files.writeString(CONFIG_FILE, "ventana_max=true\n");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                VentanaGuardada.aplicar(f);
                VentanaGuardada.minimizar(f);
                assertEquals(JFrame.ICONIFIED, f.getExtendedState() & JFrame.ICONIFIED, "minimizada");
                assertEquals(JFrame.MAXIMIZED_BOTH, f.getExtendedState() & JFrame.MAXIMIZED_BOTH,
                        "y al restaurarla vuelve maximizada");
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void guardarEscribeDivisorBoundsYNoMaximizado() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            JSplitPane split = new JSplitPane();
            try {
                f.setBounds(10, 20, 800, 500);
                split.setDividerLocation(321);
                VentanaGuardada.guardar(f, split);
            } finally {
                f.dispose();
            }
        });
        Properties p = leerConfigDeDisco();
        assertEquals("321", p.getProperty("divisor"));
        assertEquals("false", p.getProperty("ventana_max"));
        assertEquals("10,20,800,500", p.getProperty("ventana"));
    }

    @Test
    void guardarMaximizadoNoSobrescribeLosBounds() throws Exception {
        Files.writeString(CONFIG_FILE, "ventana=1,2,3,4\n");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                f.setExtendedState(JFrame.MAXIMIZED_BOTH);
                VentanaGuardada.guardar(f, null);
            } finally {
                f.dispose();
            }
        });
        Properties p = leerConfigDeDisco();
        assertEquals("true", p.getProperty("ventana_max"));
        assertEquals("1,2,3,4", p.getProperty("ventana"));   // maximizada: los bounds no se tocan
        assertNull(p.getProperty("divisor"));                // splitPrincipal null: no se escribe
    }
}
