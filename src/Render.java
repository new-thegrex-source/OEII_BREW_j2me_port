/* Sin table and view size. The span renderer is TinyGL.java, ported from oe2-re render.c / render_span.c / render_sprite.c. */
final class Render {
    static final int VIEW_X = 0;
    static final int VIEW_Y = 0;
    static final int VIEW_W = 240;
    static final int VIEW_H = 200;

    static int renderMode;
    static boolean isSkyMap;
    static int skyMapX;
    static int skyMapY;
    static final int[] sinTable = new int[1024];

    static {
        for (int i = 0; i < 1024; i++) {
            double a = i * 2.0 * 3.141592653589793 / 1024.0;
            sinTable[i] = (int) (Math.sin(a) * 65536.0);
        }
    }

    static void clearView(Framebuffer fb, int rgb565) {
        fb.fillRect(VIEW_X, VIEW_Y, VIEW_W, VIEW_H, rgb565);
    }

    static void drawSprite(Framebuffer fb, Sprite s, int x, int y) {
        fb.blit(s, x, y);
    }
}
