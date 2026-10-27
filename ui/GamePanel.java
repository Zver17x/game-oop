package ui;

import model.CarView;
import model.MeteorView;
import model.ViewState;
import network.GameClient;

import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Graphics-only upgrade: the TCP client still receives state from GameServer.
 * No meteors, car movement, scores or collisions are simulated here.
 */
public class GamePanel extends RetroPanel {

    private static final Color WHITE = new Color(0xECF4FF);
    private static final Color MUTED = new Color(0x9FB1CC);
    private static final Color CYAN = new Color(0x67DCE8);
    private static final Color GREEN = new Color(0x9BEC8B);
    private static final Color PINK = new Color(0xFB7BAD);
    private static final Color DARK = new Color(0x101827);
    private static final Color[] COLORS = {
        CYAN, new Color(0xFFC45F), PINK, GREEN
    };

    private static final int TRACK_X = 120;
    private static final int TRACK_Y = 230;
    private static final int TRACK_W = 660;
    private static final int TRACK_H = 574;

    private final GameAssets assets = new GameAssets();
    private final Preferences preferences = Preferences.userNodeForPackage(GamePanel.class);
    private final GameClient client;

    private ViewState state = new ViewState();
    private boolean readySent = false;
    private int highScore = preferences.getInt("HIGH_SCORE", 0);
    private String disconnectReason = "Connection closed";

    // Visual effects only. The server never receives these values.
    private final Map<Integer, HitEffect> hits = new HashMap<>();
    private final Map<Integer, HitEffect> laneMoves = new HashMap<>();

    private static final class HitEffect {
        final int lane;
        final long started;
        HitEffect(int lane) {
            this.lane = lane;
            this.started = System.nanoTime();
        }
        double seconds() { return (System.nanoTime() - started) / 1_000_000_000.0; }
    }

    public GamePanel(String host, int port) {
        this.client = new GameClient(host, port, new GameClient.Listener() {
            @Override public void onWelcome(int playerId) {
                state.myId = playerId;
                repaint();
            }

            @Override public void onState(ViewState next) {
                acceptState(next);
            }

            @Override public void onDisconnected(String reason) {
                disconnectReason = reason == null ? "Connection closed" : reason;
                state.status = ViewState.ST_DISCONNECTED;
                repaint();
            }
        });
        configureKeyboard();
        new Timer(33, e -> repaint()).start(); // paint only, not game logic
        client.connectAsync();
    }

    /** Offline preview hook for development; does not open a socket. */
    GamePanel(ViewState snapshot) {
        this.client = null;
        state = snapshot;
        configureKeyboard();
    }

