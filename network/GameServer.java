package network;

import ui.GB;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One authoritative game world for four human players. */
public class GameServer {
    private static final int PLAYERS = 4;
    private static final int TICK_MS = 33;
    private enum Phase { WAITING, PLAYING, GAMEOVER }

    private static class Car {
        final int id;
        int lane;
        int dodge;
        boolean alive = true;
        Car(int id, int lane) { this.id = id; this.lane = lane; }
    }

    private static class Meteor {
        int lane;
        double y;
        boolean counted;
        Meteor(int lane, double y) { this.lane = lane; this.y = y; }
    }

    private final Object lock = new Object();
    private final ClientHandler[] clients = new ClientHandler[PLAYERS];
    private final Car[] cars = new Car[PLAYERS];
    private final Meteor[] meteors = {
        new Meteor(0, 30), new Meteor(2, 70), new Meteor(4, 50)
    };
    private final Random random = new Random();
    private Phase phase = Phase.WAITING;
    private int tick;
    private long timeMs;

    public GameServer() {
        int[] startLanes = {0, 1, 3, 4};
        for (int i = 0; i < PLAYERS; i++) cars[i] = new Car(i + 1, startLanes[i]);
    }

    public void start(int port) throws IOException {
        ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
        clock.scheduleAtFixedRate(() -> {
            try {
                synchronized (lock) {
                    if (phase == Phase.PLAYING) updateGame();
                    broadcastState();
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }, 0, TICK_MS, TimeUnit.MILLISECONDS);

        try (ServerSocket listener = new ServerSocket(port)) {
            System.out.println("PIXEL METEOR SERVER listening on port " + port);
            while (true) {
                Socket socket = listener.accept();
                socket.setTcpNoDelay(true);
                synchronized (lock) {
                    int slot = freeSlot();
                    if (slot < 0 || phase == Phase.PLAYING) {
                        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                                socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                            out.println("ERROR|Room full or game already running");
                        }
                        socket.close();
                        continue;
                    }
                    ClientHandler handler = new ClientHandler(socket, slot);
                    clients[slot] = handler;
                    handler.send("WELCOME|" + (slot + 1));
                    handler.start();
                    System.out.println("P" + (slot + 1) + " connected");
                    broadcastState();
                }
            }
        } finally {
            clock.shutdownNow();
        }
    }

    private int freeSlot() {
        for (int i = 0; i < PLAYERS; i++) if (clients[i] == null) return i;
        return -1;
    }
    private int connectedCount() {
        int n = 0;
        for (ClientHandler c : clients) if (c != null) n++;
        return n;
    }
    private int readyCount() {
        int n = 0;
        for (ClientHandler c : clients) if (c != null && c.ready) n++;
        return n;
    }
    private void startRound() {
        int[] lanes = {0, 1, 3, 4};
        for (int i = 0; i < PLAYERS; i++) {
            cars[i].lane = lanes[i];
            cars[i].alive = true;
            cars[i].dodge = 0;
            clients[i].ready = false;
        }
        meteors[0] = new Meteor(0, 30);
        meteors[1] = new Meteor(2, 70);
        meteors[2] = new Meteor(4, 50);
        tick = 0;
        timeMs = 0;
        phase = Phase.PLAYING;
        System.out.println("Round started (4 players)");
    }

    private void updateGame() {
        tick++;
        timeMs += TICK_MS;
        double speed = Math.min(3.5, 1.2 * (1.0 + timeMs / 1000.0 / 20.0));
        for (Meteor m : meteors) {
            m.y += speed;
            for (Car c : cars) {
                if (c.alive && c.lane == m.lane && m.y >= GB.CAR_Y &&
                        m.y <= GB.CAR_Y + GB.CAR_H)
                    c.alive = false;
            }
            if (!m.counted && m.y > GB.CAR_Y + GB.CAR_H) {
                for (Car c : cars) if (c.alive) c.dodge++;
                m.counted = true;
            }
            if (m.y > GB.TRACK_BOT) {
                m.y = GB.TRACK_TOP;
                m.lane = random.nextInt(GB.LANES);
                m.counted = false;
            }
        }
        int alive = 0;
        for (Car c : cars) if (c.alive) alive++;
        if (alive <= 1) {
            phase = Phase.GAMEOVER;
            System.out.println("Game over, alive=" + alive);
        }
    }

    private String snapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("STATE|").append(phase).append('|').append(tick).append('|')
          .append(timeMs).append('|').append(connectedCount()).append('|')
          .append(readyCount()).append('|');
        for (int i = 0; i < PLAYERS; i++) {
            Car c = cars[i];
            if (i != 0) sb.append(';');
            sb.append(c.id).append(",P").append(c.id).append(',')
              .append(c.lane).append(',').append(c.alive ? 1 : 0).append(',').append(c.dodge);
        }
        sb.append('|');
        for (int i = 0; i < meteors.length; i++) {
            if (i != 0) sb.append(';');
            sb.append(meteors[i].lane).append(',').append(meteors[i].y);
        }
        return sb.toString();
    }

    private void broadcastState() {
        String packet = snapshot();
        for (ClientHandler c : clients) if (c != null) c.send(packet);
    }

    private class ClientHandler extends Thread {
        final Socket socket;
        final int slot;
        final PrintWriter output;
        boolean ready;

        ClientHandler(Socket socket, int slot) throws IOException {
            super("meteor-server-p" + (slot + 1));
            this.socket = socket;
            this.slot = slot;
            this.output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            setDaemon(true);
        }
        void send(String text) { output.println(text); }

        @Override public void run() {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8))) {
                String command;
                while ((command = input.readLine()) != null) {
                    synchronized (lock) {
                        if (clients[slot] != this) break;
                        if ("READY".equals(command) && phase != Phase.PLAYING) {
                            ready = true;
                            if (connectedCount() == PLAYERS && readyCount() == PLAYERS)
                                startRound();
                        } else if (phase == Phase.PLAYING && cars[slot].alive) {
                            if ("LEFT".equals(command))
                                cars[slot].lane = Math.max(0, cars[slot].lane - 1);
                            else if ("RIGHT".equals(command))
                                cars[slot].lane = Math.min(GB.LANES - 1, cars[slot].lane + 1);
                        }
                        broadcastState();
                    }
                }
            } catch (IOException ex) {
                System.out.println("P" + (slot + 1) + " disconnected: " + ex.getMessage());
            } finally {
                synchronized (lock) {
                    if (clients[slot] == this) {
                        clients[slot] = null;
                        ready = false;
                        if (phase == Phase.PLAYING) {
                            cars[slot].alive = false;
                            int n = 0;
                            for (Car c : cars) if (c.alive) n++;
                            if (n <= 1) phase = Phase.GAMEOVER;
                        }
                        broadcastState();
                    }
                }
                try { socket.close(); } catch (IOException ignored) { }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6000;
        new GameServer().start(port);
    }
}
