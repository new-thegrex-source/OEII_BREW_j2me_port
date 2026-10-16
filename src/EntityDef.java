/* Entity definitions from entities.bin (resource 1000).
 * Format: u16 count, then 13-byte records
 * {s16 tile, u8 subId, u8 eType, u8 eSubType, s16 parm,
 *  s16 nameId, s16 longNameId, s16 descId}.
 * Names are string ids in group 1 of strings.bin. */
final class EntityDef {
    static final int MAX = 200;
    static EntityDef[] list = new EntityDef[MAX];
    static int count;

    int tileIndex;
    int subId;
    int eType;
    int eSubType;
    int parm;
    int nameId;
    int longNameId;
    int descId;
    String name = "";
    String longName = "";
    String description = "";

    static void load() {
        count = 0;
        byte[] data = Resource.open(1000);
        if (data == null || data.length < 2)
            return;
        int n = u16(data, 0);
        if (n < 0 || n > MAX)
            n = MAX;
        int p = 2;
        for (int i = 0; i < n && p + 13 <= data.length; i++) {
            EntityDef d = new EntityDef();
            d.tileIndex = s16(data, p); p += 2;
            d.subId = data[p++] & 0xff;
            d.eType = data[p++] & 0xff;
            d.eSubType = data[p++] & 0xff;
            d.parm = s16(data, p); p += 2;
            d.nameId = s16(data, p); p += 2;
            d.longNameId = s16(data, p); p += 2;
            d.descId = s16(data, p); p += 2;
            list[count++] = d;
        }
        resolveNames();
    }

    static void resolveNames() {
        String[] group1 = loadGroup(1);
        if (group1 == null)
            return;
        for (int i = 0; i < count; i++) {
            EntityDef d = list[i];
            if (d.nameId >= 0 && d.nameId < group1.length)
                d.name = group1[d.nameId];
            if (d.longNameId >= 0 && d.longNameId < group1.length)
                d.longName = group1[d.longNameId];
            if (d.descId >= 0 && d.descId < group1.length)
                d.description = group1[d.descId];
        }
    }

    /* strings.bin: int count; int groupOfs[count]; then NUL-separated text. */
    static String[] loadGroup(int group) {
        byte[] bin = Resource.open(1002);
        if (bin == null || bin.length < 8)
            return null;
        int groups = i32(bin, 0);
        if (groups < group + 2 || 4 + (groups + 1) * 4 > bin.length)
            return null;
        int start = i32(bin, 4 + group * 4);
        int end = i32(bin, 4 + (group + 1) * 4);
        int base = 4 + groups * 4;
        if (start < 0 || end < start || base + end > bin.length)
            return null;
        int n = 0;
        for (int i = start; i < end; i++)
            if (bin[base + i] == 0)
                n++;
        String[] out = new String[n + 1];
        int s = start;
        int k = 0;
        for (int i = start; i < end && k < out.length; i++) {
            if (bin[base + i] == 0) {
                out[k++] = new String(bin, base + s, i - s);
                s = i + 1;
            }
        }
        if (k < out.length)
            out[k] = "";
        return out;
    }

    static EntityDef findByTile(int tile) {
        for (int i = 0; i < count; i++)
            if (list[i].tileIndex == tile)
                return list[i];
        return null;
    }

    static EntityDef find(int eType, int eSubType, int parm) {
        for (int i = 0; i < count; i++) {
            EntityDef d = list[i];
            if (d.eType == eType && d.eSubType == eSubType && (parm == -1 || d.parm == parm))
                return d;
        }
        return null;
    }

    static EntityDef findType(int eType, int eSubType) {
        return find(eType, eSubType, -1);
    }

    static int u16(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8);
    }

    static int s16(byte[] b, int p) {
        int v = u16(b, p);
        return v >= 32768 ? v - 65536 : v;
    }

    static int i32(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 24);
    }
}
