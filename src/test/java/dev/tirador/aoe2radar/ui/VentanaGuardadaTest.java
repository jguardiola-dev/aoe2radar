package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.util.Properties;

import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
