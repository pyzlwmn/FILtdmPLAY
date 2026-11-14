package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpTacticalMapS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpKillFeedS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.ptcrys.fpsmatch.common.client.screen.hud.IHudRenderer;

import java.util.List;

/**
 * v80：战术地图雷达（右下角圆盘）。
 *
 * <p>画什么完全由服务端包决定：
 * 队友常驻（死亡画 ❌）、敌人只在「被看见」时出现（带 0.3s 淡出由服务端 linger 控制）。
 * 皮肤：包内带玩家当前主武器皮肤 id，这里按 skin id 哈希出一个固定颜色，
 * 后续要换成真图标只改 {@link #skinColor(String)} 一处即可。</p>
 */
@OnlyIn(Dist.CLIENT)
public class TcpTacticalMapHud implements IHudRenderer {

    /** 基准半径（配置 scale=1.0 时）。 */
    private static final int RADIUS = 78;
    private static final int MARGIN = 14;
    private static final int C_BG = 0xB00B1018;
    private static final int C_RING = 0x66A8B6C8;
    private static final int C_RED = 0xFFE63946;
    private static final int C_BLUE = 0xFF3B82F6;
    private static final int C_SELF = 0xFFF5F7FA;
    private static final int C_DEAD = 0x99FF6B6B;
    private static final int C_TICK = 0x55202836;

