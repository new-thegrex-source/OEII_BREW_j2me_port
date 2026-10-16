/* Render_beginLoadMapData. A node is a split (axis in args1) or a leaf
 * (first line and count in args2). Spawn stays on spawnTile. */
final class BspMap {
    int floorRgb;
    int ceilRgb;
    int floorH;
    int ceilH = 64;
    int playerTile;
    int spawnTile;
    int spawnDir;
    int numNodes;
    int numLines;
    int numSprites;
    int[] nx1 = new int[400];
    int[] ny1 = new int[400];
    int[] nx2 = new int[400];
    int[] ny2 = new int[400];
    int[] nsplit = new int[400];
    int[] naxis = new int[400];
    int[] nlo = new int[400];
    int[] nhi = new int[400];
    int[] x1 = new int[750];
    int[] y1 = new int[750];
    int[] x2 = new int[750];
    int[] y2 = new int[750];
    int[] tex = new int[750];
    int[] tex2 = new int[750];
    int[] flags = new int[750];
    int[] z1 = new int[750];
    int[] z2 = new int[750];
    int[] sx = new int[400];
    int[] sy = new int[400];
    int[] sz = new int[400];
    int[] stile = new int[400];
    int[] sflags = new int[400];
    int numKeys;
    boolean hasTiles;
    byte[] floorId = new byte[1024];
    byte[] ceilId = new byte[1024];
    byte[] tileFlags = new byte[1024];
    int[] kx = new int[32];
    int[] ky = new int[32];
    int[] kang = new int[32];
    int[] kdur = new int[32];
    String error = "";

    static BspMap load(byte[] b) {
        BspMap m = new BspMap();
        if (b == null || b.length < 32) {
            m.error = "map too small";
            return m;
        }
        int p = 0;
        if ((b[p++] & 0xff) != 10) {
            m.error = "map version " + (b[0] & 0xff);
            return m;
        }
        p += 6;
        int fr = b[p] & 0xff, fg = b[p + 1] & 0xff, fb = b[p + 2] & 0xff;
        int cr = b[p + 3] & 0xff, cg = b[p + 4] & 0xff, cb = b[p + 5] & 0xff;
        p += 9;
        p += 1;
        m.playerTile = u16(b, p);
        p += 2;
        m.spawnDir = b[p++] & 0xff;
        m.spawnTile = u16(b, p);
        p += 2;
        /* Bytes 22/23 are floor and ceiling height. 0 ceiling means 64.
         * 128 is two wall units; the caves play as one, so clamp to 64. */
        m.floorH = b[p++] & 0xff;
        m.ceilH = b[p++] & 0xff;
        if (m.ceilH == 0 || m.ceilH == 128)
            m.ceilH = 64;
        if (m.ceilH < m.floorH)
            m.ceilH = m.floorH + 64;
        p += 1;
        /* Floor/ceiling bytes in the BREW map are B, G, R. */
        m.floorRgb = (fb << 16) | (fg << 8) | fr;
        m.ceilRgb = (cb << 16) | (cg << 8) | cr;
        if (p + 2 > b.length) {
            m.error = "header cut";
            return m;
        }
        int nodes = u16(b, p);
        p += 2;
        if (nodes > 400) {
            m.error = "nodes " + nodes;
            return m;
        }
        for (int i = 0; i < nodes; i++) {
            if (p + 10 > b.length) {
                m.error = "node cut";
                return m;
            }
            m.nx1[i] = (b[p++] & 0xff) << 3;
            m.ny1[i] = (b[p++] & 0xff) << 3;
            m.nx2[i] = (b[p++] & 0xff) << 3;
            m.ny2[i] = (b[p++] & 0xff) << 3;
            int axis = b[p++] & 0xff;
            int split = (b[p++] & 0xff) << 3;
            m.naxis[i] = axis;
            m.nsplit[i] = split;
            m.nlo[i] = u16(b, p);
            p += 2;
            m.nhi[i] = u16(b, p);
            p += 2;
            m.numNodes = i + 1;
        }
        if (p + 2 > b.length) {
            m.error = "lines missing";
            return m;
        }
        int lines = u16(b, p);
        p += 2;
        if (lines > 750) {
            m.error = "lines " + lines;
            return m;
        }
        for (int i = 0; i < lines; i++) {
            if (p + 10 > b.length) {
                m.error = "line cut";
                return m;
            }
            int x1 = (b[p++] & 0xff) << 3;
            int y1 = (b[p++] & 0xff) << 3;
            int x2 = (b[p++] & 0xff) << 3;
            int y2 = (b[p++] & 0xff) << 3;
            int texture = b[p++] & 0xff;
            int texture2 = b[p++] & 0xff;
            int flags = u32(b, p);
            p += 4;
            if ((flags & 8) != 0) {
                if ((flags & 0x200) != 0) {
                    int n = (flags & 0x4000) != 0 ? 2 : (flags & 0x2000) != 0 ? -2 : 0;
                    x1 += n;
                    x2 += n;
                } else if ((flags & 0x100) != 0) {
                    int n = (flags & 0x800) != 0 ? 2 : (flags & 0x1000) != 0 ? -2 : 0;
                    y1 += n;
                    y2 += n;
                }
            }
            int adx = x1 > x2 ? x1 - x2 : x2 - x1;
            int ady = y1 > y2 ? y1 - y2 : y2 - y1;
            int len = adx > ady ? adx : ady;
            m.x1[i] = x1;
            m.y1[i] = y1;
            m.x2[i] = x2;
            m.y2[i] = y2;
            m.tex[i] = texture;
            m.tex2[i] = texture2;
            m.flags[i] = flags;
            m.z1[i] = (flags & 0x8000) == 0 ? 0 : len;
            m.z2[i] = (flags & 0x8000) == 0 ? len : 0;
            m.numLines = i + 1;
        }
        if (p + 4 > b.length) {
            m.error = "sprites missing";
            return m;
        }
        int standing = u16(b, p);
        int placed = u16(b, p + 2);
        p += 4;
        int n = standing + placed;
        if (n > 400)
            n = 400;
        for (int i = 0; i < n; i++) {
            int need = i < standing ? 5 : 7;
            if (p + need > b.length)
                break;
            m.sx[i] = (b[p++] & 0xff) << 3;
            m.sy[i] = (b[p++] & 0xff) << 3;
            if (i < standing) {
                m.sz[i] = 0x20;
                m.stile[i] = b[p++] & 0xff;
            } else {
                m.sz[i] = (b[p++] & 0xff) - 0x20;
                m.stile[i] = b[p++] & 0xff;
                m.sflags[i] = b[p++] & 0xff;
            }
            int extra = u16(b, p);
            p += 2;
            m.sflags[i] |= extra << 16;
            if ((m.sflags[i] & 0x800000) != 0) {
                if ((m.sflags[i] & 0x100000) != 0)
                    m.sy[i] += 1;
                else if ((m.sflags[i] & 0x80000) != 0)
                    m.sy[i] -= 1;
                else if ((m.sflags[i] & 0x200000) != 0)
                    m.sx[i] += 1;
                else if ((m.sflags[i] & 0x400000) != 0)
                    m.sx[i] -= 1;
            }
            m.numSprites = i + 1;
        }
        readTail(m, b, p);
        return m;
    }

