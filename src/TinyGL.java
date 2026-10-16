/* Port of oe2-re render.c / render_span.c / render_sprite.c.
 *
 * View space after transformPoint is 16.16 (x = depth, y = lateral).
 * A wall is clipped to the 90-degree frustum and the near plane (4.0),
 * projected with projW, then drawn as one vertical span per column.
 * walkBSP is front to back: solid walls mark columns (occluder pass)
 * so a node whose silhouette is covered is skipped. Sprites test the
 * same per-column depth. Floor and ceiling rows are the perspective
 * span from Render_drawFlatRow.
 */
final class TinyGL {
    static final int NEAR_PLANE = 0x40000;
    static final int DEPTH_EMPTY = 0x7fffffff;
    static final int DEPTH_SOLID = 0x7ffffffe;
    static final int VIEW_W = 240;
    static final int VIEW_H = 270;
    static final int VIEW_TOP = 12;

    static Framebuffer fb;
    static BspMap map;
    static int camX, camY, camZ, camAng;
    static int horizon;
    static int viewY0;
    static int viewX0;
    static int projX, projW, projH;
    static int centerY;
    static int floorH, ceilH;
    static int vx0, vx1, vx2, vx3, vx4, vx5;
    static int frame = 1;
    static final int[] columnDepth = new int[VIEW_W];
    static final int[] leaves = new int[400];
    static int leafCount;
    static final int[] drawnLine = new int[750];
    static final int[] lx1 = new int[6];
    static final int[] lx2 = new int[6];

    static void drawWalls(Framebuffer dest, BspMap world, int px, int py, int angle, int eye) {
        beginView(dest, world, px, py, angle, eye);
        clearDepth();
        drawFloorCeiling();
        leafCount = 0;
        if (world.numNodes > 0)
            walk(0, 0);
        for (int i = 0; i < leafCount; i++)
            drawLeaf(leaves[i]);
        /* A line the BSP never linked, or a span that projected right-to-left,
         * still has to be drawn. Depth keeps the nearer face. */
        for (int i = 0; i < map.numLines; i++) {
            if ((map.flags[i] & 0x1000000) != 0)
                continue;
            if (drawnLine[i] != frame)
                drawLine(i, map.tex[i]);
        }
    }

    static int[] spriteOrder = new int[1100];
    static int[] spriteDist = new int[1100];

    static void drawSprites(Framebuffer dest, BspMap world, int px, int py, int angle, int eye) {
        if (fb != dest || map != world)
            beginView(dest, world, px, py, angle, eye);
        int n = world.numSprites;
        if (n > spriteOrder.length)
            n = spriteOrder.length;
        for (int i = 0; i < n; i++) {
            spriteOrder[i] = i;
            int dx = world.sx[i] - px;
            int dy = world.sy[i] - py;
            spriteDist[i] = dx * dx + dy * dy;
        }
        for (int i = 1; i < n; i++) {
            int oi = spriteOrder[i];
            int di = spriteDist[oi];
            int j = i;
            while (j > 0 && spriteDist[spriteOrder[j - 1]] < di) {
                spriteOrder[j] = spriteOrder[j - 1];
                j--;
            }
            spriteOrder[j] = oi;
        }
        for (int i = 0; i < n; i++)
            drawSprite(spriteOrder[i]);
    }

    static void beginView(Framebuffer dest, BspMap world, int px, int py, int angle, int eye) {
        fb = dest;
        map = world;
        camX = px;
        camY = py;
        camAng = angle;
        camAng = angle;
        horizon = eye;
        frame++;
        int cosA = Render.sinTable[(angle + 256) & 1023];
        int sinA = Render.sinTable[angle & 1023];
        /* eye sits 16 units behind the player, same as Render_render */
        int x = px - (cosA >> 12);
        int y = py + (sinA >> 12);
        camX = x;
        camY = y;
        camZ = 0x24;
        floorH = map.floorH;
        ceilH = map.ceilH < 1 ? 0x40 : map.ceilH;
        viewX0 = 0;
        viewY0 = VIEW_TOP;
        if (viewY0 + VIEW_H > Framebuffer.H)
            viewY0 = Framebuffer.H - VIEW_H;
        centerY = VIEW_H / 2;
        projX = (VIEW_W / 2 << 16) - 0x8000;
        projH = VIEW_W << 16;
        projW = projH - 0x8000;
        vx0 = cosA;
        vx1 = -sinA;
        vx2 = -(rmul(x, cosA) + rmul(-sinA, y));
        vx3 = sinA;
        vx4 = cosA;
        vx5 = -(rmul(x, sinA) + rmul(cosA, y));
    }

