/* Entity base: definitions, 32x32 tile grid, spawn from map sprites.
 * Port of the relevant parts of entity.c / Game_spawnEntities.
 *
 * info bits (subset of C):
 *   low 16 = sprite index (1-based) or line index
 *   0x10000  attacking
 *   0x20000  removed / gibbed
 *   0x40000  alive / interactive
 *   0x80000  active (monster)
 *   0x100000 loot dropped
 *   0x200000 line entity
 *   0x4000000 linked in grid
 */
final class Entity {
    static final int MAX = 450;
    static final int MAX_MONSTERS = 100;
    static final int GRID = 32;

    static final int INFO_ATTACKING = 0x10000;
    static final int INFO_REMOVED = 0x20000;
    static final int INFO_ALIVE = 0x40000;
    static final int INFO_ACTIVE = 0x80000;
    static final int INFO_LINE = 0x200000;
    static final int INFO_LINKED = 0x4000000;

    static Entity[] pool = new Entity[MAX];
    static int count;
    static Entity[] grid = new Entity[GRID * GRID];
    static int numMonsters;

    EntityDef def;
    Entity nextOnTile;
    Entity prevOnTile;
    int linkIndex;
    int info;
    int param;
    String name = "";
    int x, y;          /* world position (from sprite) */
    int spriteIndex = -1;
    int hp, maxHp;
    int monsterFlags;
    boolean isMonster;
    boolean isBlocker;

    static {
        for (int i = 0; i < MAX; i++)
            pool[i] = new Entity();
    }

    static void clear() {
        count = 0;
        numMonsters = 0;
        for (int i = 0; i < grid.length; i++)
            grid[i] = null;
        for (int i = 0; i < MAX; i++)
            pool[i].reset();
    }

    void reset() {
        def = null;
        nextOnTile = null;
        prevOnTile = null;
        linkIndex = 0;
        info = 0;
        param = 0;
        name = "";
        x = y = 0;
        spriteIndex = -1;
        hp = maxHp = 0;
        monsterFlags = 0;
        isMonster = false;
        isBlocker = false;
    }

    static Entity alloc() {
        if (count >= MAX)
            return null;
        Entity e = pool[count++];
        e.reset();
        return e;
    }

    static void spawnFromMap(BspMap map) {
        clear();
        if (map == null)
            return;
        EntityDef.load();

        /* Entity 0: world; 1 and 2: player defs (placeholders). */
        Entity world = alloc();
        if (world != null) {
            world.def = EntityDef.findType(0, 0);
            if (world.def != null)
                world.name = world.def.name;
        }
        Entity p1 = alloc();
        if (p1 != null) {
            p1.def = EntityDef.findType(1, 0);
            if (p1.def != null)
                p1.name = p1.def.name;
            p1.info = INFO_ALIVE;
        }
        Entity p2 = alloc();
        if (p2 != null) {
            p2.def = EntityDef.findType(1, 1);
            if (p2.def != null)
                p2.name = p2.def.name;
        }

        EntityDef blocker = EntityDef.findType(0xc, 0);
        EntityDef blockerAlt = EntityDef.findType(0xc, 1);
        EntityDef blockerLow = EntityDef.findType(0xd, 0);

        for (int i = 0; i < map.numSprites; i++) {
            int info = map.sflags[i];
            if ((info & 0x8000000) != 0) {
                map.sflags[i] = info & ~0x8000000;
                continue;
            }
            int tile = info & 0xff;
            if ((info & 0x40000) != 0)
                tile += 0x101;
            int sx = map.sx[i];
            int sy = map.sy[i];
            EntityDef def = EntityDef.findByTile(tile);

            int linkX = sx;
            int linkY = sy;
            if (def == null || def.eType != 2) {
                int wall = info & 0x780000;
                if (wall != 0x600000 && wall != 0x180000 && wall != 0) {
                    if ((info & 0x200000) != 0)
                        linkX -= 0x40;
                    else if ((info & 0x100000) != 0)
                        linkY -= 0x40;
                    else if ((info & 0x80000) != 0)
                        linkY += 0x20;
                    else if ((info & 0x400000) != 0)
                        linkX += 0x20;
                }
                if (def == null) {
                    if ((info & 0x20000) == 0)
                        continue;
                    Entity ent = alloc();
                    if (ent == null)
                        return;
                    ent.info = (i + 1) & 0xffff;
                    ent.spriteIndex = i;
                    ent.x = sx;
                    ent.y = sy;
                    if (tile == 0xa6 || tile == 0xa8 || tile == 0xc2) {
                        ent.def = blockerLow;
                        if ((map.sflags[i] & 0x40000000) != 0)
                            map.sflags[i] &= ~0x40000000;
                    } else if ((map.sflags[i] & 0x40000000) != 0) {
                        ent.def = blockerAlt;
                        map.sflags[i] &= ~0x40000000;
                    } else {
                        ent.def = blocker;
                    }
                    if (ent.def != null)
                        ent.name = ent.def.name;
                    ent.isBlocker = true;
                    if ((map.sflags[i] & 0x10000) == 0)
                        link(ent, linkX >> 6, linkY >> 6);
                    continue;
                }
            }

            Entity ent = alloc();
            if (ent == null)
                return;
            ent.def = def;
            ent.info = (i + 1) & 0xffff;
            ent.spriteIndex = i;
            ent.x = sx;
            ent.y = sy;
            if (def != null)
                ent.name = def.name;

            if (def != null && def.eType == 2) {
                if (numMonsters >= MAX_MONSTERS)
                    return;
                numMonsters++;
                ent.isMonster = true;
                ent.hp = ent.maxHp = defaultHp(def);
                int fi = map.sflags[i];
                if ((fi & 0x80000) != 0)
                    ent.monsterFlags |= 0x1000;
                else if ((fi & 0x100000) != 0)
                    ent.monsterFlags |= 0x2000;
                else if ((fi & 0x200000) != 0)
                    ent.monsterFlags |= 0x3000;
                else if ((fi & 0x400000) != 0)
                    ent.monsterFlags |= 0x4000;
                map.sflags[i] &= 0xff87ffff;
                ent.info |= INFO_ACTIVE;
            } else if (def != null && def.eType == 7
                    && (def.eSubType == 1 || def.eSubType == 6 || def.eSubType == 4)) {
                map.sflags[i] |= 0x80000000;
            }
            initSpawn(ent);
            if ((map.sflags[i] & 0x10000) == 0)
                link(ent, linkX >> 6, linkY >> 6);
        }
    }

