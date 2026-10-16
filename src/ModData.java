/* Tables the BREW build keeps inside orcsandelves2.mod. File offset == code address.
 * The JAR carries the user's own .mod; these bytes are not copied into source. */
final class ModData {
    static final int EXPECTED_SIZE = 292116;
    static final int EXPECTED_CRC = 0x1631c7f1;

    static final int FMT_OUT_OF_MEMORY = 0x45e80;
    static final int FMT_BAD_BPP = 0x4710c;

    byte[] data;

    static ModData open(byte[] raw) {
        ModData m = new ModData();
        if (raw == null)
            return m;
        if (raw.length != EXPECTED_SIZE || Crc32.of(raw) != EXPECTED_CRC)
            throw new RuntimeException("mod is not the EN EA 2007 build");
        m.data = raw;
        return m;
    }

    boolean ok() {
        return data != null;
    }

    int u8(int off) {
        return data != null && off >= 0 && off < data.length ? data[off] & 0xff : 0;
    }

    int s8(int off) {
        int v = u8(off);
        return v >= 128 ? v - 256 : v;
    }

    int u16(int off) {
        if (data == null || off < 0 || off + 1 >= data.length)
            return 0;
        return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
    }

    int s16(int off) {
        int v = u16(off);
        return v >= 32768 ? v - 65536 : v;
    }

    int u32(int off) {
        if (data == null || off < 0 || off + 3 >= data.length)
            return 0;
        return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8)
                | ((data[off + 2] & 0xff) << 16) | ((data[off + 3] & 0xff) << 24);
    }

    String str(int off) {
        if (data == null || off < 0 || off >= data.length)
            return "";
        int end = off;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, off, end - off);
    }
}
