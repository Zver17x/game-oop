import ui.GamePanel;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Start the client: java -cp out Main [server-ip] [port] */
public class Main {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6000;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("PIXEL METEOR - F1 SURVIVAL ONLINE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            GamePanel game = new GamePanel(host, port);
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
