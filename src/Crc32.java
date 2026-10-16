/* IEEE CRC32, init 0, same polynomial zlib uses. BAR/MOD checks match the C port. */
final class Crc32 {
    private static final int[] TABLE = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int k = 0; k < 8; k++)
                c = (c & 1) != 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
            TABLE[i] = c;
        }
    }

    static int of(byte[] data) {
        int c = ~0;
        for (int i = 0; i < data.length; i++)
            c = TABLE[(c ^ data[i]) & 0xff] ^ (c >>> 8);
        return ~c;
    }
}
