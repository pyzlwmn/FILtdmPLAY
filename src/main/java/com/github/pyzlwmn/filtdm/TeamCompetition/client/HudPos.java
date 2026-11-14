package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * v80：HUD 锚点定位（配置里写 TOP_LEFT / BOTTOM_CENTER 这种字符串）。
 */
@OnlyIn(Dist.CLIENT)
public final class HudPos {

    private static final int EDGE = 12;
    private static final int CENTER_GAP = 8;

    private HudPos() {
    }

    /**
     * @return {@code {x, y}} 左上角坐标
     */
    public static int[] place(String anchor, int w, int h, int screenW, int screenH, int offX, int offY) {
        String a = anchor == null ? "BOTTOM_RIGHT" : anchor.trim().toUpperCase();

        int x;
        int y;
        if (a.contains("LEFT")) {
            x = EDGE;
        } else if (a.contains("CENTER")) {
            x = (screenW - w) / 2;
        } else {
            x = screenW - w - EDGE;
        }

        if (a.startsWith("TOP")) {
            y = EDGE;
        } else if (a.startsWith("MIDDLE")) {
            y = (screenH - h) / 2;
        } else {
            y = screenH - h - EDGE;
        }

        // 中心锚点留一点和原版 HUD / 物品栏的间距
        if (a.contains("CENTER") && a.startsWith("BOTTOM")) y -= CENTER_GAP;

        return new int[]{x + offX, y + offY};
    }
}
