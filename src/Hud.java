final class Hud {
    static String line = "";

    static void draw(Framebuffer fb) {
        fb.fillRect(0, Framebuffer.H - 44, Framebuffer.W, 44, Framebuffer.rgb(20, 16, 12));
        Text.draw(fb, line, 2, 182, false);
        Text.draw(fb, "L soft  R menu", 2, 196, false);
    }
}
