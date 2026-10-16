/* CLDC gzip/deflate. BAR payloads are gzip members, often with an FNAME. */
final class Inflate {
    private final byte[] in;
    private int inPos;
    private final int inEnd;
    private int bitBuf;
    private int bitCnt;
    private byte[] out;
    private int outPos;

    private Inflate(byte[] src, int off, int len) {
        in = src;
        inPos = off;
        inEnd = off + len;
    }

    static byte[] gunzip(byte[] src, int off, int len) {
        if (len < 18 || u8at(src, off) != 0x1f || u8at(src, off + 1) != 0x8b)
            throw new RuntimeException("not gzip");
        int flags = u8at(src, off + 3);
        int p = off + 10;
        if ((flags & 4) != 0)
            p += 2 + (u8at(src, p) | (u8at(src, p + 1) << 8));
        if ((flags & 8) != 0) {
            while (src[p] != 0) p++;
            p++;
        }
        if ((flags & 16) != 0) {
            while (src[p] != 0) p++;
            p++;
        }
        if ((flags & 2) != 0) p += 2;
        int isize = u8at(src, off + len - 4)
                | (u8at(src, off + len - 3) << 8)
                | (u8at(src, off + len - 2) << 16)
                | (u8at(src, off + len - 1) << 24);
        return new Inflate(src, p, (off + len - 8) - p).run(isize);
    }

    static String gzipName(byte[] src, int off, int len) {
        if (len < 10 || u8at(src, off) != 0x1f || u8at(src, off + 1) != 0x8b)
            return "";
        if ((u8at(src, off + 3) & 8) == 0)
            return "";
        int p = off + 10;
        if ((u8at(src, off + 3) & 4) != 0)
            p += 2 + (u8at(src, p) | (u8at(src, p + 1) << 8));
        int end = p;
        while (end < off + len && src[end] != 0) end++;
        return new String(src, p, end - p);
    }

    private byte[] run(int expected) {
        out = new byte[expected > 0 ? expected : 256];
        boolean last = false;
        while (!last) {
            last = bits(1) != 0;
            int type = bits(2);
            if (type == 0) {
                align();
                int len = u16();
                int nlen = u16();
                if ((len ^ 0xffff) != nlen)
                    throw new RuntimeException("bad stored block");
                ensure(len);
                for (int i = 0; i < len; i++)
                    out[outPos++] = (byte) u8();
            } else if (type == 1) {
                codes(fixedLit(), fixedDist());
            } else if (type == 2) {
                int nlit = bits(5) + 257;
                int ndist = bits(5) + 1;
                int ncode = bits(4) + 4;
                int[] clen = new int[19];
                int[] order = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
                for (int i = 0; i < ncode; i++)
                    clen[order[i]] = bits(3);
                Huffman cl = new Huffman(clen, 19);
                int[] lens = new int[nlit + ndist];
                int i = 0;
                while (i < lens.length) {
                    int s = cl.decode(this);
                    if (s < 16) {
                        lens[i++] = s;
                    } else if (s == 16) {
                        int rep = bits(2) + 3;
                        int prev = lens[i - 1];
                        while (rep-- > 0) lens[i++] = prev;
                    } else if (s == 17) {
                        int rep = bits(3) + 3;
                        while (rep-- > 0) lens[i++] = 0;
                    } else {
                        int rep = bits(7) + 11;
                        while (rep-- > 0) lens[i++] = 0;
                    }
                }
                int[] dl = new int[ndist];
                System.arraycopy(lens, nlit, dl, 0, ndist);
                codes(new Huffman(lens, nlit), new Huffman(dl, ndist));
            } else {
                throw new RuntimeException("bad block " + type);
            }
        }
        if (expected > 0 && outPos != expected)
            throw new RuntimeException("isize " + outPos + " != " + expected);
        if (outPos == out.length)
            return out;
        byte[] trimmed = new byte[outPos];
        System.arraycopy(out, 0, trimmed, 0, outPos);
        return trimmed;
    }

    private void codes(Huffman lit, Huffman dist) {
        int[] lenBase = {3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
                35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258};
        int[] lenExtra = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
                3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
        int[] distBase = {1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
                257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577};
        int[] distExtra = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
                7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};
        for (;;) {
            int sym = lit.decode(this);
            if (sym < 256) {
                ensure(1);
                out[outPos++] = (byte) sym;
            } else if (sym == 256) {
                return;
            } else {
                int li = sym - 257;
                int length = lenBase[li] + bits(lenExtra[li]);
                int di = dist.decode(this);
                int distance = distBase[di] + bits(distExtra[di]);
                ensure(length);
                int from = outPos - distance;
                for (int i = 0; i < length; i++)
                    out[outPos++] = out[from + i];
            }
        }
    }

    private void ensure(int add) {
        if (outPos + add <= out.length)
            return;
        int n = out.length;
        while (n < outPos + add)
            n = n < 1024 ? 1024 : n * 2;
        byte[] grown = new byte[n];
        System.arraycopy(out, 0, grown, 0, outPos);
        out = grown;
    }

    int bits(int n) {
        while (bitCnt < n) {
            if (inPos >= inEnd)
                throw new RuntimeException("truncated deflate");
            bitBuf |= (in[inPos++] & 0xff) << bitCnt;
            bitCnt += 8;
        }
        int v = bitBuf & ((1 << n) - 1);
        bitBuf >>= n;
        bitCnt -= n;
        return v;
    }

    private void align() {
        bitBuf = 0;
        bitCnt = 0;
    }

    private int u8() {
        if (inPos >= inEnd)
            throw new RuntimeException("truncated");
        return in[inPos++] & 0xff;
    }

    private int u16() {
        return u8() | (u8() << 8);
    }

    private static int u8at(byte[] s, int i) {
        return s[i] & 0xff;
    }

    private static Huffman fixedLit() {
        int[] lens = new int[288];
        for (int i = 0; i <= 143; i++) lens[i] = 8;
        for (int i = 144; i <= 255; i++) lens[i] = 9;
        for (int i = 256; i <= 279; i++) lens[i] = 7;
        for (int i = 280; i <= 287; i++) lens[i] = 8;
        return new Huffman(lens, 288);
    }

    private static Huffman fixedDist() {
        int[] lens = new int[32];
        for (int i = 0; i < 32; i++) lens[i] = 5;
        return new Huffman(lens, 32);
    }

    static final class Huffman {
        private final int[] counts = new int[16];
        private final int[] symbols;
        private final int[] first = new int[16];
        private final int[] offs = new int[16];

        Huffman(int[] lengths, int n) {
            for (int i = 0; i < n; i++) {
                int L = lengths[i];
                if (L > 0) counts[L]++;
            }
            symbols = new int[n];
            int code = 0;
            int si = 0;
            for (int L = 1; L <= 15; L++) {
                code = (code + counts[L - 1]) << 1;
                first[L] = code;
                offs[L] = si;
                si += counts[L];
            }
            int[] pos = new int[16];
            System.arraycopy(offs, 0, pos, 0, 16);
            for (int i = 0; i < n; i++) {
                int L = lengths[i];
                if (L > 0)
                    symbols[pos[L]++] = i;
            }
        }

        int decode(Inflate z) {
            int code = 0;
            for (int L = 1; L <= 15; L++) {
                code = (code << 1) | z.bits(1);
                int idx = code - first[L];
                if (idx >= 0 && idx < counts[L])
                    return symbols[offs[L] + idx];
            }
            throw new RuntimeException("bad huffman");
        }
    }
}
