package ui;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Color;

/** Scales the modern UI independently of the server's 160x144 world coordinates. */
public abstract class RetroPanel extends JPanel {
    protected static final int UI_W = 900;
    protected static final int UI_H = 900;

    protected RetroPanel() {
        setPreferredSize(new Dimension(765, 765));
        setBackground(new Color(0x0B1120));
        setDoubleBuffered(true);
        setFocusable(true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min(getWidth() / (double) UI_W,
                                    getHeight() / (double) UI_H);
            g.translate((getWidth() - UI_W * scale) / 2.0,
                        (getHeight() - UI_H * scale) / 2.0);
            g.scale(scale, scale);
            drawPixels(g);
        } finally {
            g.dispose();
        }
    }

    protected abstract void drawPixels(Graphics2D g);
}