    static void clearDepth() {
        for (int i = 0; i < VIEW_W; i++)
            columnDepth[i] = DEPTH_EMPTY;
    }

    /* Render_transformPoint. pt[0]=x, pt[1]=y, in place. */
    static void transform(int[] pt) {
        int x = pt[0];
        int y = pt[1];
        pt[1] = rmul(vx4, y) + rmul(vx3, x) + vx5;
        pt[0] = rmul(vx1, y) + rmul(vx0, x) + vx2;
    }

    static void project(int[] pt, int proj) {
        int lateral = pt[1];
        int depth = pt[0];
        if (depth < NEAR_PLANE)
            depth = NEAR_PLANE;
        int scale = div(mul64(proj, 0x10000), depth);
        pt[1] = scale;
        pt[0] = projX + (int) (mul64(lateral, scale) >> 17);
        pt[2] = rmul(scale, pt[2]);
    }

    static void lerpClip(int[] out, int[] a, int[] b, int d0, int d1) {
        int den = d1 - d0;
        if (den == 0)
            return;
        long t = mul64(-d0, 0x10000) / den;
        long s = 0x10000L - t;
        out[0] = (int) ((mul64(s, a[0]) + mul64(t, b[0]) + 0x8000) >> 16);
        out[1] = (int) ((mul64(s, a[1]) + mul64(t, b[1]) + 0x8000) >> 16);
        out[2] = (int) ((mul64(s, a[2]) + mul64(t, b[2]) + 0x8000) >> 16);
    }

    /* Render_clipSegmentNear. 1 = survives. Ends live in lx1/lx2. */
    static boolean clipNear() {
        int a, b;
        a = lx1[0] + lx1[1];
        b = lx2[0] + lx2[1];
        if (a < 0) {
            if (b < 0)
                return false;
            lerpClip(lx1, lx1, lx2, a, b);
        } else if (b < 0) {
            lerpClip(lx2, lx1, lx2, -a, -b);
        }
        a = lx1[0] - lx1[1];
        b = lx2[0] - lx2[1];
        if (b < 0) {
            if (a < 0)
                return false;
            lerpClip(lx2, lx1, lx2, a, b);
        } else if (a < 0) {
            lerpClip(lx1, lx1, lx2, -a, -b);
        }
        a = lx1[0] - NEAR_PLANE;
        b = lx2[0] - NEAR_PLANE;
        if (b < 0) {
            if (a < 0)
                return false;
            lerpClip(lx2, lx1, lx2, a, b);
        } else if (a < 0) {
            lerpClip(lx1, lx1, lx2, -a, -b);
        }
        return true;
    }

    static void loadWorld(int x1, int y1, int z1, int x2, int y2, int z2) {
        lx1[0] = x1;
        lx1[1] = y1;
        lx1[2] = z1;
        lx2[0] = x2;
        lx2[1] = y2;
        lx2[2] = z2;
    }

    static boolean facing(int x1, int y1, int x2, int y2) {
        return rmul(y2 - y1, x1 - camX) + rmul(-(x2 - x1), y1 - camY) > 0;
    }

