/* Canvas_drawStringRange: glyph = c - 0x21, column glyph & 15, row glyph >> 4.
 * Small sheet Font.bmp is 9x12, advance 7. Large Font_LG.bmp is 13x17, advance 10. */
final class Text {
    static int[] small;
    static int smallW;
    static int smallH;
    static int[] large;
    static int largeW;
    static int largeH;

    static void load() {
        int[] s = sheet("Font.bmp", "3011.bmp");
        if (s != null) {
            small = s;
            smallW = sheetW;
            smallH = sheetH;
        }
        int[] g = sheet("Font_LG.bmp", "3012.bmp");
        if (g != null) {
            large = g;
            largeW = sheetW;
            largeH = sheetH;
        }
    }

    static int sheetW;
    static int sheetH;

    static int[] sheet(String a, String b) {
        byte[] data = MenuSystem.read(a);
        if (data == null)
            data = MenuSystem.read(b);
        if (data == null)
            return null;
        Sprite sp = Sprite.fromBmp(data, true);
        if (sp == null)
            return null;
        sheetW = sp.w;
        sheetH = sp.h;
        int[] argb = new int[sp.w * sp.h];
        for (int i = 0; i < argb.length; i++) {
            int p = sp.px[i] & 0xffff;
            int r = ((p >> 11) & 31) << 3;
            int g = ((p >> 5) & 63) << 2;
            int blue = (p & 31) << 3;
            if (r > 240 && g < 16 && blue > 240)
                argb[i] = 0;
            else
                argb[i] = 0xff000000 | (r << 16) | (g << 8) | blue;
        }
        return argb;
    }

    static void draw(Framebuffer fb, String s, int x, int y, boolean big) {
        if (s == null)
            return;
        int[] sheet = big ? large : small;
        int sw = big ? largeW : smallW;
        int sh = big ? largeH : smallH;
        int cellW = big ? 13 : 9;
        int cellH = big ? 17 : 12;
        int advance = big ? 10 : 7;
        if (sheet == null) {
            fallback(fb, s, x, y);
            return;
        }
        int pen = x;
        for (int i = 0; i < s.length(); i++) {
            int c = s.charAt(i);
            if (c == ' ') {
                pen += advance;
                continue;
            }
            int glyph = c - 0x21;
            if (glyph < 0)
                continue;
            blit(fb, sheet, sw, sh, cellW * (glyph & 15), cellH * (glyph >> 4), cellW, cellH, pen, y);
            pen += advance;
        }
    }

    static int width(String s, boolean big) {
        if (s == null)
            return 0;
        return s.length() * (big ? 10 : 7);
    }

    static void blit(Framebuffer fb, int[] sheet, int sw, int sh, int sx, int sy, int w, int h, int dx, int dy) {
        for (int y = 0; y < h; y++) {
            int iy = sy + y;
            int oy = dy + y;
            if (iy < 0 || iy >= sh || oy < 0 || oy >= Framebuffer.H)
                continue;
            for (int x = 0; x < w; x++) {
                int ix = sx + x;
                int ox = dx + x;
                if (ix < 0 || ix >= sw || ox < 0 || ox >= Framebuffer.W)
                    continue;
                int p = sheet[iy * sw + ix];
                if ((p >>> 24) != 0)
                    fb.px[oy * Framebuffer.W + ox] = (short) Framebuffer.rgb((p >> 16) & 255, (p >> 8) & 255, p & 255);
            }
        }
    }

    static void fallback(Framebuffer fb, String s, int x, int y) {
        int pen = x;
        for (int i = 0; i < s.length(); i++) {
            int c = s.charAt(i);
            if (c != ' ')
                fb.fillRect(pen, y, 5, 7, Framebuffer.rgb(255, 255, 255));
            pen += 7;
        }
    }
}