    @Override
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
    }

    @Override
    public void onSpectatorRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        render(g, screenWidth, screenHeight);
    }

    @Override
    public void onPlayerRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        render(g, screenWidth, screenHeight);
    }

    private void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player self = mc.player;
        if (self == null) return;
        // v86：打开任何界面时隐藏
        if (mc.screen != null) return;
        if (!TcpTacticalMapData.fresh()) return;
        if (!ClientConfig.radarEnabled) return;

        List<TcpTacticalMapS2CPacket.Blip> blips = TcpTacticalMapData.blips();
        float range = Math.max(8.0f, TcpTacticalMapData.range());

        // ★ v80：大小 / 位置全部走客户端配置
        float rs = (float) Math.max(0.5, ClientConfig.radarScale);
        int radius = Math.max(24, Math.round(RADIUS * rs));
        int diameter = radius * 2;
        String anchor = ClientConfig.radarAnchor == null
                ? "BOTTOM_RIGHT" : ClientConfig.radarAnchor.trim().toUpperCase();
        int[] pos;
        if (ClientConfig.radarSnapAboveHotbar && anchor.startsWith("BOTTOM")) {
            // ★ v81：直接吸附在物品栏正上方，右/左边缘对齐，永不错位
            int[] hb = TcpHotbarHud.hotbarRect(width, height, TcpHotbarHud.hotbarScale());
            int x = anchor.contains("LEFT") ? hb[0] : hb[0] + hb[2] - diameter;
            int y = hb[1] - diameter - 8;
            pos = new int[]{x + ClientConfig.radarOffsetX, y + ClientConfig.radarOffsetY};
        } else {
            pos = HudPos.place(anchor, diameter, diameter, width, height,
                    ClientConfig.radarOffsetX, ClientConfig.radarOffsetY);
        }
        // 兜底：别画出屏幕
        pos[0] = Math.max(4, Math.min(pos[0], width - diameter - 4));
        pos[1] = Math.max(4, Math.min(pos[1], height - diameter - 4));
        int cx = pos[0] + radius;
        int cy = pos[1] + radius;

        // 底盘
        disc(g, cx, cy, radius, C_BG);

        // ★ v88：地形层（纹理里已烘好圆形遮罩，旋转后仍是正圆）
        if (ClientConfig.radarTerrain && RadarTerrain.ready()) {
            var tp = g.pose();
            tp.pushPose();
            tp.translate(cx, cy, 0);
            tp.mulPose(new org.joml.Quaternionf().rotateZ(
                    (float) Math.toRadians(-self.getYRot())));
            tp.translate(-radius, -radius, 0);
            g.blit(RadarTerrain.textureId(), 0, 0, 0.0f, 0.0f,
                    diameter, diameter, RadarTerrain.GRID, RadarTerrain.GRID);
            tp.popPose();
        }
        ring(g, cx, cy, radius, C_RING);
        // 十字刻度
        g.fill(cx - radius + 4, cy, cx + radius - 4, cy + 1, C_TICK);
        g.fill(cx, cy - radius + 4, cx + 1, cy + radius - 4, C_TICK);

        // 自己（始终在中心，箭头朝上）
        triangle(g, cx, cy, 4, C_SELF, 0f);

        double px = self.getX();
        double pz = self.getZ();
        float viewerYaw = self.getYRot();
        double fx = -Math.sin(Math.toRadians(viewerYaw));
        double fz = Math.cos(Math.toRadians(viewerYaw));
        double scale = (radius - 8) / (double) range;
        int viewerTeam = TcpTacticalMapData.viewerTeam();
        boolean spectator = TcpTacticalMapData.spectator();

        for (TcpTacticalMapS2CPacket.Blip b : blips) {
            if (b.id().equals(self.getUUID())) continue;

            boolean mate = spectator || b.team() == viewerTeam;
            if (!mate && !b.visible()) continue;

            double dx = b.x() - px;
            double dz = b.z() - pz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range) continue;

            // 以玩家朝向为「上」旋转
            double fwd = dx * fx + dz * fz;
            double rgt = -dx * fz + dz * fx;

            int sx = cx + (int) Math.round(rgt * scale);
            int sy = cy - (int) Math.round(fwd * scale);

            int color = mate ? skinColor(b.skin(), b.team()) : C_RED;

            if (mate && !b.alive()) {
                cross(g, sx, sy, 3, C_DEAD);
                continue;
            }

            dot(g, sx, sy, 3, color);

            // 朝向短线（相对视角）：敌人线更长更醒目
            float rel = b.yaw() - viewerYaw;
            double rad = Math.toRadians(rel);
            double hx = -Math.sin(rad);
            double hz = Math.cos(rad);
            double hf = hx * fx + hz * fz;
            double hr = -hx * fz + hz * fx;
            int lineLen = mate ? 6 : 11;
            int ex = sx + (int) Math.round(hr * lineLen);
            int ey = sy - (int) Math.round(hf * lineLen);
            line(g, sx, sy, ex, ey, color);
            if (!mate) {
                // 敌人线末端加个亮点，远看更清楚
                g.fill(ex, ey, ex + 2, ey + 2, color);
            }
        }
    }

    /** 皮肤 id → 颜色（哈希取色相，同皮肤永远同色；空皮肤用队伍色）。 */
    private static int skinColor(String skin, int team) {
        if (skin == null || skin.isEmpty()) {
            return team == TcpKillFeedS2CPacket.TEAM_RED ? C_RED : C_BLUE;
        }
        int h = Math.abs(skin.hashCode());
        float hue = (h % 360) / 360.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int steps = 180;
        int prevX = cx + r;
        int prevY = cy;
        for (int i = 1; i <= steps; i++) {
            double a = Math.PI * 2.0 * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy + (int) Math.round(Math.sin(a) * r);
            line(g, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    private static void dot(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    private static void cross(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
        }
    }

    private static void triangle(GuiGraphics g, int cx, int cy, int size, int color, float rotDeg) {
        double a = Math.toRadians(rotDeg);
        int x1 = cx + (int) Math.round(Math.sin(a) * size);
        int y1 = cy - (int) Math.round(Math.cos(a) * size);
        int x2 = cx + (int) Math.round(Math.sin(a + 2.5) * size);
        int y2 = cy - (int) Math.round(Math.cos(a + 2.5) * size);
        int x3 = cx + (int) Math.round(Math.sin(a - 2.5) * size);
        int y3 = cy - (int) Math.round(Math.cos(a - 2.5) * size);
        line(g, x1, y1, x2, y2, color);
        line(g, x2, y2, x3, y3, color);
        line(g, x3, y3, x1, y1, color);
    }

    /** Bresenham，用来画圆环 / 朝向线 / 三角。 */
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int guard = 0;
        while (guard++ < 512) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
