/* 4/8-bit BMP from resource 3000+index. Magenta is the transparent key. */
final class Sprite {
    short[] px;
    int w, h;
    boolean transparent;
    short key;

    static Sprite fromBmp(byte[] bmp, boolean transparent) {
        if (bmp == null || bmp.length < 54 || bmp[0] != 'B' || bmp[1] != 'M')
            return null;
        int pixelOfs = le32(bmp, 10);
        int heightSigned = le32(bmp, 0x16);
        boolean topDown = heightSigned < 0;
        int width = le32(bmp, 0x12);
        int height = heightSigned < 0 ? -heightSigned : heightSigned;
        int bpp = le16(bmp, 0x1c);
        int numColors = le32(bmp, 0x2e);
        if (width <= 0 || height <= 0 || width > 1024 || height > 1024)
            return null;
        if (bpp != 4 && bpp != 8 && bpp != 24)
            return null;
        if (bpp != 24 && numColors == 0)
            numColors = 1 << bpp;
        if (numColors > 256)
            numColors = 256;
        short[] pal = new short[256];
        for (int i = 0; i < numColors; i++) {
            int q = 0x36 + i * 4;
            pal[i] = (short) Framebuffer.rgb(bmp[q + 2] & 0xff, bmp[q + 1] & 0xff, bmp[q] & 0xff);
        }
        int rowBits = bpp * width;
        if ((rowBits & 0x1f) != 0)
            rowBits = (rowBits / 32) * 32 + 32;
        int stride = rowBits / 8;
        Sprite s = new Sprite();
        s.w = width;
        s.h = height;
        s.px = new short[width * height];
        s.transparent = transparent;
        s.key = (short) Framebuffer.rgb(255, 0, 255);
        for (int y = 0; y < height; y++) {
            int row = pixelOfs + stride * (topDown ? y : height - 1 - y);
            int dst = y * width;
            for (int x = 0; x < width; x++) {
                if (bpp == 24) {
                    int q = row + x * 3;
                    if (q + 2 >= bmp.length)
                        continue;
                    s.px[dst + x] = (short) Framebuffer.rgb(bmp[q + 2] & 0xff, bmp[q + 1] & 0xff, bmp[q] & 0xff);
                    continue;
                }
                int idx = bpp == 8 ? bmp[row + x] & 0xff
                        : ((x & 1) != 0 ? bmp[row + (x >> 1)] & 0x0f : (bmp[row + (x >> 1)] & 0xff) >> 4);
                s.px[dst + x] = pal[idx];
            }
        }
        return s;
    }

    static int le16(byte[] p, int o) {
        return (p[o] & 0xff) | ((p[o + 1] & 0xff) << 8);
    }

    static int le32(byte[] p, int o) {
        return (p[o] & 0xff) | ((p[o + 1] & 0xff) << 8)
                | ((p[o + 2] & 0xff) << 16) | ((p[o + 3] & 0xff) << 24);
    }
}
