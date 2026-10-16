/* Wolf startup: logos, then the sound yes/no, then the main menu.
 * Missing .mod does not skip that. It is shown after the player passes it. */
final class Game {
    static final int ST_BOOT = 0;
    static final int ST_LOGO = 1;
    static final int ST_SOUND = 2;
    static final int ST_MENU = 3;
    static final int ST_NEED_DATA = 4;
    static final int ST_PLAY = 5;
    static final int ST_STORY = 6;
    static final int ST_CINE = 7;
    static final int ST_DIALOG = 8;
    static final int ST_PAUSE = 9;
    static final int ST_INV = 10;
    static final int ST_MAP = 10;
    static final int ST_CHEAT = 11;
    static final int ST_GATE = 12;
    static final int ST_MAPS = 13;
    static final int ST_STATS = 14;
    static final int ST_DEV = 15;
    static final int ST_DIFF = 16;
    static int cineFrame;
    static int wakeSprite = -1;
    static int wakeFrame;
    static int wakeX, wakeY, wakeAng, wakeDx, wakeDy;
    static int dialogLine;
    static int bannerTicks;
    static int pauseSel;
    static int invPage;
    static int invSel;
    static int cheatSel;
    static int gateSel;
    static int mapSel;
    static int mapCount = 14;
    static int diffSel;
    static int scrollOpen;
    static int cheatCombo;
    static int digitCount;
    static boolean cheatFromMenu;
    static final String[] DIALOG = {
        "Great! The entrance just caved in! Maybe you should join a guild and learn how to step lightly, you're quite heavy-footed for a thief.",
        "I stole you didn't I? Though why I was sent to retrieve you is beyond me. You're more trouble than you're worth!",
        "I suggest you be quiet unless you want to be left here."
    };
    static int[] fallX = new int[12];
    static int[] fallY = new int[12];

    static int state = ST_BOOT;
    static int tick;
    static int logoFrame;
    static int soundChoice;
    static String error = "";
    static String caption = "";
    static final int[] lastKey = new int[8];
    static int nKeys;

    static void boot() {
        try {
            MenuSystem.loadMainImages();
            Text.load();
            Resource.openArchives();
            state = ST_LOGO;
            caption = "logo";
        } catch (Throwable t) {
            state = ST_LOGO;
            error = t.getMessage() == null ? t.toString() : t.getMessage();
        }
    }

    static int weaponFrame;
    static int weaponTicks;
    static int[] mobHp;
    static boolean[] alerted;
    static int[] mobFace;
    static int viewX, viewY, viewAng;
    static int fromX, fromY, fromAng, moveF;
    static int hitSprite = -1;
    static int hitDmg;
    static int hitTicks;
    static int stepCount;

    static void pushKey(int avk) {
        if (avk == 0 || nKeys >= lastKey.length)
            return;
        if (nKeys > 0 && lastKey[nKeys - 1] == avk && isStep(avk))
            return;
        lastKey[nKeys++] = avk;
    }

    static boolean isStep(int avk) {
        return avk == Keys.AVK_UP || avk == Keys.AVK_DOWN || avk == Keys.AVK_LEFT
                || avk == Keys.AVK_RIGHT || avk == Keys.AVK_1 || avk == Keys.AVK_3;
    }

    static void tick40() {
        tick++;
        while (nKeys > 0) {
            int k = lastKey[0];
            System.arraycopy(lastKey, 1, lastKey, 0, --nKeys);
            onKey(k);
        }
        if (state == ST_LOGO) {
            logoFrame++;
            if (logoFrame >= 38 * 3)
                enterSound();
        }
        if (state == ST_CINE && wakeSprite >= 0) {
            cineFrame++;
            int f = cineFrame > 20 ? 20 : cineFrame;
            Player.tileX = wakeX + (wakeDx - wakeX) * f / 20;
            Player.tileY = wakeY + (wakeDy - wakeY) * f / 20;
            Player.angle = wakeAng;
            if (cineFrame == 12)
                world.sflags[wakeSprite] = (world.sflags[wakeSprite] & ~15) | 1;
            if (cineFrame > 24) {
                Player.tileX = wakeDx;
                Player.tileY = wakeDy;
                state = ST_PLAY;
                cineFrame = 0;
            }
            return;
        }
        if (state == ST_CINE) {
            cineFrame++;
            if (bannerTicks > 0) {
                bannerTicks--;
                Player.angle = (world.spawnDir & 3) * 256 + cineFrame * 4;
                for (int i = 0; i < fallX.length; i++)
                    fallY[i] += 6 + (i & 3);
                if (bannerTicks == 0)
                    state = ST_DIALOG;
            } else if (!stepCamera()) {
                bannerTicks = 24;
                cineFrame = 0;
                for (int i = 0; i < fallX.length; i++) {
                    fallX[i] = 20 + i * 18;
                    fallY[i] = -((i * 17) & 63);
                }
            }
        }
        if (moveF < 6)
            moveF++;
        ScriptThread.tick();
        Combat.tick();
    }

    static void enterSound() {
        state = ST_SOUND;
        soundChoice = 1;
        caption = "";
    }

    static void enterMenu() {
        state = ST_MENU;
        MenuSystem.menu = MenuSystem.MENU_MAIN;
        MenuSystem.selected = 0;
        caption = "";
        if (soundChoice == 1)
            Sound.playNamed("Music_Title.wav");
    }