    private void configureKeyboard() {
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (client == null) return;
                int key = e.getKeyCode();
                if (state.status == ViewState.ST_START &&
                    key == KeyEvent.VK_ENTER && !readySent) {
                    readySent = true;
                    client.sendReady();
                } else if (state.status == ViewState.ST_PLAY) {
                    CarView me = state.find(state.myId);
                    if (me != null && me.alive) {
                        if (key == KeyEvent.VK_LEFT) client.sendMove(-1);
                        if (key == KeyEvent.VK_RIGHT) client.sendMove(1);
                    }
                } else if (state.status == ViewState.ST_GAMEOVER &&
                           key == KeyEvent.VK_R && !readySent) {
                    readySent = true;
                    client.sendReady();
                }
                repaint();
            }
        });
    }

    private void acceptState(ViewState next) {
        ViewState old = state;
        if (next.status == ViewState.ST_PLAY) {
            readySent = false;
            if (old.status == ViewState.ST_PLAY) {
                for (CarView car : next.cars) {
                    CarView previous = old.find(car.id);
                    if (previous != null && previous.alive && !car.alive)
                        hits.put(car.id, new HitEffect(car.lane));
                    if (previous != null && previous.alive && car.alive &&
                            previous.lane != car.lane)
                        laneMoves.put(car.id, new HitEffect(previous.lane));
                }
            } else {
                hits.clear();
                laneMoves.clear();
            }
        } else if (next.status == ViewState.ST_GAMEOVER &&
                   old.status != ViewState.ST_GAMEOVER) {
            readySent = false;
            CarView me = next.find(next.myId);
            if (me != null && me.dodge > highScore) {
                highScore = me.dodge;
                preferences.putInt("HIGH_SCORE", highScore);
            }
        }
        state = next;
        repaint();
    }

    @Override protected void drawPixels(Graphics2D g) {
        drawBackdrop(g);
        drawHeader(g);
        switch (state.status) {
            case ViewState.ST_PLAY:
                drawTopHud(g);
                drawTrack(g, true);
                drawMeteorSprites(g);
                drawCarSprites(g);
                drawHitAnimations(g);
                drawPlayerCards(g);
                CarView me = state.find(state.myId);
                if (me != null && !me.alive) drawSpectating(g);
                break;
            case ViewState.ST_START:
                drawLobby(g);
                break;
            case ViewState.ST_GAMEOVER:
                drawRanking(g);
                break;
            case ViewState.ST_CONNECTING:
                drawMessage(g, "CONNECTING TO SERVER", "Check Server IP / port");
                break;
            default:
                drawMessage(g, "DISCONNECTED", disconnectReason);
        }
    }

    private void drawBackdrop(Graphics2D g) {
        if (!assets.draw(g, "background.png", 0, 0, UI_W, UI_H)) {
            g.setColor(new Color(0x0B1120));
            g.fillRect(0, 0, UI_W, UI_H);
        }
        g.setColor(new Color(8, 15, 31, 185));
        g.fillRoundRect(28, 16, 844, 872, 30, 30);
    }

    private void drawHeader(Graphics2D g) {
        if (!assets.draw(g, "ui/logo.png", 58, 32, 405, 83)) {
            text(g, "PIXEL METEOR", 70, 66, 25, CYAN, true);
        }
        String badge = (state.status == ViewState.ST_CONNECTING ||
                        state.status == ViewState.ST_DISCONNECTED)
                       ? "ui/status_offline.png" : "ui/status_online.png";
        assets.draw(g, badge, 695, 48, 142, 44);
    }

    private void drawTopHud(Graphics2D g) {
        assets.draw(g, "ui/hud_panel.png", 66, 125, 768, 95);
        text(g, "TIME", 92, 151, 14, MUTED, true);
        text(g, "ALIVE", 375, 151, 14, MUTED, true);
        text(g, "DODGED", 673, 151, 14, MUTED, true);
        text(g, fmtTime(state.time), 92, 194, 29, WHITE, true);
        text(g, state.aliveCount() + " / " + state.cars.size(), 375, 194, 29, WHITE, true);
        CarView mine = state.find(state.myId);
        text(g, mine == null ? "000" : String.format(Locale.US,"%03d", mine.dodge),
                674, 194, 29, WHITE, true);
    }

    private void drawTrack(Graphics2D g, boolean scroll) {
        Graphics2D roadG = (Graphics2D) g.create();
        try {
            roadG.setClip(new Rectangle(TRACK_X, TRACK_Y, TRACK_W, TRACK_H));
            assets.draw(roadG, "road/road.png", TRACK_X, TRACK_Y, TRACK_W, TRACK_H);
            BufferedImage tile = assets.get("road/road_tile.png");
            if (tile != null) {
                int tileH = Math.max(1, (int) Math.round(tile.getHeight() *
                        TRACK_W / (double) tile.getWidth()));
                int scrollOffset = scroll ? (state.tick * 3) % tileH : 0;
                for (int y = TRACK_Y + scrollOffset - tileH;
                         y < TRACK_Y + TRACK_H; y += tileH)
                    roadG.drawImage(tile, TRACK_X, y, TRACK_W, tileH, null);
            }
            // Subtle line-mark and curb art from the asset set.
            int off = scroll ? (state.tick * 3) % 115 : 0;
            for (int y = TRACK_Y + off - 115; y < TRACK_Y + TRACK_H; y += 115) {
                for (int lane = 1; lane < GB.LANES; lane++) {
                    int x = TRACK_X + lane * TRACK_W / GB.LANES;
                    Graphics2D markG = (Graphics2D) roadG.create();
                    markG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
                    assets.draw(markG, "road/lane_mark.png", x - 5, y, 10, 76);
                    markG.dispose();
                }
                assets.draw(roadG, "road/roadside.png", TRACK_X - 6, y, 15, 112);
                assets.draw(roadG, "road/roadside.png", TRACK_X + TRACK_W - 9, y, 15, 112);
            }
        } finally {
            roadG.dispose();
        }
    }

    private int laneCenter(int lane) {
        return TRACK_X + (int)Math.round((lane + 0.5) * TRACK_W / GB.LANES);
    }
    private int worldY(double y) {
        return TRACK_Y + (int) Math.round((y - GB.TRACK_TOP)
                   * TRACK_H / (double)(GB.TRACK_BOT - GB.TRACK_TOP));
    }
    private int carTop() { return worldY(GB.CAR_Y); }

    private void drawMeteorSprites(Graphics2D g) {
        Graphics2D sprites = (Graphics2D) g.create();
        try {
            sprites.clipRect(TRACK_X, TRACK_Y, TRACK_W, TRACK_H);
            for (int i = 0; i < state.meteors.size(); i++) {
                MeteorView meteor = state.meteors.get(i);
                int x = laneCenter(meteor.lane);
                int y = worldY(meteor.y);
                Graphics2D one = (Graphics2D)sprites.create();
                try {
                    one.translate(x, y);
                    one.rotate(Math.sin((state.tick + i * 37) / 25.0) * 0.09);
                    String file = i % 3 == 2 ? "meteors/meteor_alt.png" : "meteor.png";
                    if (!assets.draw(one, file, -28, -26, 56, 52)) {
                        one.setColor(new Color(0x548DA2));
                        one.fillOval(-26, -26, 52, 52);
                    }
                } finally {
                    one.dispose();
                }
            }
        } finally {
            sprites.dispose();
        }
    }

    private void drawCarSprites(Graphics2D g) {
        Graphics2D carsG = (Graphics2D)g.create();
        try {
            carsG.clipRect(TRACK_X, TRACK_Y, TRACK_W, TRACK_H);
            for (CarView c : state.cars) {
                if (!c.alive) continue;
                int x = laneCenter(c.lane);
                int y = carTop();
                HitEffect movement = laneMoves.get(c.id);
                if (movement != null && movement.seconds() < .24) {
                    drawFade(carsG, "effects/dust.png", x - 40, y + 47, 80, 80,
                            (float)Math.max(0, .48 - movement.seconds() * 1.5));
                    drawFade(carsG, "effects/boost.png", x - 28, y + 67, 56, 88,
                            (float)Math.max(0, .60 - movement.seconds() * 2));
                }
                String asset = "cars/car_p" + c.id + ".png";
                if (!assets.draw(carsG, asset, x - 31, y - 6, 62, 108)) {
                    carsG.setColor(COLORS[Math.floorMod(c.id - 1, 4)]);
                    carsG.fillRoundRect(x - 23, y, 46, 92, 8, 8);
                }
                center(carsG, "P" + c.id, x, y - 15, 19,
                        COLORS[Math.floorMod(c.id - 1, 4)], true);
            }
        } finally {
            carsG.dispose();
        }
    }

    private void drawHitAnimations(Graphics2D g) {
        Graphics2D fxG = (Graphics2D) g.create();
        try {
            fxG.clipRect(TRACK_X, TRACK_Y, TRACK_W, TRACK_H);
            hits.entrySet().removeIf(e -> e.getValue().seconds() > 1.20);
            laneMoves.entrySet().removeIf(e -> e.getValue().seconds() > .5);
            for (HitEffect fx : hits.values()) {
                int x = laneCenter(fx.lane);
                int y = carTop() + 45;
                double age = fx.seconds();
                if (age < .45) {
                    float alpha = (float)Math.max(0, 1 - age / .45);
                    drawFade(fxG,"effects/explosion.png", x - 69,y - 78,138,146,alpha);
                    drawFade(fxG,"effects/debris.png",x - 80,y - 80,160,170,alpha * .9f);
                }
                if (age > .18) {
                    float smoke = (float)Math.max(0, 1 - (age - .18) / 1.05);
                    drawFade(fxG,"effects/explosion_smoke.png",
                            x - 84,y - 90-(int)(age*32),168,175,smoke * .85f);
                }
            }
        } finally {
            fxG.dispose();
        }
    }

    private void drawFade(Graphics2D g, String image, int x, int y,
                          int w, int h, float alpha) {
        if (alpha <= 0) return;
        Graphics2D layer = (Graphics2D)g.create();
        layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.min(1.0f, alpha)));
        assets.draw(layer, image, x, y, w, h);
        layer.dispose();
    }

    private void drawPlayerCards(Graphics2D g) {
        for (int i = 0; i < 4; i++) {
            int x = 66 + i * 194;
            int y = 814;
            assets.draw(g, "ui/player_card_p" + (i + 1) + ".png", x, y, 181, 75);
            CarView c = state.find(i + 1);
            if (c == null || !c.alive) {
                // Replace the ALIVE label baked into the PNG when eliminated.
                g.setColor(new Color(0x1B263B));
                g.fillRect(x + 40, y + 48, 100, 22);
                text(g, "OUT", x + 41, y + 64, 15, PINK, true);
            }
            if (state.myId == i + 1) {
                g.setColor(COLORS[i]);
                g.drawRoundRect(x + 1, y + 1, 178, 72, 12, 12);
            }
        }
    }

    private void drawSpectating(Graphics2D g) {
        assets.draw(g, "ui/panel.png", 275, 349, 350, 120);
        center(g, "YOU ARE OUT", 450, 399, 27, PINK, true);
        center(g, "SPECTATING THE MATCH", 450, 431, 17, WHITE, false);
    }

    private void drawLobby(Graphics2D g) {
        assets.draw(g, "ui/lobby_panel.png", 100, 119, 700, 650);
        center(g, "MULTIPLAYER LOBBY", 450, 209, 31, WHITE, true);
        center(g, "4 PLAYERS  /  1 SHARED SERVER", 450, 240, 16, MUTED, false);
        int id = Math.max(1, state.myId);
        center(g, "YOU ARE P" + id, 450, 321, 37, COLORS[(id - 1) % 4], true);
        center(g, "CONNECTED    " + state.connectedCount + " / 4",
                450, 385, 25, WHITE, true);
        center(g, "READY    " + state.readyCount + " / 4",
                450, 425, 25, GREEN, true);
        for (int i = 0; i < 4; i++) {
            int cx = 230 + i * 145;
            g.setColor(new Color(0x0F1727));
            g.fillRoundRect(cx - 60, 472, 120, 87, 13, 13);
            center(g, "P" + (i + 1), cx, 517, 28, COLORS[i], true);
            center(g, i < state.connectedCount ? "CONNECTED" : "WAITING", cx,
                    545, 12, i < state.connectedCount ? GREEN : MUTED, false);
        }
        assets.draw(g, readySent ? "ui/btn_waiting.png" : "ui/btn_ready.png",
                295, 610, 310, 72);
        center(g, "LOCAL HIGH SCORE   " + highScore, 450, 727, 17, MUTED, true);
    }

    private void drawRanking(Graphics2D g) {
        assets.draw(g, "ui/ranking_panel.png", 100, 119, 700, 650);
        center(g, "RANKING BOARD", 450, 209, 37, WHITE, true);
        center(g, "MATCH COMPLETED", 450, 238, 17, MUTED, false);
        List<CarView> ranking = new ArrayList<>(state.cars);
        ranking.sort(Comparator.comparing((CarView c) -> !c.alive)
                .thenComparing((CarView c) -> -c.dodge)
                .thenComparingInt(c -> c.id));
        for (int i = 0; i < ranking.size(); i++) {
            CarView c = ranking.get(i);
            int y = 271 + i * 84;
            assets.draw(g, "ui/gameover_row.png", 168, y, 565, 72);
            if (c.id == state.myId) {
                g.setColor(CYAN);
                g.drawRoundRect(169,y + 1,563,70,14,14);
            }
            text(g, "#" + (i + 1), 192, y + 46, 28, MUTED, true);
            text(g, "P" + c.id, 295, y + 46, 27, COLORS[Math.floorMod(c.id - 1, 4)], true);
            text(g, "DG " + c.dodge, 449, y + 46, 23, WHITE, true);
            text(g, c.alive ? "WIN" : "OUT", 628, y + 46, 21,
                    c.alive ? GREEN : PINK, true);
        }
        center(g, "TIME " + fmtTime(state.time) + "   |   HIGH SCORE " + highScore,
                450, 649, 17, MUTED, true);
        assets.draw(g, readySent ? "ui/btn_waiting.png" : "ui/btn_restart.png",
                295, 668, 310, 72);
    }

    private void drawMessage(Graphics2D g, String title, String message) {
        assets.draw(g, "ui/panel.png", 146, 248, 608, 402);
        center(g, title, 450, 407, 32, WHITE, true);
        if (message.length() > 50) message = message.substring(0, 50) + "...";
        center(g, message, 450, 463, 16, MUTED, false);
    }

    private static String fmtTime(double seconds) {
        int s = (int) Math.max(0, seconds);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }
    private static void text(Graphics2D g, String value, int x, int baseline,
                             int size, Color c, boolean bold) {
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        g.setColor(c);
        g.drawString(value, x, baseline);
    }
    private static void center(Graphics2D g, String value, int x, int baseline,
                               int size, Color color, boolean bold) {
        Font f = new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size);
        g.setFont(f);
        FontMetrics m = g.getFontMetrics(f);
        g.setColor(color);
        g.drawString(value, x - m.stringWidth(value) / 2, baseline);
    }
}
