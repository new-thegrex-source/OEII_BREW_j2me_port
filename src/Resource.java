import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/* Wolf reads chunks with App.getResourceAsStream. Same here: no .bar.
 * Engine still asks by BREW resource id; the file in the jar is that id.
 * .mod is the one binary the BREW build keeps in the module, also a jar entry. */
final class Resource {
    static final String MOD_PATH = "/orcsandelves2.mod";

    static ModData mod;
    static String status = "no data";

    static byte[] readJar(String path) throws java.io.IOException {
        InputStream in = App.getResourceAsStream(path);
        if (in == null)
            return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = in.read(buf)) > 0)
            bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    static void openArchives() throws java.io.IOException {
        byte[] modBytes = readJar("/orcsandelves2.mod");
        if (modBytes == null)
            modBytes = readJar("/res/orcsandelves2.mod");
        if (modBytes == null) {
            status = "orcsandelves2.mod is not in the jar";
            return;
        }
        mod = ModData.open(modBytes);
        status = "mod " + mod.data.length + ", resources in jar";
    }

    /* Resource_open. Unpacked file, not a bar member. Gzip is accepted if a
     * file was left compressed. */
    static byte[] open(int resId) {
        String[] paths = pathsFor(resId);
        for (int i = 0; i < paths.length; i++) {
            try {
                byte[] raw = readJar(paths[i]);
                if (raw == null)
                    continue;
                if (raw.length > 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b)
                    return Inflate.gunzip(raw, 0, raw.length);
                return raw;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /* Jar entries are the bare names (mappings.bin), not 1004_mappings.bin. */
    static String[] pathsFor(int id) {
        String name = knownName(id);
        if (name != null)
            return new String[] { "/" + name, "/" + id + "_" + name, "/" + id + ".bin" };
        if (id >= 2000 && id <= 2013) {
            int n = id - 1999;
            String map = n < 10 ? "m_0" + n + ".bsp" : "m_" + n + ".bsp";
            return new String[] { "/" + map };
        }
        if (id >= 3000 && id <= 3050)
            return new String[] { "/" + id + ".bmp" };
        if (id >= 4000 && id <= 4114)
            return new String[] { "/" + id + ".wav" };
        return new String[] { "/" + id + ".bin" };
    }

    /* Names from the bar extractor notes. Other ids are just the number. */
    static String knownName(int id) {
        switch (id) {
            case 1000: return "entities.bin";
            case 1001: return "sintable.bin";
            case 1002: return "strings.bin";
            case 1003: return "palettes.bin";
            case 1004: return "mappings.bin";
            case 1005: return "bitshapes.bin";
            case 1006: return "stexels.bin";
            case 1007: return "wtexels.bin";
            case 1008: return "ftexels.bin";
            default: return null;
        }
    }

    static int shiftUByte(byte[] b, int[] pos) {
        return b[pos[0]++] & 0xff;
    }

    static int shiftShort(byte[] b, int[] pos) {
        int v = (b[pos[0]] & 0xff) | ((b[pos[0] + 1] & 0xff) << 8);
        pos[0] += 2;
        return v >= 32768 ? v - 65536 : v;
    }

    static int shiftInt(byte[] b, int[] pos) {
        int v = (b[pos[0]] & 0xff) | ((b[pos[0] + 1] & 0xff) << 8)
                | ((b[pos[0] + 2] & 0xff) << 16) | ((b[pos[0] + 3] & 0xff) << 24);
        pos[0] += 4;
        return v;
    }
}
