import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

/* Ids 4000-4114 were PMD/QCELP in the BREW bar. Here they are PCM WAV in the jar.
 * Phones that already play wav take audio/x-wav; some want audio/wav, so both are tried. */
final class Sound {
    static final int WAV_FIRST = 4000;
    static final int WAV_LAST = 4114;
    static String last = "silent";
    static boolean enabled = true;
    private static Player effect;
    private static Player music;

    static void setEnabled(boolean on) {
        enabled = on;
        if (!on)
            stop();
    }

    static void playNamed(String name) {
        music = startNamed(music, name, -1);
    }

    private static Player startNamed(Player current, String name, int loops) {
        if (!enabled)
            return current;
        current = close(current);
        InputStream in = App.getResourceAsStream("/" + name);
        if (in == null)
            in = App.getResourceAsStream(name);
        if (in == null) {
            last = "missing " + name;
            return null;
        }
        Player p = open(in, loops);
        last = p == null ? "bad " + name : name;
        return p;
    }

    static void play(int resId) {
        effect = start(effect, resId, 1);
    }

    static void loop(int resId) {
        music = start(music, resId, -1);
    }

    static void stop() {
        effect = close(effect);
        music = close(music);
        last = "stopped";
    }

    private static Player start(Player current, int resId, int loops) {
        if (!enabled)
            return current;
        current = close(current);
        String[] paths = Resource.pathsFor(resId);
        for (int i = 0; i < paths.length; i++) {
            InputStream in = App.getResourceAsStream(paths[i]);
            if (in == null)
                continue;
            Player p = open(in, loops);
            if (p != null) {
                last = paths[i];
                return p;
            }
        }
        last = "missing " + resId + ".wav";
        return null;
    }

    private static Player open(InputStream in, int loops) {
        String[] types = {"audio/x-wav", "audio/wav"};
        for (int i = 0; i < types.length; i++) {
            try {
                Player p = Manager.createPlayer(in, types[i]);
                p.realize();
                p.prefetch();
                if (loops != 1)
                    p.setLoopCount(loops);
                p.start();
                return p;
            } catch (Throwable ignored) {
            }
        }
        try {
            in.close();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Player close(Player p) {
        if (p == null)
            return null;
        try {
            p.stop();
        } catch (Exception ignored) {
        }
        try {
            p.close();
        } catch (Exception ignored) {
        }
        return null;
    }
}
