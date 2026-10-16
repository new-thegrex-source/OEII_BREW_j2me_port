/* bitshapes.bin: size, then a shape. Bytes 0..3 are the stexels index,
 * 8..11 first/last column and row, then a bitmask from byte 12.
 * Only set bits take a pixel from stexels.bin. */
final class Sprites {
    static byte[] shapes;
    static byte[] stexels;
    static int stexelCount;
    static int[] stexelOfs;
    static String error = "";

    static void load() {
        error = "";
        shapes = Resource.open(1005);
        stexels = Resource.open(1006);
        stexelCount = 0;
        if (shapes == null || shapes.length < 16)
            error = "no bitshapes.bin";
        if (stexels == null || stexels.length < 8) {
            error = "no stexels.bin";
            return;
        }
        stexelCount = i32(stexels, 0);
        if (stexelCount <= 0 || 4 + stexelCount * 4 > stexels.length) {
            stexelCount = 0;
            error = "stexels count";
            return;
        }
        stexelOfs = new int[stexelCount];
        for (int i = 0; i < stexelCount; i++)
            stexelOfs[i] = i32(stexels, 4 + i * 4);
        findWeapons();
    }

    static final int[] weaponTiles = new int[8];
    static final int[] weaponStex = new int[8];
    static final String[] weaponNames = new String[8];
    static int weaponCount;
    static int selected;

    /* View weapons are bitshapes whose stexel index is the sheet.
     * The wand in the hand is STEX000, not a map critter that happens
     * to be thin (that guess was drawing Bob). */
    static void findWeapons() {
        weaponCount = 0;
        selected = 0;
        int[] want = { 0, 1, 2, 3, 4, 5 };
        String[] names = { "Wand", "Dagger", "Staff", "Bow", "Hammer", "Sling" };
        for (int i = 0; i < want.length; i++) {
            int tile = shapeForStexel(want[i]);
            if (tile < 0)
                continue;
            weaponTiles[weaponCount] = tile;
            weaponStex[weaponCount] = want[i];
            weaponNames[weaponCount] = names[i];
            weaponCount++;
        }
        if (weaponCount == 0) {
            weaponTiles[0] = 0;
            weaponStex[0] = 0;
            weaponNames[0] = "Wand";
            weaponCount = 1;
        }
    }

    static int shapeForStexel(int stex) {
        int n = Mappings.numSprites;
        if (shapes == null || n <= 0)
            return -1;
        if (n > shapes.length)
            n = shapes.length;
        int fallback = -1;
        for (int t = 0; t < n; t++) {
            int ofs = Mappings.spriteOfs[t];
            if (ofs < 0 || ofs + 4 > shapes.length)
                continue;
            if (u32(shapes, 4 + ofs) != stex)
                continue;
            if (bounds(t) == null)
                continue;
            if (!usedOnMap(t))
                return t;
            if (fallback < 0)
                fallback = t;
        }
        return fallback;
    }

    static String weaponName() {
        if (weaponCount <= 0)
            return "Wand";
        int i = selected;
        if (i < 0 || i >= weaponCount)
            i = 0;
        return weaponNames[i];
    }

    static void selectWeapon(int i) {
        if (weaponCount <= 0)
            return;
        if (i < 0)
            i = weaponCount - 1;
        selected = i % weaponCount;
    }

    static void cycleWeapon(int dir) {
        if (weaponCount <= 0)
            return;
        selectWeapon(selected + dir);
    }

    static boolean usedOnMap(int tile) {
        if (Game.world == null)
            return false;
        for (int i = 0; i < Game.world.numSprites; i++) {
            if (Game.world.stile[i] == tile)
                return true;
        }
        return false;
    }

    static boolean sameHand(int a, int b) {
        int[] ba = bounds(a);
        int[] bb = bounds(b);
        if (ba == null || bb == null)
            return false;
        return Math.abs(ba[3] - bb[3]) < 12 && Math.abs(ba[1] - bb[1]) < 16;
    }

    static int[] bounds(int tile) {
        if (shapes == null || tile < 0 || tile >= Mappings.numSprites)
            return null;
        int ofs = Mappings.spriteOfs[tile];
        if (ofs < 0 || ofs + 12 >= shapes.length)
            return null;
        int base = 4 + ofs;
        int x0 = shapes[base + 8] & 0xff;
        int x1 = shapes[base + 9] & 0xff;
        int y0 = shapes[base + 10] & 0xff;
        int y1 = shapes[base + 11] & 0xff;
        if (x1 < x0 || y1 < y0)
            return null;
        return new int[] { x0, x1, y0, y1 };
    }

