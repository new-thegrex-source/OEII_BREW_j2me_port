/* BREW wall/flat sheets. wtexels are 117x117, column-major (index = 117*col + row).
 * ftexels are 64x64. mode 8 is raw indices, anything else is 4bpp, low nibble first.
 * A map texture byte indexes wallRemap/flatRemap, not the file slot.
 * The mapping record then picks the sheet and the palette (raw or << 8). */
final class Tex {
    static final int SIZE = 64;
    static final int WALL = 117;

    static short[][] walls = new short[256][];
    static int count;

    static short[][] flats = new short[64][];
    static int flatCount;

    static byte[][] wallIdx = new byte[256][];
    static int wallTexCount;
    static int diagMode;
    static int diagPal;
    static int diagOfs;
    static int diagSpan;
    static int diagRaw;
    static int diagPacked;

    static void load() {
        count = 0;
        flatCount = 0;
        wallTexCount = 0;
        walls = new short[256][];
        flats = new short[64][];
        wallIdx = new byte[256][];
        if (!Palettes.load())
            Palettes.synthesize();
        byte[][] sheets = pages(openTex(1007, "/wtexels.bin"), WALL);
        byte[][] floors = pages(openTex(1008, "/ftexels.bin"), SIZE);
        bakeWalls(sheets);
        bakeFlats(floors);
    }

    static byte[] openTex(int id, String name) {
        byte[] file = Resource.open(id);
        if (file != null)
            return file;
        try {
            return Resource.readJar(name);
        } catch (Exception ignored) {
            return null;
        }
    }

    static byte[][] pages(byte[] file, int side) {
        byte[][] out = new byte[512][];
        if (file == null || file.length < 8)
            return out;
        int n = i32(file, 0);
        if (n <= 0 || n > 512 || 4 + n * 4 > file.length)
            return out;
        int[] at = new int[n];
        for (int i = 0; i < n; i++)
            at[i] = i32(file, 4 + i * 4) + 4;
        for (int i = 0; i < n; i++) {
            int end = i + 1 < n ? at[i + 1] : file.length;
            if (end < at[i])
                end = file.length;
            out[i] = page(file, at[i], end - at[i], side);
        }
        return out;
    }

    /* Phone: format byte 4 is packed 4bpp, low nibble first. Anything else
     * (including 8) is raw indices. A sheet whose span is only half a page
     * is packed even if the marker is not 4 — splitting a full 8-bit page
     * into nibbles is what painted the walls as rainbow noise. */
    static byte[] page(byte[] file, int at, int span, int side) {
        if (at < 0 || at >= file.length || span < 2)
            return null;
        int mode = file[at] & 0xff;
        if (diagMode == 0)
            diagMode = mode == 0 ? 256 : mode;
        if (diagSpan == 0)
            diagSpan = span;
        if (mode == 4)
            diagPacked++;
        else
            diagRaw++;
        int pixels = side * side;
        int data = span - 1;
        if (data < 1)
            return null;
        boolean packed = mode == 4 || (mode != 8 && data < pixels);
        byte[] idx = new byte[pixels];
        if (!packed) {
            int n = data < pixels ? data : pixels;
            for (int i = 0; i < n; i++)
                idx[i] = file[at + 1 + i];
            return idx;
        }
        int packedN = (pixels + 1) / 2;
        int n = data < packedN ? data : packedN;
        int o = 0;
        for (int k = 0; k < n && o < pixels; k++) {
            int v = file[at + 1 + k] & 0xff;
            idx[o++] = (byte) (v & 0x0f);
            if (o < pixels)
                idx[o++] = (byte) ((v >> 4) & 0x0f);
        }
        return idx;
    }

    static void bakeWalls(byte[][] sheets) {
        int n = Mappings.numWalls;
        if (n <= 0)
            n = 0;
        int bound = 0;
        for (int i = 0; i < n && i < walls.length; i++) {
            int shape = Mappings.wallOfs[i];
            byte[] src = shape >= 0 && shape < sheets.length ? sheets[shape] : null;
            if (src == null)
                continue;
            short[] img = bake(src, Mappings.wallPal[i], Mappings.wallOfs[i] >= 0);
            walls[i] = img;
            wallIdx[i] = src;
            if (bound == 0) {
                diagPal = Mappings.wallPal[i];
                diagOfs = Mappings.wallOfs[i];
            }
            bound++;
        }
        if (bound == 0) {
            for (int i = 0; i < sheets.length && i < walls.length; i++) {
                if (sheets[i] == null)
                    continue;
                walls[i] = bake(sheets[i], 0, true);
                wallIdx[i] = sheets[i];
                bound++;
            }
        }
        wallTexCount = bound;
        count = bound;
    }

