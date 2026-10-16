/* RGB565 framebuffer. Phone UI is 240x320, matching the handset frames. The 3D view stays 176 wide and is centered. */
final class Framebuffer {
    static final int W = 240;
    static final int H = 320;
    final short[] px = new short[W * H];
    int color = 0xffff;

    void clear(int rgb565) {
        short c = (short) rgb565;
        for (int i = 0; i < px.length; i++)
            px[i] = c;
    }

    void fillRect(int x, int y, int w, int h, int rgb565) {
        if (w <= 0 || h <= 0)
            return;
        int x0 = x < 0 ? 0 : x;
        int y0 = y < 0 ? 0 : y;
        int x1 = x + w > W ? W : x + w;
        int y1 = y + h > H ? H : y + h;
        short c = (short) rgb565;
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * W;
            for (int xx = x0; xx < x1; xx++)
                px[row + xx] = c;
        }
    }

    void blit(Sprite s, int dx, int dy) {
        if (s == null)
            return;
        for (int y = 0; y < s.h; y++) {
            int sy = dy + y;
            if (sy < 0 || sy >= H)
                continue;
            int row = sy * W;
            int srow = y * s.w;
            for (int x = 0; x < s.w; x++) {
                int sx = dx + x;
                if (sx < 0 || sx >= W)
                    continue;
                short c = s.px[srow + x];
                if (s.transparent && c == s.key)
                    continue;
                px[row + sx] = c;
            }
        }
    }

    static int rgb(int r, int g, int b) {
        return ((r & 0xf8) << 8) | ((g & 0xfc) << 3) | (b >> 3);
    }

    int[] toArgb() {
        int[] argb = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i] & 0xffff;
            int r = ((c >> 11) & 31) * 255 / 31;
            int g = ((c >> 5) & 63) * 255 / 63;
            int b = (c & 31) * 255 / 31;
            argb[i] = (0xff << 24) | (r << 16) | (g << 8) | b;
        }
        return argb;
    }
}
