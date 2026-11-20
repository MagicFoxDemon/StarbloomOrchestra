package dev.starbloom.orchestra.client.music;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** 乐器界面的羊皮纸背景（tools/parchment_art.py 画的像素画），九宫格拉伸（木框、黄铜包角不被拉伸）。 */
final class Parchment {
    static final ResourceLocation TEXTURE = dev.starbloom.orchestra.StarbloomOrchestra.id("textures/gui/parchment.png");
    private static final int TEX_W = 1600, TEX_H = 896;
    private static final int[] SRC_X = {0, 128, 1472, 1600};
    private static final int[] SRC_Y = {0, 128, 768, 896};

    static final int INK = 0xFF3A2414;
    static final int INK_TITLE = 0xFF5A3410;
    static final int INK_HINT = 0xFF7A5A3A;
    static final int BRASS = 0xFF9C7A30;
    static final int GOLD = 0xFFE0B84A;

    private Parchment() {}

    static void draw(GuiGraphics g, int x, int y, int w, int h) {
        draw(g, x, y, w, h, 40);
    }

    /** frame：四角最大边长；小一些框就细一些（手持乐器的演奏条用）。 */
    static void draw(GuiGraphics g, int x, int y, int w, int h, int frame) {
        int corner = Math.min(frame, Math.min(w, h) / 2);
        int[] dx = {x, x + corner, x + w - corner, x + w};
        int[] dy = {y, y + corner, y + h - corner, y + h};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                // 目标矩形 dx/dy，源矩形是贴图上的整像素格；UV = 源像素 / 贴图边长，和按归一化 UV 画是同一块
                g.blit(TEXTURE, dx[col], dy[row], dx[col + 1] - dx[col], dy[row + 1] - dy[row],
                    SRC_X[col], SRC_Y[row], SRC_X[col + 1] - SRC_X[col], SRC_Y[row + 1] - SRC_Y[row], TEX_W, TEX_H);
            }
        }
    }
}