    /* After sprites: tile events, bytecode, a nibble of flags per tile,
     * then 32x32 floor ids and 32x32 ceiling ids. Camera keys are op 0x36. */
    static void readTail(BspMap m, byte[] b, int p) {
        if (p + 2 > b.length)
            return;
        int events = u16(b, p);
        p += 2;
        if (events > 400)
            return;
        p += events * 2;
        p += events * 4;
        if (p + 2 > b.length)
            return;
        int codes = u16(b, p);
        p += 2;
        int duration = 1000;
        for (int i = 0; i < codes; i++) {
            if (p + 9 > b.length)
                return;
            int op = b[p++] & 0xff;
            int arg = u32(b, p);
            p += 8;
            if (op == 0x35)
                duration = arg;
            else if (op == 0x36 && m.numKeys < 32) {
                int tileX = arg & 0x1f;
                int tileY = (arg >> 5) & 0x1f;
                m.kx[m.numKeys] = tileX * 64 + 32;
                m.ky[m.numKeys] = tileY * 64 + 32;
                m.kang[m.numKeys] = ((arg >> 18) & 7) * 128;
                m.kdur[m.numKeys] = duration < 1 ? 1000 : duration;
                m.numKeys++;
            }
        }
        if (p + 0x200 + 0x800 > b.length)
            return;
        for (int i = 0; i < 0x200; i++) {
            int bflags = b[p++] & 0xff;
            m.tileFlags[i * 2] = (byte) (bflags & 0xf);
            m.tileFlags[i * 2 + 1] = (byte) (bflags >> 4);
        }
        for (int i = 0; i < 1024; i++)
            m.floorId[i] = b[p++];
        for (int i = 0; i < 1024; i++)
            m.ceilId[i] = b[p++];
        m.hasTiles = true;
    }

    int tileAt(int x, int y, boolean ceiling) {
        int tx = x >> 6;
        int ty = y >> 6;
        if (tx < 0 || ty < 0 || tx > 31 || ty > 31)
            return 0;
        int i = ty * 32 + tx;
        return (ceiling ? ceilId[i] : floorId[i]) & 0xff;
    }

    boolean isSplit(int node) {
        return (naxis[node] & 3) != 0;
    }

    /* Phone and qa/bsp.py: start tile is the ushort at 0x11, dir at 0x13.
     * Offset 20 is the front-end camera tile, not the player. */
    int spawnX() {
        int tile = playerTile;
        return (tile & 31) * 64 + 32;
    }

    int spawnY() {
        int tile = playerTile;
        return ((tile >> 5) & 31) * 64 + 32;
    }

    /* Game_spawnPlayer: 0 -> 512, 1 -> 0, 3 -> 768, else 256. */
    int spawnAngle() {
        switch (spawnDir & 7) {
        case 0:
            return 0x200;
        case 1:
            return 0;
        case 3:
            return 0x300;
        default:
            return 0x100;
        }
    }

    static int u16(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8);
    }

    static int u32(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 24);
    }
}