    static void onKey(int avk) {
        if (avk >= Keys.AVK_0 && avk <= Keys.AVK_0 + 9 && state != ST_PLAY && state != ST_CINE && state != ST_DIALOG) {
            if (enterDigit(avk - Keys.AVK_0))
                return;
        }
        if (state == ST_LOGO && (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1)) {
            enterSound();
        } else if (state == ST_SOUND) {
            if (avk == Keys.AVK_UP || avk == Keys.AVK_DOWN)
                soundChoice = 1 - soundChoice;
            else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1) {
                Sound.setEnabled(soundChoice == 1);
                enterMenu();
            }
        } else if (state == ST_DIALOG && (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1)) {
            dialogLine++;
            if (dialogLine >= DIALOG.length) {
                if (dwarfExit) {
                    dwarfExit = false;
                    loadMap(2001);
                } else
                    state = ST_PLAY;
            }
        } else if (state == ST_STORY && (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1 || avk == Keys.AVK_SOFT2)) {
            bannerTicks = 20;
            cineFrame = 0;
            state = ST_CINE;
        } else if (state == ST_DIFF) {
            if (avk == Keys.AVK_UP)
                diffSel = (diffSel + DIFF.length - 1) % DIFF.length;
            else if (avk == Keys.AVK_DOWN)
                diffSel = (diffSel + 1) % DIFF.length;
            else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR)
                enterMenu();
            else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1) {
                Player.difficulty = diffSel == 0 ? 1 : diffSel == 1 ? 2 : 4;
                enterStory();
            }
        } else if (state == ST_MENU) {
            if (avk == Keys.AVK_DOWN)
                MenuSystem.selected = (MenuSystem.selected + 1) % MenuSystem.ITEMS.length;
            else if (avk == Keys.AVK_UP)
                MenuSystem.selected = (MenuSystem.selected + MenuSystem.ITEMS.length - 1) % MenuSystem.ITEMS.length;
            else if (avk == Keys.AVK_SELECT)
                onMenuSelect();
            else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR)
                caption = "exit";
        } else if (state == ST_PLAY) {
            if (avk == Keys.AVK_UP || avk == Keys.AVK_0 + 2)
                tryMove(1);
            else if (avk == Keys.AVK_DOWN || avk == Keys.AVK_0 + 8)
                tryMove(-1);
            else if (avk == Keys.AVK_LEFT || avk == Keys.AVK_0 + 4)
                turn(1);
            else if (avk == Keys.AVK_RIGHT || avk == Keys.AVK_0 + 6)
                turn(-1);
            else if (avk == Keys.AVK_1)
                tryStrafe(-1);
            else if (avk == Keys.AVK_3)
                tryStrafe(1);
            else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_0 + 5)
                swing();
            else if (avk == Keys.AVK_0 + 7 || avk == Keys.AVK_STAR)
                cycleWeapon(1);
            else if (avk == Keys.AVK_0 || avk == Keys.AVK_SOFT1 || avk == Keys.AVK_CLR)
                openPause();
        } else if (state == ST_INV) {
            onInvKey(avk);
        } else if (state == ST_PAUSE) {
            if (avk == Keys.AVK_UP)
                pauseSel = (pauseSel + PAUSE.length - 1) % PAUSE.length;
            else if (avk == Keys.AVK_DOWN)
                pauseSel = (pauseSel + 1) % PAUSE.length;
            else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR)
                state = ST_PLAY;
            else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1)
                onPauseSelect();
        } else if (state == ST_MAP) {
            if (avk == Keys.AVK_UP)
                tryMove(1);
            else if (avk == Keys.AVK_DOWN)
                tryMove(-1);
            else if (avk == Keys.AVK_LEFT)
                turn(1);
            else if (avk == Keys.AVK_RIGHT)
                turn(-1);
            else if (avk == Keys.AVK_1)
                tryStrafe(-1);
            else if (avk == Keys.AVK_3)
                tryStrafe(1);
            else if (avk == Keys.AVK_SOFT1)
                openGate();
            else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR || avk == Keys.AVK_SELECT)
                state = ST_PLAY;
        } else if (state == ST_GATE) {
            if (avk == Keys.AVK_UP)
                gateSel = (gateSel + GATE.length - 1) % GATE.length;
            else if (avk == Keys.AVK_DOWN)
                gateSel = (gateSel + 1) % GATE.length;
            else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR)
                state = ST_PLAY;
            else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1)
                onGateSelect();
        } else if (state == ST_MAPS) {
            if (avk == Keys.AVK_UP)
                mapSel = (mapSel + mapCount - 1) % mapCount;
            else if (avk == Keys.AVK_DOWN)
                mapSel = (mapSel + 1) % mapCount;
            else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR)
                state = ST_GATE;
            else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1)
                loadMap(2000 + mapSel);
        } else if (state == ST_STATS || state == ST_DEV) {
            if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR || avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1)
                state = ST_GATE;
        } else if (state == ST_CHEAT) {
            if (avk == Keys.AVK_UP)
                cheatSel = (cheatSel + CHEATS.length - 1) % CHEATS.length;
            else if (avk == Keys.AVK_DOWN)
                cheatSel = (cheatSel + 1) % CHEATS.length;
            else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR)
                state = ST_GATE;
            else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1)
                onCheatSelect();
        }
    }

    /* Original MenuSystem_enterDigit: first digit is units, so 3666 stores 6663. */
    /* One tile along the same basis the camera uses. Solid walls block. */
    static void tryMove(int dir) {
        int ang = Player.angle & ~255;
        int cos = Render.sinTable[(ang + 256) & 1023];
        int sin = Render.sinTable[ang & 1023];
        int nx = Player.tileX + ((cos * 64 * dir) >> 16);
        int ny = Player.tileY - ((sin * 64 * dir) >> 16);
        if (!blocked(nx, ny)) {
            beginMove(nx, ny, ang);
            stepCount++;
        }
        checkWake();
        chase();
    }

    static void tryStrafe(int dir) {
        int ang = Player.angle & ~255;
        int rx = Render.sinTable[ang & 1023];
        int ry = Render.sinTable[(ang + 256) & 1023];
        int nx = Player.tileX + ((rx * 64 * dir) >> 16);
        int ny = Player.tileY + ((ry * 64 * dir) >> 16);
        if (blocked(nx, ny))
            return;
        beginMove(nx, ny, Player.angle);
        stepCount++;
        checkWake();
        chase();
    }

    static void turn(int dir) {
        int ang = (Player.angle + dir * 256) & 1023;
        beginMove(Player.tileX, Player.tileY, ang);
    }

    static void beginMove(int nx, int ny, int ang) {
        fromX = viewX == 0 ? Player.tileX : viewX;
        fromY = viewY == 0 ? Player.tileY : viewY;
        fromAng = viewAng == 0 ? Player.angle : viewAng;
        Player.tileX = nx;
        Player.tileY = ny;
        Player.angle = ang & 1023;
        moveF = 0;
    }

    static void swing() {
        weaponTicks = 6;
        weaponFrame = 0;
        if (world == null)
            return;
        ensureHp();
        int ang = Player.angle & 1023;
        int fx = Render.sinTable[(ang + 256) & 1023];
        int fy = -Render.sinTable[ang & 1023];
        Entity bestEnt = null;
        int best = -1;
        int bestD = 72 * 72;
        for (int i = 0; i < world.numSprites; i++) {
            Entity e = Entity.findBySprite(i);
            boolean dead = (world.sflags[i] & 0x10000) != 0;
            if (e != null && e.isMonster) {
                if (!e.alive())
                    continue;
                dead = false;
            } else if (dead || (mobHp != null && i < mobHp.length && mobHp[i] <= 0)) {
                continue;
            } else if (!isEnemy(world.stile[i])) {
                continue;
            }
            int dx = (e != null ? e.x : world.sx[i]) - Player.tileX;
            int dy = (e != null ? e.y : world.sy[i]) - Player.tileY;
            int d = dx * dx + dy * dy;
            if (d > bestD)
                continue;
            if (((dx * fx + dy * fy) >> 16) < 16)
                continue;
            best = i;
            bestEnt = e;
            bestD = d;
        }
        if (best < 0) {
            int prop = facingProp();
            if (prop >= 0 && world.stile[prop] == 178) {
                world.sflags[prop] |= 0x10000;
                caption = "Rubble Pile destroyed!";
            } else
                caption = "Miss";
            return;
        }
        int dmg = 4 + Sprites.selected * 2;
        if (bestEnt != null && bestEnt.isMonster) {
            bestEnt.hp -= dmg;
            hitSprite = best;
            hitDmg = dmg;
            hitTicks = 18;
            if (bestEnt.hp <= 0) {
                bestEnt.info |= Entity.INFO_REMOVED;
                world.sflags[best] |= 0x10000;
                caption = "Killed " + (bestEnt.name.length() > 0 ? bestEnt.name : "monster");
            } else
                caption = "Hit " + dmg + " (" + bestEnt.hp + ")";
            return;
        }
        if (mobHp != null && best < mobHp.length)
            mobHp[best] -= dmg;
        hitSprite = best;
        hitDmg = dmg;
        hitTicks = 18;
        if (mobHp != null && best < mobHp.length && mobHp[best] <= 0) {
            world.sflags[best] |= 0x10000;
            caption = "Killed";
        } else
            caption = "Hit " + dmg;
    }

    static boolean blocked(int x, int y) {
        if (Player.noclip != 0 || world == null)
            return false;
        int tx = x >> 6;
        int ty = y >> 6;
        if (tx < 0 || ty < 0 || tx > 31 || ty > 31)
            return true;
        for (int i = 0; i < world.numLines; i++) {
            int flags = world.flags[i];
            if ((flags & 1) != 0 || (flags & 0x4000000) != 0)
                continue;
            if (nearSeg(world.x1[i], world.y1[i], world.x2[i], world.y2[i], x, y, 20))
                return true;
        }
        return false;
    }

    static boolean nearSeg(int x1, int y1, int x2, int y2, int px, int py, int rad) {
        int vx = x2 - x1;
        int vy = y2 - y1;
        int len2 = vx * vx + vy * vy;
        if (len2 < 1)
            return false;
        int t = ((px - x1) * vx + (py - y1) * vy);
        if (t < 0)
            t = 0;
        else if (t > len2)
            t = len2;
        int cx = x1 + (int) ((long) vx * t / len2);
        int cy = y1 + (int) ((long) vy * t / len2);
        int dx = px - cx;
        int dy = py - cy;
        return dx * dx + dy * dy < rad * rad;
    }

    static void strafe(int dir) {
        tryStrafe(dir);
    }

    static boolean enterDigit(int digit) {
        int scale = 1;
        for (int i = 0; i < digitCount; i++)
            scale *= 10;
        cheatCombo += scale * digit;
        digitCount++;
        if (cheatCombo == 6663) {
            cheatCombo = 0;
            digitCount = 0;
            openGate();
            return true;
        }
        if (digitCount >= 4) {
            cheatCombo = 0;
            digitCount = 0;
        }
        return false;
    }

    static final String[] GATE = {
        "Resume Game", "Back", "Cheats", "Change Map", "Stats", "Developer"
    };

    static void openGate() {
        gateSel = 0;
        state = ST_GATE;
    }

    static void onGateSelect() {
        String item = GATE[gateSel];
        if ("Resume Game".equals(item) || "Back".equals(item))
            state = ST_PLAY;
        else if ("Cheats".equals(item)) {
            cheatSel = 0;
            state = ST_CHEAT;
        } else if ("Change Map".equals(item)) {
            mapCount = 14;
            if (mapSel < 0 || mapSel >= mapCount)
                mapSel = 0;
            state = ST_MAPS;
        } else if ("Stats".equals(item))
            state = ST_STATS;
        else if ("Developer".equals(item))
            state = ST_DEV;
    }

    static void loadMap(int id) {
        byte[] map = Resource.open(id);
        if (map == null) {
            caption = "no map " + id;
            return;
        }
        world = BspMap.load(map);
        if (world.numLines == 0) {
            caption = world.error.length() == 0 ? "map empty" : world.error;
            return;
        }
        Mappings.load();
        Palettes.load();
        Tex.load();
        Sprites.load();
        Entity.spawnFromMap(world);
        Player.tileX = world.spawnX();
        Player.tileY = world.spawnY();
        Player.angle = world.spawnAngle();
        viewX = fromX = Player.tileX;
        viewY = fromY = Player.tileY;
        viewAng = fromAng = Player.angle;
        weaponFrame = 0;
        weaponTicks = 0;
        stepCount = 0;
        mapId = id;
        caption = mapName(id);
        state = ST_PLAY;
    }

    static final String[] CHEATS = {
        "Back",
        "No Clip",
        "Disable AI",
        "Level Up",
        "God Mode",
        "Give Everything",
        "Reveal Map",
        "Give Gold",
        "Give Ammo"
    };

    static void onCheatSelect() {
        switch (cheatSel) {
            case 0:
                state = ST_GATE;
                break;
            case 1:
                Player.noclip = 1 - Player.noclip;
                break;
            case 2:
                Player.disableAI = 1 - Player.disableAI;
                break;
            case 3:
                Player.level++;
                Player.hpMax += 5;
                Player.hp = Player.hpMax;
                break;
            case 4:
                Player.god = 1 - Player.god;
                if (Player.god != 0)
                    Player.hp = Player.hpMax < 1 ? 100 : Player.hpMax;
                break;
            case 5:
                Player.god = 1;
                Player.noclip = 1;
                Player.level = 10;
                Player.gold += 1000;
                Player.hpMax = 100;
                Player.hp = 100;
                break;
            case 6:
                state = ST_MAP;
                break;
            case 7:
                Player.gold += 1000;
                break;
            case 8:
                Player.gold += 10;
                break;
            default:
                break;
        }
    }

    static void onMenuSelect() {
        String item = MenuSystem.ITEMS[MenuSystem.selected];
        caption = item;
        if ("New Game".equals(item))
            enterDifficulty();
        else if ("Continue".equals(item))
            beginLevel();
    }

    static final String[] DIFF = { "Normal", "Difficult", "Nightmare" };

    static void enterDifficulty() {
        Sound.stop();
        diffSel = 0;
        scrollOpen = 0;
        state = ST_DIFF;
        caption = "";
    }

    static void enterStory() {
        Sound.stop();
        scrollOpen = 0;
        state = ST_STORY;
        caption = "";
    }

    static void beginLevel() {
        startGame();
    }
    static void startGame() {
        Player.hp = 100;
        Player.hpMax = 100;
        Player.level = 1;
        byte[] map = null;
        try {
            map = Resource.readJar("/m_01.bsp");
        } catch (Exception ignored) {
        }
        if (map == null)
            map = Resource.open(2000);
        if (map == null) {
            state = ST_MENU;
            caption = "no m_01.bsp in jar";
            return;
        }
        world = BspMap.load(map);
        Mappings.load();
        Palettes.load();
        Tex.load();
        Sprites.load();
        Entity.spawnFromMap(world);
        if (world.numLines == 0) {
            state = ST_MENU;
            caption = world.error.length() == 0 ? "map has no walls" : world.error;
            return;
        }
        Sound.stop();
        Player.tileX = world.spawnX();
        Player.tileY = world.spawnY();
        Player.angle = world.spawnAngle();
        viewX = fromX = Player.tileX;
        viewY = fromY = Player.tileY;
        viewAng = fromAng = Player.angle;
        mapId = 2000;
        weaponFrame = 0;
        weaponTicks = 0;
        stepCount = 0;
        cineFrame = 0;
        dialogLine = 0;
        bannerTicks = 0;
        state = ST_STORY;
        caption = world.numKeys == 0 ? "no camera keys" : "keys " + world.numKeys;
    }

    static void openPause() {
        Sound.stop();
        pauseSel = 0;
        scrollOpen = 0;
        state = ST_PAUSE;
    }

    static final String[] PAUSE = {
        "Resume Game", "Inventory", "Quest Log", "Save Game", "Load Game",
        "View Map", "Status", "Help/About", "Options", "Restart Level",
        "Save & Quit", "Main Menu"
    };

    static void onPauseSelect() {
        String item = PAUSE[pauseSel];
        if ("Resume Game".equals(item))
            state = ST_PLAY;
        else if ("Save Game".equals(item)) {
            caption = saveGame() ? "Saved" : "Save failed";
            state = ST_PLAY;
        } else if ("Load Game".equals(item)) {
            caption = loadGame() ? "Loaded" : "No save";
        } else if ("Save & Quit".equals(item)) {
            saveGame();
            enterMenu();
        } else if ("Main Menu".equals(item))
            enterMenu();
        else if ("Restart Level".equals(item)) {
            if (mapId >= 2000)
                loadMap(mapId);
            else
                beginLevel();
        }
        else if ("Inventory".equals(item)) {
            invPage = 0;
            invSel = 0;
            state = ST_INV;
        }
        else if ("View Map".equals(item)) {
            scrollOpen = Framebuffer.H;
            state = ST_MAP;
        }
        else
            caption = item;
    }

    static final String[] INV_HUB = { "Weapons", "Potions", "Rings", "Other" };
    static final String[] INV_WEAPONS = { "Talking Wand", "Magic Dagger", "Guiding Staff" };
    static final int[] INV_WID = { 0, 1, 2 };

    static void onInvKey(int avk) {
        int n = invCount();
        if (avk == Keys.AVK_UP)
            invSel = (invSel + n - 1) % n;
        else if (avk == Keys.AVK_DOWN)
            invSel = (invSel + 1) % n;
        else if (avk == Keys.AVK_SOFT2 || avk == Keys.AVK_CLR) {
            if (invPage == 0)
                state = ST_PAUSE;
            else {
                invPage = 0;
                invSel = 0;
            }
        } else if (avk == Keys.AVK_SELECT || avk == Keys.AVK_SOFT1) {
            if (invPage == 0) {
                invPage = invSel + 1;
                invSel = 0;
            } else if (invPage == 1) {
                Sprites.selectWeapon(INV_WID[invSel]);
                caption = INV_WEAPONS[invSel];
            }
        }
    }

    static int invCount() {
        if (invPage == 0)
            return INV_HUB.length;
        if (invPage == 1)
            return INV_WEAPONS.length;
        if (invPage == 2)
            return 1;
        if (invPage == 3)
            return 1;
        return 2;
    }

    static void paintInv(Framebuffer fb) {
        paintPlay(fb);
        int y = 28;
        int h = Framebuffer.H - 16 - 40;
        drawScroll(fb, 4, y, Framebuffer.W - 8, h);
        Text.draw(fb, "100 / 100", 16, y + 8, false);
        Text.draw(fb, "10 / 10", Framebuffer.W - 70, y + 8, false);
        String title = invPage == 0 ? "Inventory" : INV_HUB[invPage - 1];
        Text.draw(fb, title, (Framebuffer.W - Text.width(title, false)) / 2, y + 24, false);
        int row = y + 44;
        int n = invCount();
        for (int i = 0; i < n; i++) {
            if (i == invSel && MenuSystem.cursorPx != null)
                blitCachedAt(fb, MenuSystem.cursorPx, MenuSystem.cursorW, MenuSystem.cursorH, 14, row);
            String line = invLine(i);
            Text.draw(fb, line, 28, row, false);
            row += 16;
        }
    }

    static String invLine(int i) {
        if (invPage == 0)
            return INV_HUB[i];
        if (invPage == 1) {
            String mark = Sprites.selected == INV_WID[i] ? "> " : "  ";
            return mark + INV_WEAPONS[i];
        }
        if (invPage == 2)
            return "No potions";
        if (invPage == 3)
            return "No ring";
        return i == 0 ? "Leather Armor" : "Lockpick 1";
    }

    static void paintPause(Framebuffer fb) {
        paintPlay(fb);
        if (scrollOpen < Framebuffer.H - 16)
            scrollOpen += 28;
        int h = scrollOpen;
        if (h > Framebuffer.H - 16)
            h = Framebuffer.H - 16;
        int y = (Framebuffer.H - 16 - h) / 2;
        drawScroll(fb, 0, y, Framebuffer.W, h);
        if (h < 80)
            return;
        int ty = y + 28;
        int first = pauseSel > 8 ? pauseSel - 8 : 0;
        for (int i = first; i < PAUSE.length && ty < y + h - 16; i++) {
            if (i == pauseSel && MenuSystem.cursorPx != null)
                blitCachedAt(fb, MenuSystem.cursorPx, MenuSystem.cursorW, MenuSystem.cursorH, 16, ty);
            Text.draw(fb, PAUSE[i], 28, ty, false);
            ty += 14;
        }
        paintHudBar(fb, Framebuffer.H - 16);
        Text.draw(fb, "Back", 4, Framebuffer.H - 13, false);
        Text.draw(fb, "Select", Framebuffer.W - Text.width("Select", false) - 4, Framebuffer.H - 13, false);
    }

    static void paintDifficulty(Framebuffer fb) {
        if (scrollOpen < Framebuffer.H - 16)
            scrollOpen += 28;
        int h = scrollOpen > Framebuffer.H - 16 ? Framebuffer.H - 16 : scrollOpen;
        fb.clear(0);
        int y = (Framebuffer.H - 16 - h) / 2;
        drawScroll(fb, 0, y, Framebuffer.W, h);
        if (h > 70) {
            String title = "Difficulty";
            Text.draw(fb, title, (Framebuffer.W - Text.width(title, false)) / 2, y + 28, false);
            int ty = y + 56;
            for (int i = 0; i < DIFF.length; i++) {
                if (i == diffSel && MenuSystem.cursorPx != null)
                    blitCachedAt(fb, MenuSystem.cursorPx, MenuSystem.cursorW, MenuSystem.cursorH, 48, ty);
                Text.draw(fb, DIFF[i], 64, ty, false);
                ty += 18;
            }
        }
        paintHudBar(fb, Framebuffer.H - 16);
        Text.draw(fb, "Back", 4, Framebuffer.H - 13, false);
        Text.draw(fb, "Select", Framebuffer.W - Text.width("Select", false) - 4, Framebuffer.H - 13, false);
    }

    /* Canvas_drawDialogFrame: Bg_Scroll is a 120x26 sheet of 60x13 corners,
     * stretched to the box. Stains and tears are regions, not whole sheets. */
    static void drawScroll(Framebuffer fb, int x, int y, int w, int h) {
        int paper = Framebuffer.rgb(168, 150, 126);
        if (w < 8 || h < 8)
            return;
        fb.fillRect(x, y, w, h, paper);
        javax.microedition.lcdui.Image frame = MenuSystem.scrollBg;
        if (frame == null || frame.getWidth() < 120 || frame.getHeight() < 26) {
            fb.fillRect(x, y, w, 3, Framebuffer.rgb(92, 64, 36));
            fb.fillRect(x, y + h - 3, w, 3, Framebuffer.rgb(92, 64, 36));
            return;
        }
        int right = x + w - 60;
        int bottom = y + h - 13;
        if (right < x + 60)
            right = x + 60;
        if (bottom < y + 13)
            bottom = y + 13;
        blitRegion(fb, frame, 0, 0, 60, 13, x, y);
        blitRegion(fb, frame, 60, 0, 60, 13, right, y);
        blitRegion(fb, frame, 0, 13, 60, 13, x, bottom);
        blitRegion(fb, frame, 60, 13, 60, 13, right, bottom);
        for (int i = 0; i < h - 26; i++) {
            int row = y + 13 + i;
            blitRegion(fb, frame, 0, 13, 60, 1, x, row);
            blitRegion(fb, frame, 60, 13, 60, 1, right, row);
        }
        for (int i = 0; i < w - 120; i++) {
            int col = x + 60 + i;
            blitRegion(fb, frame, 60, 0, 1, 13, col, y);
            blitRegion(fb, frame, 60, 13, 1, 13, col, bottom);
        }
        if (w > 120 && h > 26)
            fb.fillRect(x + 60, y + 13, w - 120, h - 26, paper);
        if (w > 160 && h > 160)
            drawScrollWear(fb, x, y, w, h);
    }

    static void drawScrollFrame(Framebuffer fb, int x, int y, int w, int h) {
        int paper = Framebuffer.rgb(168, 150, 126);
        fb.fillRect(x, y, w, h, paper);
        javax.microedition.lcdui.Image frame = MenuSystem.scrollBg;
        if (frame == null || frame.getWidth() < 120 || frame.getHeight() < 26)
            return;
        int right = x + w - 60;
        int bottom = y + h - 13;
        if (right < x + 60)
            right = x + 60;
        if (bottom < y + 13)
            bottom = y + 13;
        blitRegion(fb, frame, 0, 0, 60, 13, x, y);
        blitRegion(fb, frame, 60, 0, 60, 13, right, y);
        blitRegion(fb, frame, 0, 13, 60, 13, x, bottom);
        blitRegion(fb, frame, 60, 13, 60, 13, right, bottom);
        for (int i = 0; i < h - 26; i++) {
            int row = y + 13 + i;
            blitRegion(fb, frame, 0, 13, 60, 1, x, row);
            blitRegion(fb, frame, 60, 13, 60, 1, right, row);
        }
        for (int i = 0; i < w - 120; i++) {
            int col = x + 60 + i;
            blitRegion(fb, frame, 60, 0, 1, 13, col, y);
            blitRegion(fb, frame, 60, 13, 1, 13, col, bottom);
        }
        if (w > 120 && h > 26)
            fb.fillRect(x + 60, y + 13, w - 120, h - 26, paper);
    }

    static void drawScrollWear(Framebuffer fb, int x, int y, int w, int h) {
        javax.microedition.lcdui.Image stains = MenuSystem.scrollStains;
        javax.microedition.lcdui.Image tears = MenuSystem.scrollTears;
        if (stains != null && stains.getWidth() >= 93 && stains.getHeight() >= 67) {
            blitRegion(fb, stains, 0, 0, 59, 45, x + 26, y + 38);
            blitRegion(fb, stains, 60, 0, 43, 38, x + w - 68, y + 100);
            blitRegion(fb, stains, 0, 46, 43, 6, x + 108, y + 56);
            blitRegion(fb, stains, 60, 39, 33, 28, x + 40, y + 104);
        }
        if (tears != null && tears.getWidth() >= 57 && tears.getHeight() >= 28) {
            blitRegion(fb, tears, 0, 0, 13, 29, x + 158, y + 43);
            blitRegion(fb, tears, 14, 0, 12, 13, x + 159, y + 85);
            blitRegion(fb, tears, 14, 14, 12, 12, x + 7, y + 50);
            blitRegion(fb, tears, 27, 0, 6, 18, x + 165, y + 113);
            blitRegion(fb, tears, 34, 0, 10, 19, x + 7, y + h - 80);
            blitRegion(fb, tears, 45, 0, 12, 28, x + 7, y + h - 65);
        }
    }

    static boolean stepCamera() {
        if (world == null || world.numKeys == 0) {
            Player.angle = Player.angle & ~255;
            return cineFrame < 90;
        }
        int t = cineFrame * 40;
        int at = 0;
        for (int i = 0; i < world.numKeys; i++) {
            int dur = world.kdur[i];
            if (t < at + dur) {
                int next = i + 1 < world.numKeys ? i + 1 : i;
                int f = (t - at) * 256 / dur;
                Player.tileX = world.kx[i] + (world.kx[next] - world.kx[i]) * f / 256;
                Player.tileY = world.ky[i] + (world.ky[next] - world.ky[i]) * f / 256;
                Player.angle = (world.kang[i] + 128) & ~255;
                return true;
            }
            at += dur;
        }
        return false;
    }

    static BspMap world;

    static void draw(Framebuffer fb) {
        fb.clear(Framebuffer.rgb(8, 8, 12));
        if (state == ST_MENU) {
            MenuSystem.scroll++;
            paintMenu(fb);
        } else if (state == ST_SOUND) {
            paintSound(fb);
        } else if (state == ST_STORY) {
            paintPlay(fb);
            paintIntro(fb);
        } else if (state == ST_DIFF) {
            paintDifficulty(fb);
        } else if (state == ST_PAUSE) {
            paintPause(fb);
        } else if (state == ST_INV) {
            paintInv(fb);
        } else if (state == ST_MAP) {
            paintMap(fb);
        } else if (state == ST_GATE) {
            paintGate(fb);
        } else if (state == ST_MAPS) {
            paintMapList(fb);
        } else if (state == ST_STATS) {
            paintStats(fb);
        } else if (state == ST_DEV) {
            paintDev(fb);
        } else if (state == ST_CHEAT) {
            paintCheat(fb);
        } else if (state == ST_CINE || state == ST_DIALOG || state == ST_PLAY) {
            paintPlay(fb);
            if (bannerTicks > 0) {
                int shut = (24 - bannerTicks) * TinyGL.VIEW_H / 24;
                fb.fillRect(40, TinyGL.VIEW_TOP + TinyGL.VIEW_H / 2 - shut / 2, Framebuffer.W - 80, shut, Framebuffer.rgb(48, 40, 32));
                for (int i = 0; i < fallX.length; i++) {
                    int ry = TinyGL.VIEW_TOP + (fallY[i] & 127);
                    fb.fillRect(fallX[i], ry, 6, 6, Framebuffer.rgb(90, 80, 70));
                    fb.fillRect(fallX[i] + 8, ry + 10, 4, 4, Framebuffer.rgb(60, 54, 46));
                }
                paintBanner(fb, "CAVE IN!");
            }
            else if (state == ST_DIALOG)
                paintDialog(fb);
            if (state == ST_PLAY) {
                paintExamine(fb);
                paintTarget(fb);
                checkDwarf();
            }
            paintHit(fb);
        }
    }

    static void paintCheat(Framebuffer fb) {
        if (cheatFromMenu)
            paintMenu(fb);
        else {
            paintPlay(fb);
            blitHud(fb, MenuSystem.scrollBg, 8, 16);
            blitHud(fb, MenuSystem.scrollStains, 8, 16);
            blitHud(fb, MenuSystem.scrollTears, 8, 16);
        }
        int y = 36;
        int first = cheatSel > 8 ? cheatSel - 8 : 0;
        for (int i = first; i < CHEATS.length && y < 188; i++) {
            String line = CHEATS[i];
            if (i == 1)
                line = line + (Player.noclip != 0 ? ": On" : ": Off");
            else if (i == 2)
                line = line + (Player.disableAI != 0 ? ": On" : ": Off");
            else if (i == 4)
                line = line + (Player.god != 0 ? ": On" : ": Off");
            else if (i == 3)
                line = line + " " + Player.level;
            if (i == cheatSel && MenuSystem.cursorPx != null)
                blitCachedAt(fb, MenuSystem.cursorPx, MenuSystem.cursorW, MenuSystem.cursorH, 12, y);
            Text.draw(fb, line, 24, y, false);
            y += 14;
        }
        if (!cheatFromMenu) {
            paintHudBar(fb, Framebuffer.H - 16);
            Text.draw(fb, "Back", 4, Framebuffer.H - 13, false);
            Text.draw(fb, "Select", Framebuffer.W - Text.width("Select", false) - 4, Framebuffer.H - 13, false);
        }
    }

    static void paintGate(Framebuffer fb) {
        paintPlay(fb);
        paintList(fb, GATE, gateSel, "Back", "Select");
    }

    /* 2000.bsp .. 2013.bsp, story maps then the BREW developer maps. */
    static final String[] MAP_NAMES = {
        "Caverns", "Dwarkar", "Tunnels", "El' Landra", "Dark Forest", "Sol", "Darkfel",
        "Ben", "Brett", "Inventory", "Jah", "Karina", "Matt", "Monster"
    };
    static int mapId = 2000;

    static String mapName(int id) {
        int i = id - 2000;
        if (i >= 0 && i < MAP_NAMES.length)
            return MAP_NAMES[i];
        return "Map " + id;
    }

    static void cycleWeapon(int dir) {
        Sprites.cycleWeapon(dir);
        caption = Sprites.weaponName();
    }

    static boolean saveGame() {
        try {
            byte[] rec = new byte[32];
            putI(rec, 0, 0x4f453247);
            putI(rec, 4, mapId);
            putI(rec, 8, Player.tileX);
            putI(rec, 12, Player.tileY);
            putI(rec, 16, Player.angle);
            rec[20] = (byte) Player.hp;
            rec[21] = (byte) Player.hpMax;
            rec[22] = (byte) Player.level;
            rec[23] = (byte) Sprites.selected;
            putI(rec, 24, Player.gold);
            putI(rec, 28, Player.xp);
            SaveStore.write("SD_0", rec);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean loadGame() {
        byte[] rec = SaveStore.read("SD_0");
        if (rec == null || rec.length < 32 || getI(rec, 0) != 0x4f453247)
            return false;
        int id = getI(rec, 4);
        loadMap(id);
        if (world == null || world.numLines == 0)
            return false;
        Player.tileX = getI(rec, 8);
        Player.tileY = getI(rec, 12);
        Player.angle = getI(rec, 16) & 1023;
        Player.hp = rec[20] & 0xff;
        Player.hpMax = rec[21] & 0xff;
        if (Player.hpMax < 1)
            Player.hpMax = 100;
        Player.level = rec[22] & 0xff;
        Sprites.selectWeapon(rec[23] & 0xff);
        Player.gold = getI(rec, 24);
        Player.xp = getI(rec, 28);
        caption = "Loaded " + mapName(id);
        state = ST_PLAY;
        return true;
    }

    static void putI(byte[] b, int p, int v) {
        b[p] = (byte) v;
        b[p + 1] = (byte) (v >> 8);
        b[p + 2] = (byte) (v >> 16);
        b[p + 3] = (byte) (v >> 24);
    }

    static int getI(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 24);
    }

    static void paintMapList(Framebuffer fb) {
        paintPlay(fb);
        String[] names = new String[mapCount];
        for (int i = 0; i < mapCount; i++)
            names[i] = mapName(2000 + i);
        paintList(fb, names, mapSel, "Back", "Select");
    }

    static void paintStats(Framebuffer fb) {
        paintPlay(fb);
        paintList(fb, new String[] {
            "HP " + Player.hp + "/" + Player.hpMax,
            "Level " + Player.level,
            "Gold " + Player.gold,
            "Back"
        }, 3, "Back", "Select");
    }

    static void paintDev(Framebuffer fb) {
        paintPlay(fb);
        int wand = Sprites.weaponCount > 0 ? Sprites.weaponStex[Sprites.selected] : 0;
        paintList(fb, new String[] {
            "walls " + (world == null ? 0 : world.numLines),
            "fmt " + Tex.diagMode + " pal " + Tex.diagPal + " ofs " + Tex.diagOfs,
            "raw " + Tex.diagRaw + " pack " + Tex.diagPacked,
            Sprites.weaponName() + " stex " + wand,
            "xy " + (Player.tileX >> 6) + "," + (Player.tileY >> 6),
            "Back"
        }, 4, "Back", "Select");
    }

    static void paintList(Framebuffer fb, String[] items, int sel, String left, String right) {
        int y = 28;
        int first = sel > 6 ? sel - 6 : 0;
        for (int i = first; i < items.length && y < 200; i++) {
            if (i == sel)
                fb.fillRect(16, y - 1, 140, 13, Framebuffer.rgb(180, 160, 48));
            Text.draw(fb, items[i], 22, y, false);
            y += 16;
        }
        paintHudBar(fb, Framebuffer.H - 16);
        Text.draw(fb, left, 4, Framebuffer.H - 13, false);
        Text.draw(fb, right, Framebuffer.W - Text.width(right, false) - 4, Framebuffer.H - 13, false);
    }

    static void paintMap(Framebuffer fb) {
        fb.clear(0);
        int soft = Framebuffer.H - 16;
        /* Parchment only. Tears are sheet regions; blitting them here looked like walls. */
        drawScrollFrame(fb, 0, 0, Framebuffer.W, soft);
        int x0 = 14;
        int y0 = 16;
        int side = Framebuffer.W - 28;
        if (side > soft - 32)
            side = soft - 32;
        int ink = Framebuffer.rgb(74, 62, 42);
        int door = Framebuffer.rgb(140, 48, 36);
        int grid = Framebuffer.rgb(150, 132, 108);
        for (int i = 0; i <= 32; i += 4) {
            int g = x0 + i * side / 32;
            fb.fillRect(g, y0, 1, side, grid);
            fb.fillRect(x0, y0 + i * side / 32, side, 1, grid);
        }
        if (world != null) {
            for (int i = 0; i < world.numLines; i++) {
                int ax = x0 + world.x1[i] * side / 2048;
                int ay = y0 + world.y1[i] * side / 2048;
                int bx = x0 + world.x2[i] * side / 2048;
                int by = y0 + world.y2[i] * side / 2048;
                int col = (world.flags[i] & 8) != 0 ? door : ink;
                drawMapLine(fb, ax, ay, bx, by, col);
            }
            int px = x0 + Player.tileX * side / 2048;
            int py = y0 + Player.tileY * side / 2048;
            paintArrow(fb, px, py, Player.angle);
        } else {
            Text.draw(fb, "no map", 24, 40, false);
        }
        paintHudBar(fb, Framebuffer.H - 16);
        Text.draw(fb, "Menu", 4, Framebuffer.H - 13, false);
        Text.draw(fb, "Leave", Framebuffer.W - Text.width("Leave", false) - 4, Framebuffer.H - 13, false);
    }

    static void drawMapLine(Framebuffer fb, int x0, int y0, int x1, int y1, int rgb) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.abs(dx) > Math.abs(dy) ? Math.abs(dx) : Math.abs(dy);
        if (steps < 1)
            steps = 1;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + dx * i / steps;
            int y = y0 + dy * i / steps;
            if (x >= 0 && y >= 0 && x < Framebuffer.W && y < Framebuffer.H - 16)
                fb.px[y * Framebuffer.W + x] = (short) rgb;
        }
    }

    static boolean blockedStep(int ax, int ay, int bx, int by) {
        int mx = (ax + bx) / 2;
        int my = (ay + by) / 2;
        for (int i = 0; i < world.numLines; i++) {
            if (segsCross(ax, ay, bx, by, world.x1[i], world.y1[i], world.x2[i], world.y2[i]))
                return true;
            if (nearSeg(world.x1[i], world.y1[i], world.x2[i], world.y2[i], mx, my, 12))
                return true;
        }
        return false;
    }

    static boolean wallBetween(int x0, int y0, int x1, int y1) {
        int ax = x0 * 64 + 32;
        int ay = y0 * 64 + 32;
        int bx = x1 * 64 + 32;
        int by = y1 * 64 + 32;
        int mx = (ax + bx) / 2;
        int my = (ay + by) / 2;
        for (int i = 0; i < world.numLines; i++) {
            if (segsCross(ax, ay, bx, by, world.x1[i], world.y1[i], world.x2[i], world.y2[i]))
                return true;
            if (nearSeg(world.x1[i], world.y1[i], world.x2[i], world.y2[i], mx, my, 28))
                return true;
        }
        return false;
    }

    static boolean segsCross(int ax, int ay, int bx, int by, int cx, int cy, int dx, int dy) {
        int d1 = cross(bx - ax, by - ay, cx - ax, cy - ay);
        int d2 = cross(bx - ax, by - ay, dx - ax, dy - ay);
        int d3 = cross(dx - cx, dy - cy, ax - cx, ay - cy);
        int d4 = cross(dx - cx, dy - cy, bx - cx, by - cy);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    static int cross(int ax, int ay, int bx, int by) {
        return ax * by - ay * bx;
    }

    static void paintArrow(Framebuffer fb, int x, int y, int ang) {
        int cos = Render.sinTable[(ang + 256) & 1023];
        int sin = Render.sinTable[ang & 1023];
        int fx = (cos * 8) >> 16;
        int fy = -((sin * 8) >> 16);
        int rx = (sin * 5) >> 16;
        int ry = (cos * 5) >> 16;
        int ink = Framebuffer.rgb(236, 214, 96);
        fillTri(fb, x + fx, y + fy, x - fx + rx, y - fy + ry, x - fx - rx, y - fy - ry, ink);
    }

    static void fillTri(Framebuffer fb, int x0, int y0, int x1, int y1, int x2, int y2, int rgb) {
        int minY = y0 < y1 ? y0 : y1;
        if (y2 < minY)
            minY = y2;
        int maxY = y0 > y1 ? y0 : y1;
        if (y2 > maxY)
            maxY = y2;
        if (minY < 8)
            minY = 8;
        if (maxY > Framebuffer.H - 18)
            maxY = Framebuffer.H - 18;
        for (int y = minY; y <= maxY; y++) {
            int xa = edgeX(x0, y0, x1, y1, y);
            int xb = edgeX(x1, y1, x2, y2, y);
            int xc = edgeX(x2, y2, x0, y0, y);
            int left = xa;
            int right = xa;
            if (spanHits(y0, y1, y) ) {
                if (xa < left) left = xa;
                if (xa > right) right = xa;
            } else {
                left = 9999;
                right = -9999;
            }
            if (spanHits(y1, y2, y)) {
                if (xb < left) left = xb;
                if (xb > right) right = xb;
            }
            if (spanHits(y2, y0, y)) {
                if (xc < left) left = xc;
                if (xc > right) right = xc;
            }
            if (left > right)
                continue;
            if (left < 8)
                left = 8;
            if (right > Framebuffer.W - 8)
                right = Framebuffer.W - 8;
            fb.fillRect(left, y, right - left + 1, 1, rgb);
        }
    }

    static boolean spanHits(int y0, int y1, int y) {
        return y >= (y0 < y1 ? y0 : y1) && y <= (y0 > y1 ? y0 : y1);
    }

    static int edgeX(int x0, int y0, int x1, int y1, int y) {
        if (y0 == y1)
            return x0;
        return x0 + (x1 - x0) * (y - y0) / (y1 - y0);
    }

    static void paintIntro(Framebuffer fb) {
        int y = Framebuffer.H - 16 - 62;
        int h = 62;
        int head = 16;
        int edge = Framebuffer.rgb(230, 230, 230);
        fb.fillRect(0, y, Framebuffer.W, h, 0);
        fb.fillRect(0, y, Framebuffer.W, 1, edge);
        fb.fillRect(0, y + h - 1, Framebuffer.W, 1, edge);
        fb.fillRect(0, y, 1, h, edge);
        fb.fillRect(Framebuffer.W - 1, y, 1, h, edge);
        fb.fillRect(1, y + 1, Framebuffer.W - 2, head, Framebuffer.rgb(118, 118, 118));
        fb.fillRect(1, y + head, Framebuffer.W - 2, 1, edge);
        Text.draw(fb, "INTRODUCTION", (Framebuffer.W - Text.width("INTRODUCTION", false)) / 2, y + 2, false);
        String body = "You possess a talking wand, a magic dagger, a guiding staff, and basic leather armor.";
        wrap(fb, body, 8, y + head + 4, 36, y + h - 4);
    }

    static void paintDialog(Framebuffer fb) {
        String line = DIALOG[dialogLine];
        if (isBanner(line)) {
            paintBanner(fb, line);
            return;
        }
        boolean wand = dialogLine == 0;
        int boxW = Framebuffer.W - 16;
        int boxH = wand ? 52 : 58;
        int boxX = 8;
        int boxY = Framebuffer.H - 18 - boxH - 36;
        int fill = wand ? Framebuffer.rgb(150, 48, 196) : Framebuffer.rgb(28, 92, 196);
        int edge = Framebuffer.rgb(236, 236, 236);
        fb.fillRect(boxX, boxY, boxW, boxH, fill);
        fb.fillRect(boxX, boxY, boxW, 1, edge);
        fb.fillRect(boxX, boxY + boxH - 1, boxW, 1, edge);
        fb.fillRect(boxX, boxY, 1, boxH, edge);
        fb.fillRect(boxX + boxW - 1, boxY, 1, boxH, edge);
        int tailX = wand ? boxX + boxW - 36 : boxX + boxW / 2 - 6;
        fb.fillRect(tailX, boxY + boxH, 10, 8, fill);
        fb.fillRect(tailX + 2, boxY + boxH + 8, 6, 6, fill);
        int textX = boxX + 8;
        int max = 34;
        if (!wand) {
            fb.fillRect(boxX + 6, boxY + 8, 20, 22, Framebuffer.rgb(40, 70, 40));
            fb.fillRect(boxX + 10, boxY + 12, 4, 4, Framebuffer.rgb(220, 210, 160));
            fb.fillRect(boxX + 16, boxY + 12, 4, 4, Framebuffer.rgb(220, 210, 160));
            textX = boxX + 30;
            max = 28;
        }
        wrap(fb, line, textX, boxY + 6, max, boxY + boxH - 4);
    }

    static void wrap(Framebuffer fb, String rest, int x, int y, int max, int limit) {
        while (rest.length() > 0 && y < limit) {
            int n = rest.length() < max ? rest.length() : max;
            if (n < rest.length()) {
                int sp = rest.lastIndexOf(' ', n);
                if (sp > 8)
                    n = sp;
            }
            Text.draw(fb, rest.substring(0, n), x, y, false);
            rest = n < rest.length() && rest.charAt(n) == ' ' ? rest.substring(n + 1) : rest.substring(n);
            y += 12;
        }
    }

    static void checkWake() {
        if (world == null || wakeSprite >= 0 || state != ST_PLAY)
            return;
        int ang = Player.angle & 1023;
        int fx = Render.sinTable[(ang + 256) & 1023];
        int fy = -Render.sinTable[ang & 1023];
        int best = -1;
        int bestAhead = 0;
        for (int i = 0; i < world.numSprites; i++) {
            if ((world.sflags[i] & 0x10000) != 0)
                continue;
            int tile = world.stile[i];
            if (tile < 47 || tile > 49)
                continue;
            /* Intro skeleton only: the one by the cave mouth, not every skeleton. */
            if (world.sx[i] < 300 || world.sx[i] > 400 || world.sy[i] < 1380 || world.sy[i] > 1500)
                continue;
            int dx = world.sx[i] - Player.tileX;
            int dy = world.sy[i] - Player.tileY;
            if (dx * dx + dy * dy > 320 * 320)
                continue;
            int ahead = (dx * fx + dy * fy) >> 16;
            if (ahead < 16)
                continue;
            if (best < 0 || ahead < bestAhead) {
                best = i;
                bestAhead = ahead;
            }
        }
        if (best < 0)
            return;
        wakeSprite = best;
        wakeX = Player.tileX;
        wakeY = Player.tileY;
        wakeAng = ang;
        int sx = world.sx[best];
        int sy = world.sy[best];
        int stepX = wakeX + (sx - wakeX) / 4;
        int stepY = wakeY + (sy - wakeY) / 4;
        if (stepX == wakeX && stepY == wakeY) {
            stepX = wakeX + ((sx > wakeX) ? 64 : -64);
            stepY = wakeY;
        }
        if (blocked(stepX, stepY)) {
            stepX = wakeX;
            stepY = wakeY + ((sy > wakeY) ? 64 : -64);
        }
        if (blocked(stepX, stepY)) {
            stepX = wakeX;
            stepY = wakeY;
        }
        wakeDx = stepX;
        wakeDy = stepY;
        int dx = sx - stepX;
        int dy = sy - stepY;
        wakeAng = ((int) (Math.atan2(-dy, dx) * 512 / Math.PI)) & 1023;
        cineFrame = 0;
        state = ST_CINE;
    }

    static boolean isProp(int tile) {
        return tile == 23 || tile == 116 || tile == 147 || tile == 154 || tile == 150
            || tile == 159 || tile == 140 || tile == 142 || tile == 143 || tile == 138
            || tile == 115 || tile == 119 || tile == 99 || tile == 97 || tile == 100
            || tile == 101 || tile == 102 || tile == 66 || tile == 208 || tile == 176
            || tile == 175 || tile == 162 || tile == 163 || tile == 195 || tile == 207;
    }

    /* Entity defs eType 2. Tile 23 is a monster subtype, not a slime. */
    static boolean facesPlayer(int i) {
        if (world == null || alerted == null || i < 0 || i >= alerted.length)
            return false;
        return alerted[i] && isEnemy(world.stile[i]);
    }

    static boolean isEnemy(int tile) {
        return (tile >= 17 && tile <= 27) || (tile >= 29 && tile <= 49)
            || (tile >= 56 && tile <= 60);
    }

    static void chase() {
        if (world == null)
            return;
        ensureHp();
        for (int i = 0; i < world.numSprites; i++) {
            if ((world.sflags[i] & 0x10000) != 0 || (world.sflags[i] & 0x780000) != 0)
                continue;
            if (!isEnemy(world.stile[i]) || mobHp[i] <= 0)
                continue;
            int dx = Player.tileX - world.sx[i];
            int dy = Player.tileY - world.sy[i];
            int dist = dx * dx + dy * dy;
            if (!alerted[i]) {
                if (dist > 4 * 64 * 4 * 64)
                    continue;
                int fx = Render.sinTable[(mobFace[i] + 256) & 1023];
                int fy = -Render.sinTable[mobFace[i] & 1023];
                if (((dx * fx + dy * fy) >> 16) < 32)
                    continue;
                alerted[i] = true;
            }
            if (dist < 70 * 70)
                continue;
            int nx = world.sx[i] + (dx > 24 ? 64 : dx < -24 ? -64 : 0);
            int ny = world.sy[i] + (dy > 24 ? 64 : dy < -24 ? -64 : 0);
            if (blocked(nx, ny))
                continue;
            world.sx[i] = nx;
            world.sy[i] = ny;
            mobFace[i] = ((int) (Math.atan2(-dy, dx) * 512 / Math.PI)) & 1023;
        }
    }

    static void ensureHp() {
        if (world == null)
            return;
        if (mobHp == null || mobHp.length < world.numSprites) {
            mobHp = new int[world.numSprites];
            alerted = new boolean[world.numSprites];
            mobFace = new int[world.numSprites];
            for (int i = 0; i < mobHp.length; i++) {
                mobHp[i] = 12;
                mobFace[i] = 0;
            }
        }
    }

    static int facingMob() {
        if (world == null)
            return -1;
        ensureHp();
        int ang = Player.angle & 1023;
        int fx = Render.sinTable[(ang + 256) & 1023];
        int fy = -Render.sinTable[ang & 1023];
        int best = -1;
        int bestD = 160 * 160;
        for (int i = 0; i < world.numSprites; i++) {
            Entity e = Entity.findBySprite(i);
            if (e != null && e.isMonster) {
                if (!e.alive())
                    continue;
            } else {
                if ((world.sflags[i] & 0x10000) != 0 || (world.sflags[i] & 0x780000) != 0)
                    continue;
                if (!isEnemy(world.stile[i]) || (mobHp != null && i < mobHp.length && mobHp[i] <= 0))
                    continue;
            }
            int dx = (e != null ? e.x : world.sx[i]) - Player.tileX;
            int dy = (e != null ? e.y : world.sy[i]) - Player.tileY;
            int d = dx * dx + dy * dy;
            if (d > bestD || ((dx * fx + dy * fy) >> 16) < 24)
                continue;
            best = i;
            bestD = d;
        }
        return best;
    }

    static String lookName(int tile) {
        if (tile >= 17 && tile <= 19)
            return "Orc";
        if (tile >= 20 && tile <= 22)
            return "Wererat";
        if (tile >= 23 && tile <= 25)
            return "Mimic";
        if (tile >= 47 && tile <= 49)
            return "Brittle Skeleton";
        if (tile == 125)
            return "Sconce";
        if (tile == 178)
            return "Rubble Pile";
        if (tile == 147)
            return "Moveable Boulder";
        if (tile == 142 || tile == 144)
            return "Small Tent";
        if (tile == 139 || tile == 281)
            return "Hunk of Meat";
        if (tile == 143 || tile == 145)
            return "Locked Chest";
        if (tile == 153)
            return "Reliquary";
        if (tile == 180)
            return "Ladder";
        if (tile == 150 || tile == 181)
            return "Tombstone";
        return null;
    }

    static int facingProp() {
        if (world == null)
            return -1;
        int ang = Player.angle & 1023;
        int fx = Render.sinTable[(ang + 256) & 1023];
        int fy = -Render.sinTable[ang & 1023];
        int best = -1;
        int bestD = 96 * 96;
        for (int i = 0; i < world.numSprites; i++) {
            if ((world.sflags[i] & 0x10000) != 0)
                continue;
            if (lookName(world.stile[i]) == null)
                continue;
            int dx = world.sx[i] - Player.tileX;
            int dy = world.sy[i] - Player.tileY;
            int d = dx * dx + dy * dy;
            if (d > bestD || ((dx * fx + dy * fy) >> 16) < 16)
                continue;
            best = i;
            bestD = d;
        }
        return best;
    }

    static void paintTarget(Framebuffer fb) {
        int mob = facingMob();
        int prop = mob < 0 ? facingProp() : -1;
        int i = mob >= 0 ? mob : prop;
        if (i < 0)
            return;
        String name = lookName(world.stile[i]);
        if (name == null)
            name = isEnemy(world.stile[i]) ? "Enemy" : null;
        if (name == null)
            return;
        Text.draw(fb, name, (Framebuffer.W - Text.width(name, false)) / 2, 2, false);
        if (mob < 0)
            return;
        int pips = mobHp[i] / 3;
        if (pips < 1)
            pips = 1;
        if (pips > 8)
            pips = 8;
        int bw = pips * 6;
        int bx = (Framebuffer.W - bw) / 2;
        int by = 12;
        for (int p = 0; p < pips; p++)
            fb.fillRect(bx + p * 6, by, 4, 4, Framebuffer.rgb(40, 210, 40));
    }

    static void checkDwarf() {
        if (world == null || mapId != 2000 || state != ST_PLAY)
            return;
        if (Player.tileX > 1700 && Player.tileY < 900) {
            dialogLine = 0;
            state = ST_DIALOG;
            dwarfExit = true;
        }
    }

    static boolean dwarfExit;

    static void paintHit(Framebuffer fb) {
        if (hitTicks <= 0 || hitSprite < 0)
            return;
        hitTicks--;
        Text.draw(fb, "-" + hitDmg, Framebuffer.W / 2 - 8, TinyGL.VIEW_TOP + 18, false);
        Entity e = Entity.findBySprite(hitSprite);
        int hp = -1;
        if (e != null && e.isMonster)
            hp = e.hp;
        else if (mobHp != null && hitSprite < mobHp.length)
            hp = mobHp[hitSprite];
        if (hp > 0) {
            int pips = hp / 3;
            if (pips < 1)
                pips = 1;
            for (int i = 0; i < pips && i < 4; i++)
                fb.fillRect(14 + i * 8, TinyGL.VIEW_TOP + 16, 6, 5, Framebuffer.rgb(40, 210, 40));
        }
    }

    static void paintExamine(Framebuffer fb) {
        if (world == null)
            return;
        int best = -1;
        int bestD = 70 * 70;
        for (int i = 0; i < world.numSprites; i++) {
            int dx = world.sx[i] - Player.tileX;
            int dy = world.sy[i] - Player.tileY;
            int d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0)
            return;
        int tile = world.stile[best];
        if (tile == 23 || tile == 147 || tile == 116) {
            paintPanel(fb, "OBSTACLE", "Smash it with staff or sword.");
            return;
        }
        if (state == ST_CINE && wakeSprite >= 0) {
            int y = TinyGL.VIEW_TOP;
            int w = 168;
            fb.fillRect(0, y, w, 14, Framebuffer.rgb(12, 12, 16));
            fb.fillRect(0, y + 13, w, 1, Framebuffer.rgb(180, 150, 60));
            fb.fillRect(2, y + 3, 8, 8, Framebuffer.rgb(190, 190, 200));
            fb.fillRect(4, y + 1, 4, 12, Framebuffer.rgb(160, 160, 170));
            Text.draw(fb, "Brittle Skeleton", 14, y + 2, false);
            int hx = 14;
            int hy = y + 16;
            for (int i = 0; i < 4; i++)
                fb.fillRect(hx + i * 8, hy, 6, 5, Framebuffer.rgb(40, 210, 40));
        }
    }

    static void paintPanel(Framebuffer fb, String title, String body) {
        int y = Framebuffer.H - 16 - 52;
        int h = 52;
        int edge = Framebuffer.rgb(230, 230, 230);
        fb.fillRect(0, y, Framebuffer.W, h, 0);
        fb.fillRect(0, y, Framebuffer.W, 1, edge);
        fb.fillRect(0, y + h - 1, Framebuffer.W, 1, edge);
        fb.fillRect(1, y + 1, Framebuffer.W - 2, 14, Framebuffer.rgb(118, 118, 118));
        Text.draw(fb, title, (Framebuffer.W - Text.width(title, false)) / 2, y + 2, false);
        wrap(fb, body, 8, y + 18, 36, y + h - 2);
    }

    static boolean isBanner(String line) {
        return line.indexOf('!') >= 0 && line.indexOf('.') < 0;
    }

    static void paintBanner(Framebuffer fb, String line) {
        int w = Text.width(line, false) + 16;
        int x = (Framebuffer.W - w) / 2;
        int y = 36;
        fb.fillRect(x, y, w, 16, Framebuffer.rgb(8, 8, 12));
        fb.fillRect(x, y, w, 1, Framebuffer.rgb(230, 230, 230));
        fb.fillRect(x, y + 15, w, 1, Framebuffer.rgb(230, 230, 230));
        Text.draw(fb, line, x + 8, y + 2, false);
    }

    static void paintMenu(Framebuffer fb) {
        blitCached(fb, MenuSystem.mapPx, MenuSystem.mapW, MenuSystem.mapH, MenuSystem.scroll);
        int logoY = 8;
        blitCachedAt(fb, MenuSystem.logoPx, MenuSystem.logoW, MenuSystem.logoH,
                (Framebuffer.W - MenuSystem.logoW) / 2, logoY);
        int y = logoY + MenuSystem.logoH + 18;
        if (y < 120)
            y = 120;
        for (int i = 0; i < MenuSystem.ITEMS.length; i++) {
            String line = MenuSystem.ITEMS[i];
            int x = (Framebuffer.W - Text.width(line, true)) / 2;
            if (i == MenuSystem.selected) {
                blitCachedAt(fb, MenuSystem.cursorPx, MenuSystem.cursorW, MenuSystem.cursorH, x - 20, y);
                fb.fillRect(x - 2, y - 1, Text.width(line, true) + 4, 16, Framebuffer.rgb(180, 150, 40));
            }
            Text.draw(fb, line, x, y, true);
            y += 18;
        }
        paintHudBar(fb, Framebuffer.H - 16);
        Text.draw(fb, "Exit", 8, Framebuffer.H - 14, true);
        Text.draw(fb, "Select", Framebuffer.W - Text.width("Select", true) - 8, Framebuffer.H - 14, true);
    }

    static void paintSound(Framebuffer fb) {
        blitCached(fb, MenuSystem.mapPx, MenuSystem.mapW, MenuSystem.mapH, 0);
        int logoY = 8;
        blitCachedAt(fb, MenuSystem.logoPx, MenuSystem.logoW, MenuSystem.logoH,
                (Framebuffer.W - MenuSystem.logoW) / 2, logoY);
        int y = logoY + MenuSystem.logoH + 16;
        String title = "Enable Sounds?";
        Text.draw(fb, title, (Framebuffer.W - Text.width(title, true)) / 2, y, true);
        int yesX = (Framebuffer.W - Text.width("Yes", true)) / 2;
        if (soundChoice == 1)
            blitCachedAt(fb, MenuSystem.cursorPx, MenuSystem.cursorW, MenuSystem.cursorH, yesX - 20, y + 22);
        Text.draw(fb, "Yes", yesX, y + 22, true);
        if (soundChoice == 0)
            blitCachedAt(fb, MenuSystem.cursorPx, MenuSystem.cursorW, MenuSystem.cursorH, yesX - 20, y + 40);
        Text.draw(fb, "No", yesX, y + 40, true);
        paintHudBar(fb, Framebuffer.H - 16);
        Text.draw(fb, "Exit", 8, Framebuffer.H - 14, true);
        Text.draw(fb, "Select", Framebuffer.W - Text.width("Select", true) - 8, Framebuffer.H - 14, true);
    }

    static void paintGrayBar(Framebuffer fb, int y) {
        fb.fillRect(0, y, Framebuffer.W, 18, Framebuffer.rgb(78, 78, 78));
        fb.fillRect(0, y, Framebuffer.W, 1, Framebuffer.rgb(150, 150, 150));
        fb.fillRect(0, y + 17, Framebuffer.W, 1, Framebuffer.rgb(40, 40, 40));
    }

    static void blitCached(Framebuffer fb, short[] px, int w, int h, int scroll) {
        if (px == null || w <= 0 || h <= 0)
            return;
        int sx = (scroll / 2) % w;
        int sy = (scroll / 3) % h;
        for (int y = 0; y < Framebuffer.H; y++) {
            int iy = (sy + y) % h;
            int dst = y * Framebuffer.W;
            int src = iy * w;
            for (int x = 0; x < Framebuffer.W; x++)
                fb.px[dst + x] = px[src + (sx + x) % w];
        }
    }

    static void blitCachedAt(Framebuffer fb, short[] px, int w, int h, int dx, int dy) {
        if (px == null || w <= 0 || h <= 0)
            return;
        for (int y = 0; y < h; y++) {
            int oy = dy + y;
            if (oy < 0 || oy >= Framebuffer.H)
                continue;
            for (int x = 0; x < w; x++) {
                int ox = dx + x;
                if (ox < 0 || ox >= Framebuffer.W)
                    continue;
                short p = px[y * w + x];
                int r = (p >> 11) & 31;
                int b = p & 31;
                if (r > 28 && b > 28 && ((p >> 5) & 63) < 4)
                    continue;
                fb.px[oy * Framebuffer.W + ox] = p;
            }
        }
    }

    static void paintStory(Framebuffer fb) {
        if (MenuSystem.story != null)
            blitImage(fb, MenuSystem.story, 0);
        if (MenuSystem.intro != null)
            blitImageAt(fb, MenuSystem.intro, (Framebuffer.W - MenuSystem.intro.getWidth()) / 2, 24);
        else
            Text.draw(fb, "Intro", 8, 40, false);
        Text.draw(fb, "Select", Framebuffer.W - Text.width("Select", false) - 4, Framebuffer.H - 13, false);
    }

    static void paintHudBar(Framebuffer fb, int y) {
        if (MenuSystem.hudBar == null) {
            fb.fillRect(0, y, Framebuffer.W, 14, Framebuffer.rgb(90, 90, 90));
            return;
        }
        int w = MenuSystem.hudBar.getWidth();
        int h = MenuSystem.hudBar.getHeight();
        if (w < 1)
            w = 16;
        int srcY = h > 16 ? h - 14 : 0;
        int srcH = h > 16 ? 14 : h;
        for (int x = 0; x < Framebuffer.W; x += w)
            blitRegion(fb, MenuSystem.hudBar, 0, srcY, w, srcH, x, y);
    }

    static void blitRegion(Framebuffer fb, javax.microedition.lcdui.Image im, int sx, int sy, int w, int h, int dx, int dy) {
        int[] rgb = new int[w * h];
        im.getRGB(rgb, 0, w, sx, sy, w, h);
        for (int y = 0; y < h; y++) {
            int oy = dy + y;
            if (oy < 0 || oy >= Framebuffer.H)
                continue;
            for (int x = 0; x < w; x++) {
                int ox = dx + x;
                if (ox < 0 || ox >= Framebuffer.W)
                    continue;
                int p = rgb[y * w + x];
                int r = (p >> 16) & 255;
                int g = (p >> 8) & 255;
                int b = p & 255;
                if (r > 220 && b > 220 && g < 48 && r > g + 80 && b > g + 80)
                    continue;
                fb.px[oy * Framebuffer.W + ox] = (short) Framebuffer.rgb(r, g, b);
            }
        }
    }

    static void paintPlay(Framebuffer fb) {
        if (world == null) {
            Text.draw(fb, "no map", 4, 8, false);
            return;
        }
        int floor = Framebuffer.rgb((world.floorRgb >> 16) & 255, (world.floorRgb >> 8) & 255, world.floorRgb & 255);
        int ceil = Framebuffer.rgb((world.ceilRgb >> 16) & 255, (world.ceilRgb >> 8) & 255, world.ceilRgb & 255);
        int horizon = TinyGL.VIEW_TOP + TinyGL.VIEW_H / 2;
        int f = moveF > 6 ? 6 : moveF;
        viewX = fromX + (Player.tileX - fromX) * f / 6;
        viewY = fromY + (Player.tileY - fromY) * f / 6;
        int da = ((Player.angle - fromAng + 512) & 1023) - 512;
        viewAng = (fromAng + da * f / 6) & 1023;
        if (fromX == 0 && fromY == 0) {
            viewX = Player.tileX;
            viewY = Player.tileY;
            viewAng = Player.angle;
        }
        int ang = viewAng & 1023;
        fb.fillRect(0, 0, Framebuffer.W, Framebuffer.H, 0);
        fb.fillRect(0, TinyGL.VIEW_TOP, TinyGL.VIEW_W, TinyGL.VIEW_H / 2, ceil);
        fb.fillRect(0, horizon, TinyGL.VIEW_W, TinyGL.VIEW_H / 2, floor);
        TinyGL.drawWalls(fb, world, viewX, viewY, ang, horizon);
        TinyGL.drawSprites(fb, world, viewX, viewY, ang, horizon);
        if (weaponTicks > 0)
            weaponTicks--;
        int bob = weaponTicks > 0 ? -6 : ((stepCount & 1) == 0 ? 0 : 2);
        Sprites.drawWeapon(fb, 0, bob);
        paintPlayHud(fb);
    }

    static void paintPlayHud(Framebuffer fb) {
        int strip = 22;
        paintHudSlice(fb, 0, true);
        blitHud(fb, MenuSystem.hudTopLeft, 0, strip);
        blitHud(fb, MenuSystem.hudTopCenter, (Framebuffer.W - widthOf(MenuSystem.hudTopCenter)) / 2, strip);
        blitHud(fb, MenuSystem.hudTopRight, Framebuffer.W - widthOf(MenuSystem.hudTopRight), strip);
        int soft = Framebuffer.H - 16;
        int leftH = heightOf(MenuSystem.hudBottomLeft);
        int midH = heightOf(MenuSystem.hudBottomCenter);
        int rightH = heightOf(MenuSystem.hudBottomRight);
        int y = soft - leftH;
        blitHud(fb, MenuSystem.hudBottomLeft, 0, y);
        blitHud(fb, MenuSystem.hudBottomCenter, (Framebuffer.W - widthOf(MenuSystem.hudBottomCenter)) / 2, soft - midH);
        int rightX = Framebuffer.W - widthOf(MenuSystem.hudBottomRight);
        blitHud(fb, MenuSystem.hudBottomRight, rightX, soft - rightH);
        paintHudSlice(fb, soft, false);
        Text.draw(fb, "10", Framebuffer.W / 2 - 46, soft - midH + 12, false);
        Text.draw(fb, "100", Framebuffer.W / 2 + 10, soft - midH + 12, false);
        paintHeart(fb, rightX, soft - rightH, widthOf(MenuSystem.hudBottomRight), rightH);
        Text.draw(fb, state == ST_DIALOG || state == ST_STORY ? "" : "Menu", 4, soft + 2, false);
        String right = state == ST_DIALOG || state == ST_STORY ? "Skip" : "Map";
        Text.draw(fb, right, Framebuffer.W - Text.width(right, false) - 4, soft + 2, false);
    }

    static void paintHeart(Framebuffer fb, int x, int y, int w, int h) {
        if (w < 8)
            w = 36;
        if (h < 8)
            h = 28;
        int max = Player.hpMax < 1 ? 100 : Player.hpMax;
        int hp = Player.hp;
        if (hp < 0)
            hp = 0;
        if (hp > max)
            hp = max;
        int innerX = x + w / 4;
        int innerW = w / 2;
        int innerY = y + 4;
        int innerH = h - 10;
        if (innerH < 4)
            innerH = 4;
        int fill = innerH * hp / max;
        fb.fillRect(innerX, innerY, innerW, innerH, Framebuffer.rgb(40, 16, 16));
        fb.fillRect(innerX, innerY + innerH - fill, innerW, fill, Framebuffer.rgb(204, 0, 0));
        String n = "" + hp;
        Text.draw(fb, n, innerX + (innerW - Text.width(n, false)) / 2, innerY + innerH / 2 - 4, false);
    }

    static void paintHudSlice(Framebuffer fb, int y, boolean top) {
        if (MenuSystem.hudBar == null) {
            fb.fillRect(0, y, Framebuffer.W, 8, Framebuffer.rgb(top ? 140 : 90, top ? 90 : 90, top ? 30 : 90));
            return;
        }
        int w = MenuSystem.hudBar.getWidth();
        int h = MenuSystem.hudBar.getHeight();
        int srcY = top ? 0 : h / 2;
        int srcH = h / 2;
        if (srcH < 1)
            srcH = h;
        for (int x = 0; x < Framebuffer.W; x += w)
            blitRegion(fb, MenuSystem.hudBar, 0, srcY, w, srcH, x, y);
    }

    static void blitHud(Framebuffer fb, javax.microedition.lcdui.Image im, int x, int y) {
        if (im == null)
            return;
        blitRegion(fb, im, 0, 0, im.getWidth(), im.getHeight(), x, y);
    }

    static int widthOf(javax.microedition.lcdui.Image im) {
        return im == null ? 0 : im.getWidth();
    }

    static int heightOf(javax.microedition.lcdui.Image im) {
        return im == null ? 0 : im.getHeight();
    }

    static void paintFlats(Framebuffer fb, int horizon) {
        for (int y = 0; y < Framebuffer.H; y++) {
            boolean ceiling = y < horizon;
            int row = ceiling ? horizon - y : y - horizon;
            if (row < 1)
                row = 1;
            int scale = 4096 / row;
            for (int x = 0; x < Framebuffer.W; x += 2) {
                int u = (Player.tileX + (x - 88) * scale / 64) >> 2;
                int v = (Player.tileY + scale) >> 2;
                short p = Tex.flat(ceiling ? 1 : 0, u, v);
                if (p == 0)
                    p = (short) Framebuffer.rgb(ceiling ? 48 : 28, ceiling ? 48 : 28, ceiling ? 48 : 28);
                fb.px[y * Framebuffer.W + x] = p;
                if (x + 1 < Framebuffer.W)
                    fb.px[y * Framebuffer.W + x + 1] = p;
            }
        }
    }

    static void paintSprites(Framebuffer fb, int horizon, int ang) {
        int fx = Render.sinTable[(ang + 256) & 1023];
        int fy = Render.sinTable[ang];
        int rx = Render.sinTable[ang];
        int ry = -Render.sinTable[(ang + 256) & 1023];
        for (int i = 0; i < world.numSprites; i++) {
            int dx = world.sx[i] - Player.tileX;
            int dy = world.sy[i] - Player.tileY;
            int depth = (int) (((long) dx * fx + (long) dy * fy) >> 16);
            if (depth < 16)
                continue;
            int side = (int) (((long) dx * rx + (long) dy * ry) >> 16);
            int col = 88 + side * 80 / depth;
            int h = 2400 / depth;
            if (h > 48)
                h = 48;
            int top = horizon - h;
            for (int y = 0; y < h * 2; y++) {
                int oy = top + y;
                if (oy < 0 || oy >= Framebuffer.H)
                    continue;
                short p = Tex.sample(world.stile[i], y * 64 / (h * 2), 32);
                if (p == 0)
                    p = (short) Framebuffer.rgb(180, 160, 90);
                if (col >= 0 && col < Framebuffer.W)
                    fb.px[oy * Framebuffer.W + col] = p;
            }
        }
    }

    static int hit(int px, int py, int rdx, int rdy, int x1, int y1, int x2, int y2) {
        int sx = x2 - x1;
        int sy = y2 - y1;
        int den = (int) (((long) rdx * sy - (long) rdy * sx) >> 16);
        if (den == 0)
            return 1 << 28;
        int t = (int) (((long) (x1 - px) * sy - (long) (y1 - py) * sx) / den);
        if (t < 4)
            return 1 << 28;
        long hitX = px + ((long) rdx * t >> 16);
        long hitY = py + ((long) rdy * t >> 16);
        int u = sx != 0 ? (int) (hitX - x1) : (int) (hitY - y1);
        int uDen = sx != 0 ? sx : sy;
        if (uDen > 0) {
            if (u < 0 || u > uDen)
                return 1 << 28;
        } else if (u > 0 || u < uDen) {
            return 1 << 28;
        }
        return t;
    }

    static void blitImage(Framebuffer fb, javax.microedition.lcdui.Image im, int scroll) {
        int mw = im.getWidth();
        int mh = im.getHeight();
        if (mw <= 0 || mh <= 0)
            return;
        int sx = (scroll / 2) % mw;
        int sy = (scroll / 3) % mh;
        int[] row = new int[mw];
        for (int y = 0; y < Framebuffer.H; y++) {
            int iy = (sy + y) % mh;
            im.getRGB(row, 0, mw, 0, iy, mw, 1);
            int dst = y * Framebuffer.W;
            for (int x = 0; x < Framebuffer.W; x++) {
                int p = row[(sx + x) % mw];
                fb.px[dst + x] = (short) Framebuffer.rgb((p >> 16) & 255, (p >> 8) & 255, p & 255);
            }
        }
    }

    static void blitImageAt(Framebuffer fb, javax.microedition.lcdui.Image im, int dx, int dy) {
        int w = im.getWidth();
        int h = im.getHeight();
        if (w <= 0 || h <= 0)
            return;
        int[] rgb = new int[w * h];
        im.getRGB(rgb, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++) {
            int oy = dy + y;
            if (oy < 0 || oy >= Framebuffer.H)
                continue;
            for (int x = 0; x < w; x++) {
                int ox = dx + x;
                if (ox < 0 || ox >= Framebuffer.W)
                    continue;
                int p = rgb[y * w + x];
                int r = (p >> 16) & 255;
                int g = (p >> 8) & 255;
                int b = p & 255;
                if (r > 240 && g < 16 && b > 240)
                    continue;
                fb.px[oy * Framebuffer.W + ox] = (short) Framebuffer.rgb(r, g, b);
            }
        }
    }
}