    /* Render_isSegmentVisible: 1 = hidden. */
    static boolean nodeHidden(int node) {
        int x1 = map.nx1[node];
        int y1 = map.ny1[node];
        int x2 = map.nx2[node];
        int y2 = map.ny2[node];
        if (x1 - 5 <= camX && camX <= x2 + 5 && y1 - 5 <= camY && camY <= y2 + 5)
            return false;
        int sx1, sy1, sx2, sy2;
        if (camX < x1) {
            if (camY < y1) {
                sx1 = x2; sy1 = y1; sx2 = x1; sy2 = y2;
            } else if (camY < y2) {
                sx1 = x1; sy1 = y1; sx2 = x1; sy2 = y2;
            } else {
                sx1 = x1; sy1 = y1; sx2 = x2; sy2 = y2;
            }
        } else if (camX < x2) {
            if (camY < y1) {
                sx1 = x2; sy1 = y1; sx2 = x1; sy2 = y1;
            } else if (camY < y2) {
                return false;
            } else {
                sx1 = x1; sy1 = y2; sx2 = x2; sy2 = y2;
            }
        } else if (camY < y1) {
            sx1 = x2; sy1 = y2; sx2 = x1; sy2 = y1;
        } else {
            sx1 = camY < y2 ? x2 : x1;
            sy1 = y2;
            sx2 = x2;
            sy2 = y1;
        }
        loadWorld(sx1, sy1, 0, sx2, sy2, 0);
        transform(lx1);
        transform(lx2);
        /* a silhouette that misses the frustum is not proof the node is
         * covered; the original returns hidden here, which blanks the
         * view when the bbox edge is behind the eye. */
        if (!clipNear())
            return false;
        project(lx1, projW);
        project(lx2, projW);
        int a = (lx1[0] + 0xffff) >> 16;
        int b = (lx2[0] + 0xffff) >> 16;
        if (a > b) {
            int t = a;
            a = b;
            b = t;
        }
        if (a < 0)
            a = 0;
        if (b > VIEW_W)
            b = VIEW_W;
        if (a >= b)
            return false;
        for (int x = a; x < b; x++)
            if (columnDepth[x] == DEPTH_EMPTY)
                return false;
        return true;
    }

    static void walk(int node, int depth) {
        if (node < 0 || node >= map.numNodes || depth > 48)
            return;
        if (map.isSplit(node)) {
            boolean pos = map.naxis[node] == 1 ? camX > map.nsplit[node] : camY > map.nsplit[node];
            int near = pos ? map.nlo[node] : map.nhi[node];
            int far = pos ? map.nhi[node] : map.nlo[node];
            walk(near, depth + 1);
            walk(far, depth + 1);
            return;
        }
        if (leafCount < leaves.length)
            leaves[leafCount++] = node;
        int first = map.nlo[node];
        int count = map.nhi[node];
        for (int i = 0; i < count; i++) {
            int li = first + i;
            if (li < 0 || li >= map.numLines)
                continue;
            if ((map.flags[li] & 0x1000000) != 0)
                continue;
            if (!twoSided(map.flags[li]))
                occludeLine(li);
        }
    }

    static void drawLeaf(int node) {
        int first = map.nlo[node];
        int count = map.nhi[node];
        for (int i = 0; i < count; i++) {
            int li = first + i;
            if (li < 0 || li >= map.numLines)
                continue;
            if ((map.flags[li] & 0x1000000) != 0)
                continue;
            drawLine(li, map.tex[li]);
        }
    }

    static boolean twoSided(int flags) {
        return (flags & 1) != 0 || (flags & 0x4000000) != 0;
    }

    static void occludeLine(int li) {
        if (!prepare(li))
            return;
        project(lx1, projW);
        project(lx2, projW);
        int x0 = (lx1[0] + 0xffff) >> 16;
        int x1 = (lx2[0] + 0xffff) >> 16;
        if (x0 < 0)
            x0 = 0;
        if (x1 > VIEW_W)
            x1 = VIEW_W;
        for (int x = x0; x < x1; x++)
            columnDepth[x] = DEPTH_SOLID;
    }

    /* World line -> view space, backface culled, frustum clipped. */
    static boolean prepare(int li) {
        int x1 = map.x1[li];
        int y1 = map.y1[li];
        int x2 = map.x2[li];
        int y2 = map.y2[li];
        int z1 = map.z1[li];
        int z2 = map.z2[li];
        if (camX == ((x1 + x2) >> 1) && camY == ((y1 + y2) >> 1))
            return false;
        if (!facing(x1, y1, x2, y2)) {
            int t = x1; x1 = x2; x2 = t;
            t = y1; y1 = y2; y2 = t;
            t = z1; z1 = z2; z2 = t;
        }
        loadWorld(x1, y1, z1, x2, y2, z2);
        transform(lx1);
        transform(lx2);
        return clipNear();
    }

    static void drawLine(int li, int tex) {
        if (li >= 0 && li < drawnLine.length)
            drawnLine[li] = frame;
        if (!prepare(li))
            return;
        project(lx1, projW);
        project(lx2, projW);
        rasterWall(tex, map.tex2[li], map.flags[li]);
    }

