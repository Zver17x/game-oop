package ui;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Loads each PNG only once; missing assets fall back gracefully. */
public final class GameAssets {
    private final Map<String, BufferedImage> images = new HashMap<>();

    public GameAssets() {
        String[] names = {
            "background.png", "meteor.png", "meteors/meteor_alt.png",
            "cars/car_p1.png", "cars/car_p2.png", "cars/car_p3.png", "cars/car_p4.png",
            "road/road.png", "road/road_tile.png", "road/lane_mark.png", "road/roadside.png",
            "effects/explosion.png", "effects/explosion_smoke.png", "effects/debris.png",
            "effects/boost.png", "effects/dust.png",
            "ui/logo.png", "ui/panel.png", "ui/hud_panel.png", "ui/lobby_panel.png",
            "ui/ranking_panel.png", "ui/gameover_row.png",
            "ui/player_card_p1.png", "ui/player_card_p2.png", "ui/player_card_p3.png",
            "ui/player_card_p4.png", "ui/status_online.png", "ui/status_offline.png",
            "ui/btn_ready.png", "ui/btn_waiting.png", "ui/btn_restart.png"
        };
        for (String name : names) {
            BufferedImage image = load(name);
            if (image != null) images.put(name, image);
            else System.err.println("Missing game asset: assets/" + name);
        }
    }

    private BufferedImage load(String name) {
        File f = new File("assets", name);
        try {
            if (f.isFile()) return ImageIO.read(f);
            try (InputStream in = GameAssets.class.getResourceAsStream("/assets/" + name)) {
                if (in != null) return ImageIO.read(in);
            }
        } catch (IOException ex) {
            System.err.println("Couldn't open PNG: " + name + ": " + ex.getMessage());
        }
        return null;
    }

    public BufferedImage get(String name) { return images.get(name); }
    public int loadedCount() { return images.size(); }
    public Map<String, BufferedImage> all() { return Collections.unmodifiableMap(images); }

    public boolean draw(Graphics2D g, String name, int x, int y, int w, int h) {
        BufferedImage image = images.get(name);
        if (image == null) return false;
        g.drawImage(image, x, y, w, h, null);
        return true;
    }
}
