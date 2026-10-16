/* BREW AVK codes from the C port's platform.h. */
final class Keys {
    static final int AVK_0 = 0xE021;
    static final int AVK_1 = 0xE022;
    static final int AVK_3 = 0xE024;
    static final int AVK_STAR = 0xE02B;
    static final int AVK_POUND = 0xE02C;
    static final int AVK_CLR = 0xE030;
    static final int AVK_UP = 0xE031;
    static final int AVK_DOWN = 0xE032;
    static final int AVK_LEFT = 0xE033;
    static final int AVK_RIGHT = 0xE034;
    static final int AVK_SELECT = 0xE035;
    static final int AVK_SOFT1 = 0xE036;
    static final int AVK_SOFT2 = 0xE037;

    static int fromMidp(int key) {
        switch (key) {
            case -1: return AVK_UP;
            case -2: return AVK_DOWN;
            case -3: return AVK_LEFT;
            case -4: return AVK_RIGHT;
            case -5: return AVK_SELECT;
            case -6: return AVK_SOFT1;
            case -7: return AVK_SOFT2;
            case 8: return AVK_CLR; /* backspace on some emulators */
            default:
                if (key >= '0' && key <= '9')
                    return AVK_0 + (key - '0');
                if (key == '*') return AVK_STAR;
                if (key == '#') return AVK_POUND;
                return 0;
        }
    }
}
