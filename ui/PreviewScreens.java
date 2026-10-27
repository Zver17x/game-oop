package ui;

import model.CarView;
import model.MeteorView;
import model.ViewState;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Optional utility: java -Djava.awt.headless=true -cp out ui.PreviewScreens */
public class PreviewScreens {
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "preview";
        File dir = new File(out);
        dir.mkdirs();
        for (int mode : new int[]{ViewState.ST_START, ViewState.ST_PLAY,
                ViewState.ST_GAMEOVER}) {
            ViewState s = new ViewState();
            s.status = mode;
            s.myId = 1;
            s.connectedCount = 4;
            s.readyCount = 2;
            s.tick = 116;
            s.time = 84.0;
            int[] lanes = {0, 1, 3, 4};
            for (int i = 0; i < 4; i++) {
                CarView c = new CarView(i + 1, "P" + (i + 1), lanes[i]);
                c.dodge = new int[]{20, 19, 16, 18}[i];
                if (mode == ViewState.ST_GAMEOVER && i != 2) c.alive = false;
                s.cars.add(c);
            }
            s.meteors.add(new MeteorView(1, 49));
            s.meteors.add(new MeteorView(3, 67));
            s.meteors.add(new MeteorView(0, 33));
            GamePanel panel = new GamePanel(s);
            panel.setSize(900, 900);
            BufferedImage image = new BufferedImage(900, 900,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
            String name = mode == ViewState.ST_START ? "lobby" :
                    mode == ViewState.ST_PLAY ? "gameplay" : "ranking";
            ImageIO.write(image, "png", new File(dir, name + ".png"));
            System.out.println("Saved " + name + ".png");
        }
    }
}
