import javax.microedition.lcdui.Image;

/* Names are the C field names. Image_load ids:
 * imgLogos 0x21 is one strip, three logos, 2 s each (ui_screens.c).
 * imgLogo 0x25 is the menu logo. imgScroll 0x26 is the menu scroll. */
final class MenuSystem {
    static final int MENU_MAIN = 0x14;
    static int menu;
    static int selected;
    static Image[] logos = new Image[3];
    static Image menuBg;
    static Image map;
    static Image logo;
    static Image cursor;
    static Image story;
    static Image intro;
    static Image hudBar;
    static Image hudTopLeft;
    static Image hudTopCenter;
    static Image hudTopRight;
    static Image hudBottomLeft;
    static Image hudBottomCenter;
    static Image hudBottomRight;
    static Image scrollBg;
    static Image scrollStains;
    static Image scrollTears;
    static short[] mapPx;
    static short[] logoPx;
    static short[] cursorPx;
    static int cursorW, cursorH;
    static int mapW, mapH, logoW, logoH;

    static short[] pixels(Image im) {
        if (im == null)
            return null;
        int w = im.getWidth();
        int h = im.getHeight();
        if (w <= 0 || h <= 0)
            return null;
        if (im == map) {
            mapW = w;
            mapH = h;
        }
        if (im == logo) {
            logoW = w;
            logoH = h;
        }
        int[] rgb = new int[w * h];
        im.getRGB(rgb, 0, w, 0, 0, w, h);
        short[] px = new short[w * h];
        for (int i = 0; i < px.length; i++) {
            int p = rgb[i];
            px[i] = (short) Framebuffer.rgb((p >> 16) & 255, (p >> 8) & 255, p & 255);
        }
        return px;
    }
    static int scroll;
    static final String[] ITEMS = {
        "Continue", "New Game", "Options", "Help/About", "More Games"
    };

    static void loadMainImages() {
        Image strip = openBmp("Logos.bmp", "3021.bmp", "33.bmp");
        if (strip != null)
            splitStrip(strip);
        map = openBmp("Main_Menu_Map.bmp", "MenuMap.bmp", "3022.bmp");
        logo = openBmp("OELogo.bmp", "Logo.bmp", "3025.bmp");
        if (logo == null && logos[0] != null)
            logo = logos[0];
        cursor = openBmp("Cursor.bmp", "3008.bmp", "8.bmp");
        story = openBmp("Scroll_Story.bmp", "3027.bmp", "39.bmp");
        intro = openBmp("Intro.bmp", "3020.bmp", "32.bmp");
        hudBar = openBmp("Hud_Bar_Segment.bmp", "3017.bmp", "17.bmp");
        hudTopLeft = openBmp("Hud_Top_Left.bmp", "3018.bmp", "18.bmp");
        hudTopCenter = openBmp("Hud_Top_Center.bmp", "3020.bmp", "20.bmp");
        hudTopRight = openBmp("Hud_Top_Right.bmp", "3019.bmp", "19.bmp");
        hudBottomLeft = openBmp("Hud_Bottom_Left.bmp", "3014.bmp", "14.bmp");
        hudBottomCenter = openBmp("Hud_Bottom_Center.bmp", "3013.bmp", "13.bmp");
        hudBottomRight = openBmp("Hud_Bottom_Right.bmp", "3015.bmp", "15.bmp");
        scrollBg = openBmp("Bg_Scroll.bmp", "3004.bmp", "4.bmp");
        scrollStains = openBmp("Bg_Scroll_Stains.bmp", "3005.bmp", "5.bmp");
        scrollTears = openBmp("Bg_Scroll_Tears.bmp", "3006.bmp", "6.bmp");
        menuBg = logo;
        mapPx = pixels(map);
        logoPx = pixels(logo);
        cursorPx = pixels(cursor);
        if (cursor != null) {
            cursorW = cursor.getWidth();
            cursorH = cursor.getHeight();
        }
    }

    static void splitStrip(Image strip) {
        int w = strip.getWidth();
        int h = strip.getHeight();
        if (w <= 0 || h < 3)
            return;
        int[] rgb = new int[w * h];
        strip.getRGB(rgb, 0, w, 0, 0, w, h);
        int y = 0;
        for (int i = 0; i < 3; i++) {
            int slice = h / 3;
            if (i == 2)
                slice = h - y;
            int[] part = new int[w * slice];
            for (int row = 0; row < slice; row++)
                System.arraycopy(rgb, (y + row) * w, part, row * w, w);
            logos[i] = Image.createRGBImage(part, w, slice, false);
            y += slice;
        }
    }

    static Image openBmp(String a, String b, String c) {
        Image im = open(a);
        if (im == null)
            im = open(b);
        if (im == null)
            im = open(c);
        return im;
    }

    static Image open(String name) {
        byte[] data = read(name);
        if (data == null)
            return null;
        if (data.length > 2 && data[0] == 'B' && data[1] == 'M')
            return fromBmp(data);
        try {
            return Image.createImage(data, 0, data.length);
        } catch (Throwable t) {
            return null;
        }
    }

    static byte[] read(String name) {
        try {
            java.io.InputStream in = App.getResourceAsStream("/" + name);
            if (in == null)
                in = App.getResourceAsStream(name);
            if (in == null)
                return null;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0)
                bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    static Image fromBmp(byte[] bmp) {
        Sprite s = Sprite.fromBmp(bmp, false);
        if (s == null || s.w <= 0 || s.h <= 0)
            return null;
        int[] argb = new int[s.w * s.h];
        for (int i = 0; i < argb.length; i++) {
            int p = s.px[i] & 0xffff;
            int r = (p >> 11) & 31;
            int g = (p >> 5) & 63;
            int b = p & 31;
            argb[i] = 0xff000000 | (r << 19) | (g << 10) | (b << 3);
        }
        return Image.createRGBImage(argb, s.w, s.h, false);
    }
}
