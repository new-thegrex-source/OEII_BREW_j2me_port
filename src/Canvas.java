import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;

/* Wolf's Canvas.java is the phone surface. Same job: keys in, 40 ms tick, blit out. */
public class Canvas extends GameCanvas implements Runnable {
    static Canvas canvas;
    static int repaintFlags;
    private volatile boolean running;
    private Thread thread;
    private final Framebuffer fb = new Framebuffer();
    private Image frame;
    private int held;
    private int repeatAt;

    public Canvas() {
        super(false);
        canvas = this;
        setFullScreenMode(true);
    }

    void start() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    void stop() {
        running = false;
    }

    public void run() {
        Game.boot();
        long next = System.currentTimeMillis();
        while (running) {
            long now = System.currentTimeMillis();
            if (now >= next) {
                if (Game.state == Game.ST_PLAY)
                pollKeys();
                Game.tick40();
                Game.draw(fb);
                present();
                next += 40;
                if (next < now)
                    next = now + 40;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void pollKeys() {
        int keys = getKeyStates();
        int avk = 0;
        if ((keys & UP_PRESSED) != 0) avk = Keys.AVK_UP;
        else if ((keys & DOWN_PRESSED) != 0) avk = Keys.AVK_DOWN;
        else if ((keys & LEFT_PRESSED) != 0) avk = Keys.AVK_LEFT;
        else if ((keys & RIGHT_PRESSED) != 0) avk = Keys.AVK_RIGHT;
        else if ((keys & FIRE_PRESSED) != 0) avk = Keys.AVK_SELECT;
        if (avk == 0) {
            held = 0;
            return;
        }
        int now = (int) System.currentTimeMillis();
        if (avk != held) {
            held = avk;
            repeatAt = now + 1500;
            Game.pushKey(avk);
        } else if (now >= repeatAt) {
            repeatAt = now + 100;
            Game.pushKey(avk);
        }
    }

    protected void keyPressed(int key) {
        Game.pushKey(mapKey(key));
    }

    protected void keyRepeated(int key) {
        Game.pushKey(mapKey(key));
    }

    private int mapKey(int key) {
        int avk = Keys.fromMidp(key);
        if (avk != 0)
            return avk;
        int action = 0;
        try {
            action = getGameAction(key);
        } catch (Exception ignored) {
        }
        if (key == 51 || key == 3) return Keys.AVK_3;
        if (key == 49 || key == 1) return Keys.AVK_1;
        if (action == UP || key == -1 || key == 50 || key == 2) return Keys.AVK_UP;
        if (action == DOWN || key == -2 || key == 56 || key == 8) return Keys.AVK_DOWN;
        if (action == LEFT || key == -3 || key == 52 || key == 4) return Keys.AVK_LEFT;
        if (action == RIGHT || key == -4 || key == 54 || key == 6) return Keys.AVK_RIGHT;
        if (action == FIRE || key == -5 || key == 53 || key == 5 || key == 10) return Keys.AVK_SELECT;
        if (key == -6 || key == -21) return Keys.AVK_SOFT1;
        if (key == -7 || key == -22) return Keys.AVK_SOFT2;
        return 0;
    }

    private void present() {
        int[] argb = fb.toArgb();
        if (frame == null)
            frame = Image.createRGBImage(argb, Framebuffer.W, Framebuffer.H, false);
        else
            frame = Image.createRGBImage(argb, Framebuffer.W, Framebuffer.H, false);
        Graphics g = getGraphics();
        g.setColor(0);
        g.fillRect(0, 0, getWidth(), getHeight());
        int dw = getWidth();
        int dh = getHeight();
        int scale = 1;
        while ((scale + 1) * Framebuffer.W <= dw && (scale + 1) * Framebuffer.H <= dh)
            scale++;
        if (scale < 1)
            scale = 1;
        int ox = (dw - Framebuffer.W * scale) / 2;
        int oy = (dh - Framebuffer.H * scale) / 2;
        if (scale == 1) {
            g.drawImage(frame, ox, oy, Graphics.TOP | Graphics.LEFT);
        } else {
            Image big = scale(argb, scale);
            g.drawImage(big, ox, oy, Graphics.TOP | Graphics.LEFT);
        }
        paintFlow(g, ox, oy, scale);
        flushGraphics();
    }

    private void paintFlow(Graphics g, int ox, int oy, int scale) {
        Image pic = null;
        if (Game.state == Game.ST_LOGO) {
            int which = Game.logoFrame / 38;
            if (which > 2)
                which = 2;
            pic = MenuSystem.logos[which];
        } else if (Game.state == Game.ST_MENU || Game.state == Game.ST_SOUND) {
            pic = MenuSystem.menuBg;
        }
        if (pic != null && Game.state == Game.ST_LOGO)
            g.drawImage(pic, ox + Framebuffer.W * scale / 2, oy + 8, Graphics.TOP | Graphics.HCENTER);
        if (Game.state == Game.ST_NEED_DATA) {
            g.setColor(0xff6666);
            g.drawString(Game.error == null ? "missing orcsandelves2.mod" : Game.error, ox + 4, oy + 8, Graphics.TOP | Graphics.LEFT);
        } else if (Game.state == Game.ST_LOGO) {
            g.drawString("press fire", ox + 8, oy + Framebuffer.H * scale - 16, Graphics.TOP | Graphics.LEFT);
        }
    }

    private Image scale(int[] argb, int scale) {
        int w = Framebuffer.W * scale;
        int h = Framebuffer.H * scale;
        int[] out = new int[w * h];
        for (int y = 0; y < Framebuffer.H; y++) {
            for (int x = 0; x < Framebuffer.W; x++) {
                int c = argb[y * Framebuffer.W + x];
                for (int dy = 0; dy < scale; dy++) {
                    int row = (y * scale + dy) * w + x * scale;
                    for (int dx = 0; dx < scale; dx++)
                        out[row + dx] = c;
                }
            }
        }
        return Image.createRGBImage(out, w, h, false);
    }

    /* Menu 3 from menu_build.c: scrolling map 0x22, logo 0x25, five items. */
    private void paintMainMenu(Graphics g, int ox, int oy, int scale) {
        int sw = Framebuffer.W * scale;
        int sh = Framebuffer.H * scale;
        if (MenuSystem.map != null) {
            int mw = MenuSystem.map.getWidth();
            int mh = MenuSystem.map.getHeight();
            int sx = 0;
            int sy = 0;
            if (mw > Framebuffer.W)
                sx = (MenuSystem.scroll / 2) % (mw - Framebuffer.W);
            if (mh > Framebuffer.H)
                sy = (MenuSystem.scroll / 3) % (mh - Framebuffer.H);
            g.drawRegion(MenuSystem.map, sx, sy, Math.min(Framebuffer.W, mw), Math.min(Framebuffer.H, mh),
                    0, ox, oy, Graphics.TOP | Graphics.LEFT);
        }
        if (MenuSystem.logo != null) {
            int lw = MenuSystem.logo.getWidth();
            g.drawImage(MenuSystem.logo, ox + (sw - lw) / 2, oy + 4 * scale, Graphics.TOP | Graphics.LEFT);
        }
        int y = oy + 68 * scale;
        for (int i = 0; i < MenuSystem.ITEMS.length; i++) {
            boolean on = i == MenuSystem.selected;
            g.setColor(on ? 0xffffff : 0xdddddd);
            g.drawString(MenuSystem.ITEMS[i], ox + sw / 2, y, Graphics.TOP | Graphics.HCENTER);
            if (on && MenuSystem.cursor != null)
                g.drawImage(MenuSystem.cursor, ox + sw / 2 - 52 * scale, y, Graphics.TOP | Graphics.LEFT);
            else if (on) {
                g.fillTriangle(ox + sw / 2 - 46 * scale, y + 2 * scale,
                        ox + sw / 2 - 38 * scale, y + 6 * scale,
                        ox + sw / 2 - 46 * scale, y + 10 * scale);
            }
            y += 14 * scale;
        }
        g.setColor(0x181818);
        g.fillRect(ox, oy + sh - 18 * scale, sw, 18 * scale);
        g.setColor(0xffffff);
        g.drawString("Exit", ox + 4 * scale, oy + sh - 16 * scale, Graphics.TOP | Graphics.LEFT);
        g.drawString("Select", ox + sw - 4 * scale, oy + sh - 16 * scale, Graphics.TOP | Graphics.RIGHT);
    }
}
