import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/* Wolf's App.java is the MIDlet. Same shape: one app, one canvas, jar resources. */
public class App extends MIDlet {
    public static App app;
    public static Display display;
    public static Canvas canvas;
    public static Class appClass;

    public App() {
        app = this;
        appClass = getClass();
    }

    public static java.io.InputStream getResourceAsStream(String path) {
        if (path == null || appClass == null)
            return null;
        String slash = path.charAt(0) == '/' ? path : "/" + path;
        String bare = slash.substring(1);
        java.io.InputStream in = appClass.getResourceAsStream(slash);
        if (in == null)
            in = appClass.getResourceAsStream(bare);
        if (in == null)
            in = appClass.getResourceAsStream("/" + bare);
        return in;
    }

    protected void startApp() {
        if (display == null)
            display = Display.getDisplay(this);
        if (canvas == null) {
            canvas = new Canvas();
            canvas.start();
        }
        display.setCurrent(canvas);
    }

    protected void pauseApp() {
        if (canvas != null)
            canvas.stop();
    }

    protected void destroyApp(boolean unconditional) {
        if (canvas != null)
            canvas.stop();
    }
}