    /* Render_rasterizeWall: one textured column per screen x.
     * 0x75 texels cover 64 world units. */
    static void rasterWall(int tex, int tex2, int flags) {
        int dx = lx2[0] - lx1[0];
        if (dx == 0)
            return;
        int y = lx1[1];
        int z = lx1[2];
        if (dx < 0) {
            dx = -dx;
            y = lx2[1];
            z = lx2[2];
            int t = lx1[0]; lx1[0] = lx2[0]; lx2[0] = t;
            t = lx1[1]; lx1[1] = lx2[1]; lx2[1] = t;
            t = lx1[2]; lx1[2] = lx2[2]; lx2[2] = t;
            y = lx1[1];
            z = lx1[2];
        }
        int dY = div(mul64(lx2[1] - y, 0x10000), dx);
        int dZ = div(mul64(lx2[2] - z, 0x10000), dx);
        int x = (lx1[0] + 0xffff) >> 16;
        int xEnd = (lx2[0] + 0xffff) >> 16;
        if (x < 0)
            x = 0;
        if (xEnd > VIEW_W)
            xEnd = VIEW_W;
        int frac = (x << 16) - lx1[0];
        z += (int) (mul64(frac, dZ) >> 16);
        y += (int) (mul64(frac, dY) >> 16);
        boolean multi = (flags & 0xc0010000) != 0;
        for (; x < xEnd; x++, y += dY, z += dZ) {
            if (y <= 0)
                continue;
            int depth = (0x40000000 / y) << 2;
            if (columnDepth[x] < depth)
                continue;
            columnDepth[x] = depth;
            int vStep = div(0x1d4000000L, y);
            int top = 0x40;
            int bottom = floorH;
            if (multi) {
                top = ceilH;
                bottom = (flags & 0x10000) != 0 ? floorH : 0x40;
            }
            int column = smulhi(vStep, z) % 0x75;
            if (column < 0)
                column += 0x75;
            int du = vStep >> 3;
            int screenY = -1;
            int piece = 0;
            while (top > bottom) {
                int h = 0x40;
                int topZ = camZ + top - 0x20;
                if (topZ - h < bottom)
                    h = topZ - bottom;
                if (h <= 0)
                    break;
                int len = (int) (((long) y * (long) h) >> 17);
                if (len < 2 && y > 0)
                    len = 2;
                if (screenY < 0)
                    screenY = centerY - ((rmul(y, top - camZ + 0x20 - 0x20) + 0x8000) >> 17) + 1;
                int texA = 0x75 * column;
                if (multi && piece == 0 && tex2 != tex)
                    texA = 0x75 * column;
                int u = texA << 12;
                int sy = screenY;
                if (sy < 0) {
                    u -= rmul(du, sy);
                    len += sy;
                    sy = 0;
                }
                if (sy + len > VIEW_H)
                    len = VIEW_H - sy;
                if (len > 0)
                    spanWall(x, sy, u, du, len, piece == 0 && tex2 != 0 && multi ? tex2 : tex, y);
                screenY = sy + len;
                piece++;
                top -= 0x40;
            }
        }
    }

    static void spanWall(int x, int y, int u, int du, int n, int tex, int yScale) {
        int row = (viewY0 + y) * Framebuffer.W + viewX0 + x;
        short[] px = fb.px;
        for (int i = 0; i < n; i++) {
            int tv = u >> 12;
            int col = tv / 117;
            int vr = tv - col * 117;
            if (col < 0)
                col = -col;
            col = col % 117;
            if (vr < 0)
                vr = 0;
            if (vr > 116)
                vr = 116;
            short c;
            if (Tex.wallTexCount > 0)
                c = Tex.wallTexel(tex, col * 117 + vr);
            else
                c = fallback(tex, i, n);
            px[row] = shade(c, yScale);
            row += Framebuffer.W;
            u += du;
        }
    }

    /* Render_drawFlatRow / Render_drawFloorCeiling. Uniform flat: the
     * phone's per-tile floor map is not in the J2ME tables yet. */
    static void drawFloorCeiling() {
        if (Tex.flatCount <= 0)
            return;
        int flat = 0;
        int ceil = Tex.flatCount > 1 ? 1 : 0;
        for (int y = 0; y < centerY; y++)
            flatRow(y, ceil, true);
        for (int y = centerY; y < VIEW_H; y++)
            flatRow(y, flat, false);
    }

