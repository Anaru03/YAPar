import gui.YAParGUI;
import javax.swing.SwingUtilities;

/**
 * Main — Punto de entrada de YAPar.
 *
 * La ejecución completa ahora se realiza
 * desde la interfaz gráfica Swing.
 *
 * El core utilizado por la GUI es EXACTAMENTE
 * el mismo que el usado anteriormente en consola:
 *
 * - YALexRunner
 * - YAParFileParser
 * - Grammar
 * - LR0Automaton
 * - SLRTable
 * - Parser
 *
 * Esto garantiza resultados idénticos
 * entre GUI y ejecución manual.
 */
public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            try {

                new YAParGUI().setVisible(true);

            }

            catch (Exception ex) {

                ex.printStackTrace();

                System.err.println(
                        "ERROR iniciando GUI:"
                );

                System.err.println(
                        ex.getMessage()
                );
            }
        });
    }
}