    static void bakeFlats(byte[][] sheets) {
        int n = Mappings.numFlats;
        int bound = 0;
        for (int i = 0; i < n && i < flats.length; i++) {
            int shape = Mappings.flatOfs[i];
            byte[] src = shape >= 0 && shape < sheets.length ? sheets[shape] : null;
            if (src == null)
                continue;
            flats[i] = bake(rowMajor(src, SIZE), Mappings.flatPal[i], false);
            bound++;
        }
        if (bound == 0) {
            for (int i = 0; i < sheets.length && i < flats.length; i++) {
                if (sheets[i] == null)
                    continue;
                flats[i] = bake(rowMajor(sheets[i], SIZE), 0, false);
                bound++;
            }
        }
        flatCount = bound;
    }

    /* Phone sheets are column-major. Floor rows sample row-major. */
    static byte[] rowMajor(byte[] src, int side) {
        int n = side * side;
        if (src.length < n)
            return src;
        byte[] dst = new byte[n];
        for (int y = 0; y < side; y++)
            for (int x = 0; x < side; x++)
                dst[y * side + x] = src[x * side + y];
        return dst;
    }

    static short[] bake(byte[] src, int pal, boolean wall) {
        short[] img = new short[src.length];
        for (int i = 0; i < src.length; i++)
            img[i] = Palettes.color(pal, src[i] & 0xff);
        return img;
    }

    static int wallSlot(int id) {
        if (id < 0)
            id = 0;
        id &= 255;
        if (Mappings.numWallRemap > 0) {
            int slot = Mappings.wallRemap[id] & 0xffff;
            /* 0x7fff is the phone's empty remap, not a sheet. */
            if (slot == 0x7fff || slot >= Mappings.numWalls)
                return 0;
            return slot;
        }
        return id;
    }

    static int flatSlot(int id) {
        if (id < 0)
            id = 0;
        id &= 255;
        if (Mappings.numFlatRemap > 0) {
            int slot = Mappings.flatRemap[id] & 0xffff;
            if (slot >= Mappings.numFlats)
                slot = 0;
            return slot;
        }
        return id;
    }

    static short flat(int id, int u, int v) {
        return flatAt(id, u, v);
    }

    static short sample(int id, int u, int v) {
        return wallTexel(id, (u & 116) * WALL + (v & 116));
    }

    static boolean hasWall(int id) {
        int slot = wallSlot(id);
        return slot >= 0 && slot < walls.length && walls[slot] != null;
    }

    static short wallTexel(int id, int texel) {
        int slot = wallSlot(id);
        short[] img = slot >= 0 && slot < walls.length ? walls[slot] : null;
        if (img == null)
            return 0;
        int at = texel % img.length;
        if (at < 0)
            at += img.length;
        return img[at];
    }

    static short flatAt(int id, int u, int v) {
        if (flatCount == 0)
            return 0;
        int slot = flatSlot(id);
        if (slot < 0)
            slot = 0;
        if (slot >= flats.length || flats[slot] == null)
            slot = slot % flatCount;
        short[] img = slot >= 0 && slot < flats.length ? flats[slot] : null;
        if (img == null)
            return 0;
        return img[(v & 63) * SIZE + (u & 63)];
    }

    /* 48x48 of the first baked wall, unscaled. If this square is already
     * rainbow, the sheet/palette is wrong before the rasterizer. If it is
     * stone and the world is not, the bug is the wall id or the UV. */
    static void swatch(Framebuffer fb, int x, int y) {
        short[] img = null;
        for (int i = 0; i < walls.length; i++) {
            if (walls[i] != null) {
                img = walls[i];
                break;
            }
        }
        if (img == null || fb == null)
            return;
        int n = 48;
        for (int row = 0; row < n; row++) {
            int oy = y + row;
            if (oy < 0 || oy >= Framebuffer.H)
                continue;
            for (int col = 0; col < n; col++) {
                int ox = x + col;
                if (ox < 0 || ox >= Framebuffer.W)
                    continue;
                int at = col * WALL + (row * WALL / n);
                if (at < 0 || at >= img.length)
                    continue;
                fb.px[oy * Framebuffer.W + ox] = img[at];
            }
        }
    }

    static int i32(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 24);
    }
}