    static void flatRow(int y, int tile, boolean ceiling) {
        int y2 = y * 2;
        int dz = ceiling ? ceilH - camZ : camZ - floorH;
        int rows = ceiling ? VIEW_H - y2 : y2 - VIEW_H;
        if (rows < 0)
            rows = -rows;
        int dist = rmul(VIEW_W, dz << 3) / (rows + 1);
        if (dist < 1)
            dist = 1;
        int k = ((dist << 19) / (VIEW_W << 5));
        int sinA = vx3;
        int cosA = vx4;
        int dv = rmul(k >> 8, cosA >> 8);
        int du = rmul(sinA >> 8, k >> 8);
        int uu = (camX << 16) + (rmul(cosA, dist) >> 3) + (rmul(du, 1 - VIEW_W) >> 1);
        int vv = (camY << 16) + (rmul(-sinA, dist) >> 3) + (rmul(dv, 1 - VIEW_W) >> 1);
        int row = (viewY0 + y) * Framebuffer.W + viewX0;
        short[] px = fb.px;
        for (int x = 0; x < VIEW_W; x++) {
            int id = tile;
            if (map.hasTiles)
                id = map.tileAt(uu >> 16, vv >> 16, ceiling);
            short c = Tex.flatAt(id, uu >> 16, vv >> 16);
            if (c != 0)
                px[row + x] = shade(c, dist);
            uu += du;
            vv += dv;
        }
    }

    /* Billboard from Render_drawSprite: project the centre, reject on
     * column depth, sample the bitshape. Wall-hung decals (0x800000)
     * are a short segment on the wall axis. */
    static void drawSprite(int i) {
        int flags = map.sflags[i];
        if ((flags & 0x10000) != 0)
            return;
        int x = map.sx[i];
        int y = map.sy[i];
        int z = map.sz[i];
        int frame = map.sflags[i] & 15;
        if ((flags & 0x780000) == 0 && Game.facesPlayer(i)) {
            int dx = camX - x;
            int dy = camY - y;
            int ang = (int) (Math.atan2(-dy, dx) * 512 / Math.PI);
            frame = ((ang - camAng + 64) >> 7) & 7;
        }
        int tile = Mappings.shapeIndex(map.stile[i], frame);
        if (tile < 0)
            return;
        int[] box = Sprites.bounds(tile);
        int x0 = box == null ? 0 : box[0];
        int x1 = box == null ? 87 : box[1];
        int y0 = box == null ? 0 : box[2];
        int y1 = box == null ? 87 : box[3];
        if ((flags & 0x780000) != 0) {
            drawDecal(i, x, y, z, flags, tile, x0, x1, y0, y1);
            return;
        }
        lx1[0] = x;
        lx1[1] = y;
        lx1[2] = 0;
        transform(lx1);
        if (lx1[0] < NEAR_PLANE)
            return;
        int scale = div(mul64(projH, 0x10000), lx1[0]);
        int col = projX + (int) (mul64(lx1[1], scale) >> 17);
        int depth = (0x40000000 / (scale == 0 ? 1 : scale)) << 2;
        int bw = x1 - x0 + 1;
        int bh = y1 - y0 + 1;
        /* 88 source pixels map to 64 world units, same on both axes. */
        int width = rmul(scale, bw * 64 / 88) >> 17;
        int height = rmul(scale, bh * 64 / 88) >> 17;
        if (width < 1 || height < 1)
            return;
        int left = (col >> 16) - (rmul(scale, 64) >> 18) + (rmul(scale, x0 * 64 / 88) >> 17);
        int foot = centerY + ((rmul(scale, 0x2a - (z - 0x20)) + 0x8000) >> 17);
        int top = foot - (rmul(scale, (88 - y0) * 64 / 88) >> 17);
        for (int c = 0; c < width; c++) {
            int sx = left + c;
            if (sx < 0 || sx >= VIEW_W)
                continue;
            if (columnDepth[sx] < depth)
                continue;
            columnDepth[sx] = depth;
            int u = x0 + c * bw / width;
            for (int r = 0; r < height; r++) {
                int sy = top + r;
                if (sy < 0 || sy >= VIEW_H)
                    continue;
                short pix = Sprites.pixel(tile, u, y0 + r * bh / height);
                if (pix == 0)
                    continue;
                fb.px[(viewY0 + sy) * Framebuffer.W + viewX0 + sx] = pix;
            }
        }
    }

