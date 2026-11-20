package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * v1.2.0：HUD 布局编辑的统一读写入口。
 *
 * <p>把散落在 {@link ClientConfig} 里的五块 HUD（顶部比分 / 小地图 / 物品栏 / 状态条 / 受击指示）
 * 统一成一套「显示 · 大小 · 透明度 · 位置 · 复位」接口，供 {@link HudLayoutScreen}
 * 和游戏内 HUD 共用，避免两边算法各写一份跑偏。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HudLayout {

    /** 可编辑的 HUD 部件。 */
    public enum Element {
        SCORE("顶部比分"),
        RADAR("小地图"),
        HOTBAR("物品栏"),
        STATUS("状态条"),
        HITDIR("受击指示");

        public final String label;

        Element(String label) {
            this.label = label;
        }
    }

    public static final Element[] ALL = Element.values();

    private static final int OFFSET_LIMIT = 2000;

    private HudLayout() {
    }

    // ------------------------------------------------------------------ 显示开关

    public static boolean enabled(Element e) {
        return switch (e) {
            case SCORE -> ClientConfig.scoreEnabled;
            case RADAR -> ClientConfig.radarEnabled;
            case HOTBAR -> ClientConfig.hotbarEnabled;
            case STATUS -> ClientConfig.statusEnabled;
            case HITDIR -> ClientConfig.hitDirEnabled;
        };
    }

    public static void setEnabled(Element e, boolean on) {
        switch (e) {
            case SCORE -> ClientConfig.scoreEnabled = on;
            case RADAR -> ClientConfig.radarEnabled = on;
            case HOTBAR -> ClientConfig.hotbarEnabled = on;
            case STATUS -> ClientConfig.statusEnabled = on;
            case HITDIR -> ClientConfig.hitDirEnabled = on;
        }
    }

    // ------------------------------------------------------------------ 大小

    public static double scale(Element e) {
        return switch (e) {
            case SCORE -> ClientConfig.scoreScale;
            case RADAR -> ClientConfig.radarScale;
            case HOTBAR -> ClientConfig.hotbarScale;
            case STATUS -> ClientConfig.statusScale;
            case HITDIR -> ClientConfig.hitDirScale;
        };
    }

    public static void setScale(Element e, double v) {
        v = clamp(v, minScale(e), maxScale(e));
        switch (e) {
            case SCORE -> ClientConfig.scoreScale = v;
            case RADAR -> ClientConfig.radarScale = v;
            case HOTBAR -> ClientConfig.hotbarScale = v;
            case STATUS -> ClientConfig.statusScale = v;
            case HITDIR -> ClientConfig.hitDirScale = v;
        }
    }

    public static double minScale(Element e) {
        return e == Element.HITDIR ? 0.4 : (e == Element.SCORE ? 0.5 : 0.5);
    }

    public static double maxScale(Element e) {
        return switch (e) {
            case SCORE -> 2.0;
            case RADAR, HOTBAR, STATUS -> 3.0;
            case HITDIR -> 2.5;
        };
    }

    // ------------------------------------------------------------------ 透明度

    public static double alpha(Element e) {
        return switch (e) {
            case SCORE -> ClientConfig.scoreAlpha;
            case RADAR -> ClientConfig.radarAlpha;
            case HOTBAR -> ClientConfig.hotbarAlpha;
            case STATUS -> ClientConfig.statusAlpha;
            case HITDIR -> ClientConfig.hitDirAlpha;
        };
    }

    public static void setAlpha(Element e, double v) {
        v = clamp(v, 0.2, 1.0);
        switch (e) {
            case SCORE -> ClientConfig.scoreAlpha = v;
            case RADAR -> ClientConfig.radarAlpha = v;
            case HOTBAR -> ClientConfig.hotbarAlpha = v;
            case STATUS -> ClientConfig.statusAlpha = v;
            case HITDIR -> ClientConfig.hitDirAlpha = v;
        }
    }

    // ------------------------------------------------------------------ 位置 / 外框

    /** 部件在屏幕上的外框 {x, y, w, h}。 */
    public static int[] bounds(Element e, int screenW, int screenH) {
        return switch (e) {
            case SCORE -> TcpHud.scoreBounds(screenW, screenH);
            case RADAR -> TcpTacticalMapHud.bounds(screenW, screenH);
            case HOTBAR -> TcpHotbarHud.hotbarRect(screenW, screenH, TcpHotbarHud.hotbarScale());
            case STATUS -> TcpStatusHud.bounds(screenW, screenH);
            case HITDIR -> TcpHitDirHud.bounds(screenW, screenH);
        };
    }

    /** 拖动：把增量写进配置（小地图会自动从「吸附物品栏」切到九宫格锚点，避免跟着物品栏跑）。 */
    public static void move(Element e, int dx, int dy, int screenW, int screenH) {
        if (dx == 0 && dy == 0) return;

        switch (e) {
            case SCORE -> {
                ClientConfig.scoreOffsetX = clampInt(ClientConfig.scoreOffsetX + dx);
                ClientConfig.scoreOffsetY = clampInt(ClientConfig.scoreOffsetY + dy);
            }
            case HOTBAR -> {
                ClientConfig.hotbarOffsetX = clampInt(ClientConfig.hotbarOffsetX + dx);
                ClientConfig.hotbarOffsetY = clampInt(ClientConfig.hotbarOffsetY + dy);
            }
            case STATUS -> {
                ClientConfig.statusOffsetX = clampInt(ClientConfig.statusOffsetX + dx);
                ClientConfig.statusOffsetY = clampInt(ClientConfig.statusOffsetY + dy);
            }
            case HITDIR -> {
                ClientConfig.hitDirOffsetX = clampInt(ClientConfig.hitDirOffsetX + dx);
                ClientConfig.hitDirOffsetY = clampInt(ClientConfig.hitDirOffsetY + dy);
            }
            case RADAR -> {
                int[] b = bounds(Element.RADAR, screenW, screenH);
                int nx = b[0] + dx;
                int ny = b[1] + dy;
                int cx = nx + b[2] / 2;
                int cy = ny + b[3] / 2;

                ClientConfig.radarSnapAboveHotbar = false;
                String anchor = pickAnchor(cx, cy, screenW, screenH);
                int[] base = HudPos.place(anchor, b[2], b[3], screenW, screenH, 0, 0);
                ClientConfig.radarAnchor = anchor;
                // 别拖出屏幕：拖出边缘时按锚点基准回推
                ClientConfig.radarOffsetX = clampInt(nx - base[0]);
                ClientConfig.radarOffsetY = clampInt(ny - base[1]);
            }
        }
    }

    /** 位置复位（只动位置，不动大小/透明度/开关）。 */
    public static void resetPosition(Element e) {
        switch (e) {
            case SCORE -> {
                ClientConfig.scoreOffsetX = 0;
                ClientConfig.scoreOffsetY = 0;
            }
            case HOTBAR -> {
                ClientConfig.hotbarOffsetX = 0;
                ClientConfig.hotbarOffsetY = 0;
            }
            case STATUS -> {
                ClientConfig.statusOffsetX = 0;
                ClientConfig.statusOffsetY = 0;
            }
            case HITDIR -> {
                ClientConfig.hitDirOffsetX = 0;
                ClientConfig.hitDirOffsetY = 0;
            }
            case RADAR -> {
                ClientConfig.radarAnchor = "BOTTOM_RIGHT";
                ClientConfig.radarOffsetX = 0;
                ClientConfig.radarOffsetY = -78;
                ClientConfig.radarSnapAboveHotbar = true;
            }
        }
    }

    /** 恢复某块 HUD 的全部默认值。 */
    public static void resetElement(Element e) {
        setEnabled(e, true);
        switch (e) {
            case SCORE -> {
                ClientConfig.scoreScale = 1.0;
                ClientConfig.scoreAlpha = 1.0;
            }
            case RADAR -> {
                ClientConfig.radarScale = 1.0;
                ClientConfig.radarAlpha = 1.0;
            }
            case HOTBAR -> {
                ClientConfig.hotbarScale = 1.0;
                ClientConfig.hotbarAlpha = 1.0;
            }
            case STATUS -> {
                ClientConfig.statusScale = 1.0;
                ClientConfig.statusAlpha = 1.0;
            }
            case HITDIR -> {
                ClientConfig.hitDirScale = 1.0;
                ClientConfig.hitDirAlpha = 0.8;
            }
        }
        resetPosition(e);
    }

    // ------------------------------------------------------------------ 工具

    private static String pickAnchor(int cx, int cy, int screenW, int screenH) {
        String v;
        if (cy < screenH / 3) v = "TOP";
        else if (cy > screenH * 2 / 3) v = "BOTTOM";
        else v = "MIDDLE";

        String h;
        if (cx < screenW / 3) h = "LEFT";
        else if (cx > screenW * 2 / 3) h = "RIGHT";
        else h = "CENTER";

        return v + "_" + h;
    }

    private static int clampInt(int v) {
        return Math.max(-OFFSET_LIMIT, Math.min(OFFSET_LIMIT, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