    static void initSpawn(Entity ent) {
        if (ent.def == null)
            return;
        ent.name = ent.def.name;
        switch (ent.def.eType) {
        case 2: /* monster */
            ent.info |= INFO_ALIVE;
            break;
        case 7:
            if (ent.def.parm != 0)
                ent.info |= INFO_ALIVE;
            else if (ent.def.eSubType == 4)
                ent.param = 1;
            break;
        case 8:
        case 10:
            ent.info |= INFO_ALIVE;
            break;
        case 9:
            if (ent.def.eSubType == 16)
                ent.info |= 0x40040000;
            break;
        case 3:
            ent.param = 1;
            break;
        default:
            break;
        }
    }

    static int defaultHp(EntityDef def) {
        /* Placeholder until CombatEntity tables are ported. */
        int base = 12 + def.eSubType * 2 + def.parm;
        if (Player.difficulty == 4)
            base += base >> 2;
        else if (Player.difficulty == 2 && def.eSubType < 11)
            base += base >> 2;
        return base < 4 ? 4 : base;
    }

    static void link(Entity ent, int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= GRID || ty >= GRID)
            return;
        if ((ent.info & INFO_LINKED) != 0)
            unlink(ent);
        int idx = tx + ty * GRID;
        ent.linkIndex = (short) idx;
        ent.nextOnTile = grid[idx];
        ent.prevOnTile = null;
        if (grid[idx] != null)
            grid[idx].prevOnTile = ent;
        grid[idx] = ent;
        ent.info |= INFO_LINKED;
    }

    static void unlink(Entity ent) {
        if ((ent.info & INFO_LINKED) == 0)
            return;
        int idx = ent.linkIndex & 0xffff;
        if (idx < 0 || idx >= grid.length) {
            ent.info &= ~INFO_LINKED;
            return;
        }
        if (grid[idx] == ent)
            grid[idx] = ent.nextOnTile;
        if (ent.prevOnTile != null)
            ent.prevOnTile.nextOnTile = ent.nextOnTile;
        if (ent.nextOnTile != null)
            ent.nextOnTile.prevOnTile = ent.prevOnTile;
        ent.nextOnTile = null;
        ent.prevOnTile = null;
        ent.info &= ~INFO_LINKED;
    }

    static Entity at(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= GRID || ty >= GRID)
            return null;
        return grid[tx + ty * GRID];
    }

    static Entity findBySprite(int sprite) {
        for (int i = 0; i < count; i++)
            if (pool[i].spriteIndex == sprite)
                return pool[i];
        return null;
    }

    static Entity nearestMonster(int x, int y, int maxDist2) {
        Entity best = null;
        int bestD = maxDist2;
        for (int i = 0; i < count; i++) {
            Entity e = pool[i];
            if (!e.isMonster || (e.info & INFO_REMOVED) != 0 || e.hp <= 0)
                continue;
            int dx = e.x - x;
            int dy = e.y - y;
            int d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    boolean alive() {
        return isMonster && (info & INFO_REMOVED) == 0 && hp > 0;
    }
}