    static void drawDecal(int i, int x, int y, int z, int flags, int tile, int x0, int x1, int y0, int y1) {
        int bw = x1 - x0 + 1;
        int worldW = bw * 64 / 88;
        if (worldW < 1)
            worldW = 1;
        int half = worldW / 2;
        if (half < 1)
            half = 1;
        int x1w = x, y1w = y, x2w = x, y2w = y;
        if ((flags & 0x80000) != 0 || (flags & 0x100000) != 0) {
            x1w = x - half;
            x2w = x + half;
        } else {
            y1w = y - half;
            y2w = y + half;
        }
        lx1[0] = x1w;
        lx1[1] = y1w;
        lx1[2] = z;
        lx2[0] = x2w;
        lx2[1] = y2w;
        lx2[2] = z;
        transform(lx1);
        transform(lx2);
        if (lx1[0] < NEAR_PLANE && lx2[0] < NEAR_PLANE)
            return;
        project(lx1, projH);
        project(lx2, projH);
        int dx = lx2[0] - lx1[0];
        if (dx < 0) {
            int t = lx1[0]; lx1[0] = lx2[0]; lx2[0] = t;
            t = lx1[1]; lx1[1] = lx2[1]; lx2[1] = t;
            t = lx1[2]; lx1[2] = lx2[2]; lx2[2] = t;
            dx = -dx;
        }
        if (dx == 0)
            return;
        int yv = lx1[1];
        int dY = div(mul64(lx2[1] - yv, 0x10000), dx);
        int sx0 = (lx1[0] + 0xffff) >> 16;
        int sx1 = (lx2[0] + 0xffff) >> 16;
        if (sx0 < 0)
            sx0 = 0;
        if (sx1 > VIEW_W)
            sx1 = VIEW_W;
        int frac = (sx0 << 16) - lx1[0];
        yv += (int) (mul64(frac, dY) >> 16);
        int bh = y1 - y0 + 1;
        int span = sx1 - sx0;
        if (span < 1)
            span = 1;
        for (int c = 0; sx0 < sx1; sx0++, c++, yv += dY) {
            if (yv <= 0)
                continue;
            int depth = (0x40000000 / yv) << 2;
            if (columnDepth[sx0] < depth)
                continue;
            int h = rmul(yv, bh * 64 / 88) >> 17;
            if (h < 1)
                continue;
            int sy = centerY + ((rmul(yv, 0x2a - (z - 0x20)) + 0x8000) >> 17) - (rmul(yv, (88 - y0) * 64 / 88) >> 17);
            int u = x0 + c * bw / span;
            for (int r = 0; r < h; r++) {
                int yy = sy + r;
                if (yy < 0 || yy >= VIEW_H)
                    continue;
                short pix = Sprites.pixel(tile, u, y0 + r * bh / h);
                if (pix != 0)
                    fb.px[(viewY0 + yy) * Framebuffer.W + viewX0 + sx0] = pix;
            }
        }
    }

    /* Visible stand-in when the wall sheet is missing: shade by column
     * so the span still reads as geometry, matching the old untextured pass. */
    /* Phone span drawer 0 writes pal[texel] unchanged. Distance darkening
     * is not part of Render_rasterizeWall; shade there is a blend mode. */
    static short shade(short c, int scale) {
        return c;
    }

    static short fallback(int tex, int row, int n) {
        int shade = 90 + (tex & 7) * 12 + (row * 40 / (n < 1 ? 1 : n));
        if (shade > 220)
            shade = 220;
        return (short) Framebuffer.rgb(shade, shade - 24, shade / 3);
    }

    static int rmul(int a, int b) {
        return (int) ((long) a * (long) b);
    }

    static long mul64(long a, int b) {
        return a * (long) b;
    }

    static int div(long num, int den) {
        if (den == 0)
            return 0;
        return (int) (num / den);
    }

    static int smulhi(int a, int b) {
        return (int) (((long) a * (long) b) >> 32);
    }
}
