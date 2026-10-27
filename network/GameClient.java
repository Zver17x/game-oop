package network;

import model.CarView;
import model.MeteorView;
import model.ViewState;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** TCP networking (view receives authoritative snapshots). */
public class GameClient {
    public interface Listener {
        void onWelcome(int playerId);
        void onState(ViewState state);
        void onDisconnected(String reason);
    }

    private final String host;
    private final int port;
    private final Listener listener;
    private volatile PrintWriter output;
    private volatile Socket socket;
    private volatile int myId;

    public GameClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void connectAsync() {
        Thread t = new Thread(this::receiveLoop, "meteor-client-reader");
        t.setDaemon(true);
        t.start();
    }

    private void receiveLoop() {
        String reason = "Server closed connection";
        try (Socket s = new Socket()) {
            socket = s;
            s.connect(new InetSocketAddress(host, port), 4000);
            s.setTcpNoDelay(true);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    s.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(new OutputStreamWriter(
                    s.getOutputStream(), StandardCharsets.UTF_8), true);
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("WELCOME|")) {
                    int id = Integer.parseInt(line.substring(8));
                    myId = id;
                    SwingUtilities.invokeLater(() -> listener.onWelcome(id));
                } else if (line.startsWith("STATE|")) {
                    ViewState s2 = parseSnapshot(line, myId);
                    SwingUtilities.invokeLater(() -> listener.onState(s2));
                } else if (line.startsWith("ERROR|")) {
                    reason = line.substring(6);
                    break;
                }
            }
        } catch (IOException | RuntimeException ex) {
            reason = ex.getMessage() == null ? "Network error" : ex.getMessage();
        } finally {
            output = null;
            socket = null;
            String finalReason = reason;
            SwingUtilities.invokeLater(() -> listener.onDisconnected(finalReason));
        }
    }

    private static ViewState parseSnapshot(String line, int playerId) {
        String[] p = line.split("\\|", -1);
        if (p.length != 8) throw new IllegalArgumentException("Bad snapshot length");
        ViewState result = new ViewState();
        switch (p[1]) {
            case "WAITING": result.status = ViewState.ST_START; break;
            case "PLAYING": result.status = ViewState.ST_PLAY; break;
            case "GAMEOVER": result.status = ViewState.ST_GAMEOVER; break;
            default: throw new IllegalArgumentException("Unknown state: " + p[1]);
        }
        result.myId = playerId;
        result.tick = Integer.parseInt(p[2]);
        result.time = Long.parseLong(p[3]) / 1000.0;
        result.connectedCount = Integer.parseInt(p[4]);
        result.readyCount = Integer.parseInt(p[5]);
        if (!p[6].isEmpty()) {
            for (String item : p[6].split(";")) {
                String[] v = item.split(",", -1);
                if (v.length != 5) throw new IllegalArgumentException("Bad car: " + item);
                CarView c = new CarView(Integer.parseInt(v[0]), v[1], Integer.parseInt(v[2]));
                c.alive = "1".equals(v[3]);
                c.dodge = Integer.parseInt(v[4]);
                result.cars.add(c);
            }
        }
        if (!p[7].isEmpty()) {
            for (String item : p[7].split(";")) {
                String[] v = item.split(",", -1);
                if (v.length != 2) throw new IllegalArgumentException("Bad meteor: " + item);
                result.meteors.add(new MeteorView(Integer.parseInt(v[0]),
                        Double.parseDouble(v[1])));
            }
        }
        return result;
    }

    private void send(String command) {
        PrintWriter writer = output;
        if (writer != null) writer.println(command);
    }
    public void sendReady() { send("READY"); }
    public void sendMove(int direction) {
        if (direction == -1) send("LEFT");
        if (direction == 1) send("RIGHT");
    }
    public void close() {
        Socket s = socket;
        if (s != null) try { s.close(); } catch (IOException ignored) { }
    }
}
