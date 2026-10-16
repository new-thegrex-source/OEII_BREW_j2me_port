/* Render_loadPalettes. A short count, then each palette is a short length
 * and that many RGB565 words. Mapping palette n starts at n << 8. */
final class Palettes {
    static int count;
    static final int MAX = 512;
    static short[] rgb = new short[MAX * 256];
    static int[] len = new int[MAX];
    static String error = "";

    static boolean load() {
        count = 0;
        error = "";
        byte[] b = Resource.open(1003);
        if (b == null) {
            try {
                b = Resource.readJar("/palettes.bin");
            } catch (Exception ignored) {
            }
        }
        if (b == null || b.length < 4) {
            error = "no palettes.bin";
            return false;
        }
        int n = (b[0] & 0xff) | ((b[1] & 0xff) << 8);
        if (n <= 0 || n > MAX) {
            error = "palettes " + n;
            return false;
        }
        int p = 2;
        for (int pal = 0; pal < n; pal++) {
            if (p + 2 > b.length)
                break;
            int colors = (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8);
            p += 2;
            int base = pal << 8;
            int got = 0;
            for (int i = 0; i < colors && i < 256; i++) {
                if (p + 2 > b.length || base + i >= rgb.length)
                    break;
                rgb[base + i] = (short) ((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8));
                p += 2;
                got++;
            }
            len[pal] = got;
            count++;
        }
        return count > 0;
    }

    static void synthesize() {
        count = 1;
        for (int i = 0; i < 256; i++) {
            int r = (i * 17) & 255;
            int g = (80 + i * 9) & 255;
            int b = (40 + i * 5) & 255;
            rgb[i] = (short) Framebuffer.rgb(r, g, b);
        }
    }

    /* Phone stores each palette 256 entries apart and indexes it directly.
     * Wrapping with % len remaps a real texel onto another colour, which is
     * why sheets looked like the right art with the wrong palette. */
    static short color(int paletteNumber, int index) {
        if (count == 0)
            return 0;
        int pal = slot(paletteNumber);
        return rgb[(pal << 8) + (index & 255)];
    }

    /* mappings.bin stores a palette index. The phone shifts it << 8. */
    static int slot(int pal) {
        if (count <= 0)
            return 0;
        if (pal < 0)
            pal = 0;
        if (pal < count)
            return pal;
        int shifted = pal >> 8;
        if (shifted >= 0 && shifted < count)
            return shifted;
        return 0;
    }
}
