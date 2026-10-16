/* Render_loadMappings. Six counts, then wall/flat/sprite records of
 * {texel offset, palette index}. The palette int is stored shifted by 8. */
final class Mappings {
    static int numWalls;
    static int numFlats;
    static int numSprites;
    static int[] wallOfs = new int[1024];
    static int[] wallPal = new int[1024];
    static int[] flatOfs = new int[256];
    static int[] flatPal = new int[256];
    static int[] spriteOfs = new int[1100];
    static int[] spritePal = new int[1100];
    static int[] spriteRemap = new int[256];
    static int numSpriteRemap;
    static int[] wallRemap = new int[256];
    static int[] flatRemap = new int[256];
    static int numWallRemap;
    static int numFlatRemap;
    static String error = "";

    static boolean load() {
        numWalls = 0;
        numFlats = 0;
        numSprites = 0;
        numWallRemap = 0;
        numFlatRemap = 0;
        numSpriteRemap = 0;
        error = "";
        byte[] b = Resource.open(1004);
        if (b == null) {
            try {
                b = Resource.readJar("/mappings.bin");
            } catch (Exception ignored) {
            }
        }
        if (b == null || b.length < 24) {
            error = "no mappings.bin";
            return false;
        }
        int p = 0;
        int walls = i32(b, p);
        int flats = i32(b, p + 4);
        int sprites = i32(b, p + 8);
        int nWallRemap = i32(b, p + 12);
        int nFlatRemap = i32(b, p + 16);
        p += 24;
        if (walls < 0 || flats < 0 || sprites < 0 || walls > 4096) {
            error = "mappings counts";
            return false;
        }
        numWalls = walls > wallOfs.length ? wallOfs.length : walls;
        for (int i = 0; i < walls; i++) {
            if (p + 8 > b.length)
                break;
            int ofs = i32(b, p);
            int pal = i32(b, p + 4);
            p += 8;
            if (i < wallOfs.length) {
                wallOfs[i] = ofs;
                wallPal[i] = pal;
            }
        }
        numFlats = flats > flatOfs.length ? flatOfs.length : flats;
        for (int i = 0; i < flats; i++) {
            if (p + 8 > b.length)
                break;
            int ofs = i32(b, p);
            int pal = i32(b, p + 4);
            p += 8;
            if (i < flatOfs.length) {
                flatOfs[i] = ofs;
                flatPal[i] = pal;
            }
        }
        numSprites = sprites > spriteOfs.length ? spriteOfs.length : sprites;
        for (int i = 0; i < sprites; i++) {
            if (p + 8 > b.length)
                break;
            if (i < spriteOfs.length) {
                spriteOfs[i] = i32(b, p);
                spritePal[i] = i32(b, p + 4);
            }
            p += 8;
        }
        if (nWallRemap < 0 || nWallRemap > 256)
            nWallRemap = 0;
        if (nFlatRemap < 0 || nFlatRemap > 256)
            nFlatRemap = 0;
        numWallRemap = nWallRemap;
        for (int i = 0; i < nWallRemap; i++) {
            if (p + 2 > b.length)
                break;
            wallRemap[i] = (short) (((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8)) & 0xffff);
            p += 2;
        }
        numFlatRemap = nFlatRemap;
        for (int i = 0; i < nFlatRemap; i++) {
            if (p + 2 > b.length)
                break;
            flatRemap[i] = (short) ((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8));
            p += 2;
        }
        int nSpriteRemap = i32(b, 20);
        if (nSpriteRemap < 0 || nSpriteRemap > 256)
            nSpriteRemap = 0;
        numSpriteRemap = nSpriteRemap;
        for (int i = 0; i < nSpriteRemap; i++) {
            if (p + 2 > b.length)
                break;
            spriteRemap[i] = (short) ((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8));
            p += 2;
        }
        return numWalls > 0;
    }

    /* BSP tile byte is not a bitshape. Phone draws spriteRemap[tile] + frame. */
    static int shapeIndex(int tile, int frame) {
        if (numSpriteRemap <= 0)
            return tile;
        if (tile < 0 || tile >= numSpriteRemap)
            return -1;
        int base = spriteRemap[tile];
        if (base == -1 || base == 0x7fff)
            return -1;
        int at = base + (frame & 15);
        if (at < 0 || at >= numSprites)
            return -1;
        return at;
    }

    static int i32(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 24);
    }
}