    /* Bottom-anchored view weapon. frame 0 idle, 1 strike, 2 recover. */
    static void drawWeapon(Framebuffer fb, int frame, int bob) {
        if (weaponCount == 0 || fb == null)
            return;
        if (frame < 0)
            frame = 0;
        if (frame >= weaponCount)
            frame = 0;
        int which = selected;
        if (which < 0 || which >= weaponCount)
            which = 0;
        int tile = weaponTiles[which];
        int[] box = bounds(tile);
        if (box == null)
            return;
        int x0 = box[0], x1 = box[1], y0 = box[2], y1 = box[3];
        int sw = x1 - x0 + 1;
        int sh = y1 - y0 + 1;
        int dh = 72;
        int dw = dh * sw / sh;
        if (dw > 150)
            dw = 150;
        int dx0 = Framebuffer.W - dw - 4;
        int dy0 = TinyGL.VIEW_TOP + TinyGL.VIEW_H - dh - 6 + bob;
        for (int y = 0; y < dh; y++) {
            int sy = dy0 + y;
            if (sy < TinyGL.VIEW_TOP || sy >= Framebuffer.H - 18)
                continue;
            int row = y0 + y * sh / dh;
            for (int x = 0; x < dw; x++) {
                int sx = dx0 + x;
                if (sx < 0 || sx >= Framebuffer.W)
                    continue;
                short c = pixel(tile, x0 + x * sw / dw, row);
                if (c == 0 || isKey(c))
                    continue;
                fb.px[sy * Framebuffer.W + sx] = c;
            }
        }
    }

    static boolean isKey(short c) {
        int v = c & 0xffff;
        int r = (v >> 11) & 31;
        int g = (v >> 5) & 63;
        int b = v & 31;
        return r > 28 && b > 28 && g < 8;
    }

    static short pixel(int tile, int col, int row) {
        if (shapes == null || tile < 0 || tile >= Mappings.numSprites)
            return 0;
        int ofs = Mappings.spriteOfs[tile];
        if (ofs < 0 || ofs + 12 >= shapes.length)
            return 0;
        int base = 4 + ofs;
        int firstCol = shapes[base + 8] & 0xff;
        int lastCol = shapes[base + 9] & 0xff;
        int firstRow = shapes[base + 10] & 0xff;
        int lastRow = shapes[base + 11] & 0xff;
        if (col < firstCol || col > lastCol || row < firstRow || row > lastRow)
            return 0;
        int rows = lastRow - firstRow + 1;
        int colBytes = (rows + 7) / 8;
        int maskAt = base + 12 + colBytes * (col - firstCol);
        int bitRow = row - firstRow;
        if (maskAt + bitRow / 8 >= shapes.length)
            return 0;
        if ((shapes[maskAt + bitRow / 8] & (1 << (bitRow & 7))) == 0)
            return 0;
        int texAt = opaqueIndex(base, firstCol, col, firstRow, row, rows, colBytes);
        return stexel(u32(shapes, base), texAt, Mappings.spritePal[tile]);
    }

    static int opaqueIndex(int base, int firstCol, int col, int firstRow, int row, int rows, int colBytes) {
        int n = 0;
        for (int c = firstCol; c <= col; c++) {
            int maskAt = base + 12 + colBytes * (c - firstCol);
            int last = c == col ? row - firstRow : rows;
            for (int y = 0; y < last; y++) {
                if (maskAt + y / 8 >= shapes.length)
                    return n;
                if ((shapes[maskAt + y / 8] & (1 << (y & 7))) != 0)
                    n++;
            }
        }
        return n;
    }

    static short stexel(int index, int pixel, int pal) {
        if (stexelCount == 0 || index < 0 || index >= stexelCount)
            return 0;
        int at = 4 + stexelOfs[index];
        if (at < 0 || at >= stexels.length)
            return 0;
        int fmt = stexels[at] & 0xff;
        if (fmt == 4) {
            int byteAt = at + 1 + pixel / 2;
            if (byteAt >= stexels.length)
                return 0;
            int v = stexels[byteAt] & 0xff;
            int idx = (pixel & 1) == 0 ? v & 0x0f : v >> 4;
            return Palettes.color(pal, idx);
        }
        int byteAt = at + 1 + pixel;
        if (byteAt >= stexels.length)
            return 0;
        return Palettes.color(pal, stexels[byteAt] & 0xff);
    }

    static int i32(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 24);
    }

    static int u32(byte[] b, int p) {
        return i32(b, p);
    }
}
