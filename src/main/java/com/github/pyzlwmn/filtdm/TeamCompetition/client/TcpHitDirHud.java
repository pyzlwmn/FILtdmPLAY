package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.ptcrys.fpsmatch.common.client.screen.hud.IHudRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ★ v1.1.0 / v1.1.2：受击方向指示器（HUD）。
 *
 * <p>屏幕正中有一个<b>不可见的虚拟圆</b>（只在代码里存在，不画任何线），
 * 被谁打中，就在圆周上「朝那个方向」画一个半透明三角：</p>
 * <pre>
 *   正前方打中 → 三角在 12 点       右侧打中 → 3 点
 *   背后打中   → 三角在 6 点       左侧打中 → 9 点
 * </pre>
 *
 * <p>角度来源：服务端在命中那一刻算一次<b>世界方位角</b>（用开火者当时的位置），
 * 客户端每帧只做 {@code wrapDegrees(bearing - playerYaw)}，
 * 所以<b>只有玩家自己转视角时三角才沿圆滑动</b>；开火者之后怎么跑都不影响。</p>
 *
 * <p>v1.1.2：<b>打开聊天栏 / Esc 菜单时也照样显示</b>（走 {@link ScreenEvent.Render.Post} 覆盖层），
 * 位置比平时再往下偏移 {@code hitdir.screenOffsetY}，避免挡住聊天内容。</p>
 */
@OnlyIn(Dist.CLIENT)
public class TcpHitDirHud implements IHudRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-HITDIR-C");

    private static final float BASE_RADIUS = 120f;
    private static final float BASE_SIZE = 11f;

    /** 淡出段时长（秒） */
    private static final float FADE_OUT = 0.70f;

    /** 调试日志节流（毫秒） */
    private static long lastDiagMs = 0L;

    /** 受击指示器可抓取范围（布局编辑器用）：以虚拟圆心为中心的正方形。 */
    public static int[] bounds(int screenWidth, int screenHeight) {
        float r = (float) Math.max(20.0, ClientConfig.hitDirRadius);
        int cx = screenWidth / 2 + ClientConfig.hitDirOffsetX;
        int cy = screenHeight / 2 + ClientConfig.hitDirOffsetY;
        int rr = Math.round(r);
        return new int[]{cx - rr, cy - rr, rr * 2, rr * 2};
    }

    /** 给布局编辑器画一个参考三角（相对角 90° = 右侧），alpha 由调用方给。 */
    public static void previewTriangle(GuiGraphics g, int screenWidth, int screenHeight, float relDeg, float alpha) {
        float cx = screenWidth / 2f + ClientConfig.hitDirOffsetX;
        float cy = screenHeight / 2f + ClientConfig.hitDirOffsetY;
        drawTriangle(g, cx, cy, relDeg, Mth.clamp(alpha, 0f, 1f));
    }

    // ------------------------------------------------------------------ 两种渲染入口

    @Override
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        // 不改原版任何 overlay
    }

    @Override
    public void onSpectatorRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        // 观战不画
    }

    @Override
    public void onPlayerRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (Minecraft.getInstance().screen != null) return;   // 有界面时交给 ScreenHook 画
        // ★ v1.1.5：死亡界面显示时让位，避免和死亡信息重叠
        if (com.github.pyzlwmn.filtdm.TeamCompetition.client.spec.DeathUiRenderer.isActive()) return;
        draw(g, screenWidth, screenHeight, false);
    }

    /**
     * 界面打开时（聊天栏 / Esc / 背包…）由这里补画，位置比平时更靠下。
     */
    @Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ScreenHook {
        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            if (!ClientConfig.hitDirScreenVisible) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            draw(event.getGuiGraphics(),
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(),
                    true);
        }
    }

    // ------------------------------------------------------------------ 核心绘制

    private static void draw(GuiGraphics g, int screenWidth, int screenHeight, boolean overScreen) {
        if (!ClientConfig.hitDirEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;

        if (!TcpHitDirData.active()) {
            diag("no data");
            return;
        }

        float duration = (float) ClientConfig.hitDirDuration;
        if (duration <= 0.05f) duration = 3.0f;

        float elapsed = (System.currentTimeMillis() - TcpHitDirData.hitAtMs()) / 1000f;
        if (elapsed < 0f) elapsed = 0f;
        if (elapsed >= duration) {
            diag("expired " + String.format("%.2f", elapsed));
            return;
        }

        float alpha = alphaCurve(elapsed, duration) * (float) ClientConfig.hitDirAlpha;
        float flash = Mth.clamp(1f - elapsed / 0.25f, 0f, 1f);
        alpha = Mth.clamp(alpha + 0.15f * flash, 0f, 1f);
        if (alpha <= 0.01f) return;

        // 相对角：0 = 正前方（屏幕上方），顺时针为正
        float rel = Mth.wrapDegrees(TcpHitDirData.bearing() - player.getYRot());

        float cx = screenWidth / 2f + ClientConfig.hitDirOffsetX;
        float cy = screenHeight / 2f + ClientConfig.hitDirOffsetY
                + (overScreen ? ClientConfig.hitDirScreenOffsetY : 0);

        drawTriangle(g, cx, cy, rel, alpha);

        diag("DRAW rel=" + String.format("%.1f", rel) + " alpha=" + String.format("%.2f", alpha)
                + " at " + (int) cx + "," + (int) cy + " overScreen=" + overScreen);
    }

    /**
     * 预览界面用：直接画一个指定角度 / 透明度 / 圆心的三角（与游戏内同一套绘制）。
     */
    public static void drawPreview(GuiGraphics g, int screenWidth, int screenHeight,
                                   float relDeg, float alpha, int centerY) {
        float cx = screenWidth / 2f + ClientConfig.hitDirOffsetX;
        float cy = centerY + ClientConfig.hitDirOffsetY;
        drawTriangle(g, cx, cy, relDeg, Mth.clamp(alpha, 0f, 1f));
    }

    /** 核心绘制：以 (cx, cy) 为圆心，按相对角 relDeg 在圆周上画三角。 */
    private static void drawTriangle(GuiGraphics g, float cx, float cy, float relDeg, float alpha) {
        double rad = Math.toRadians(relDeg);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        float radius = (float) (ClientConfig.hitDirRadius > 0 ? ClientConfig.hitDirRadius : BASE_RADIUS);
        float size = (float) (BASE_SIZE * Math.max(0.4, ClientConfig.hitDirScale));

        double px = cx + sin * radius;
        double py = cy - cos * radius;

        // 三角形：apex 朝外（指向敌人），底边在内侧
        double apexX = px + sin * size;
        double apexY = py - cos * size;
        double halfBase = size * 0.78;
        double baseX = px - sin * size * 0.55;
        double baseY = py + cos * size * 0.55;
        double perpX = cos;
        double perpY = sin;
        double b1x = baseX + perpX * halfBase;
        double b1y = baseY + perpY * halfBase;
        double b2x = baseX - perpX * halfBase;
        double b2y = baseY - perpY * halfBase;

        int rgb = colorOf(ClientConfig.hitDirColor);
        int core = (Math.round(alpha * 255f) << 24) | rgb;

        // 外发光：以质心为中心放大 1.7 倍，低透明度垫在底层
        double gx = (apexX + b1x + b2x) / 3.0;
        double gy = (apexY + b1y + b2y) / 3.0;
        double k = 1.70;
        int glowColor = (Math.round(alpha * 60f) << 24) | rgb;
        fillTri(g,
                gx + (apexX - gx) * k, gy + (apexY - gy) * k,
                gx + (b1x - gx) * k, gy + (b1y - gy) * k,
                gx + (b2x - gx) * k, gy + (b2y - gy) * k,
                glowColor);

        fillTri(g, apexX, apexY, b1x, b1y, b2x, b2y, core);

        // 内侧高光（暗场景更好认，不抢主色）
        int hi = (Math.round(alpha * 70f) << 24) | 0xFFFFFF;
        double hk = 0.45;
        fillTri(g,
                gx + (apexX - gx) * hk, gy + (apexY - gy) * hk,
                gx + (b1x - gx) * hk, gy + (b1y - gy) * hk,
                gx + (b2x - gx) * hk, gy + (b2y - gy) * hk,
                hi);
    }

    /** 命中后保持满亮，末段淡出。返回 0~1 倍率（不含用户 alpha）。 */
    private static float alphaCurve(float t, float duration) {
        float holdEnd = Math.max(0.01f, duration - FADE_OUT);
        if (t < holdEnd) return 1.0f;
        float k = (t - holdEnd) / Math.max(0.05f, duration - holdEnd);
        return Math.max(0f, 1f - k);
    }

    /** 诊断日志：最多 1 秒一条，避免刷屏。 */
    private static void diag(String why) {
        long now = System.currentTimeMillis();
        if (now - lastDiagMs < 1000L) return;
        lastDiagMs = now;
        LOGGER.info("[FIL-HITDIR-C] {}", why);
    }

    private static int colorOf(String name) {
        if (name == null) return 0xFF5B4A;
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "WHITE" -> 0xFFFFFF;
            case "AMBER" -> 0xFFCE54;
            case "CYAN" -> 0x4DD2FF;
            default -> 0xFF5B4A;   // RED 伤害红
        };
    }

    /** 扫描线填充三角形（走 g.fill 横向跨度，一次一行）。 */
    private static void fillTri(GuiGraphics g,
                                double x1, double y1,
                                double x2, double y2,
                                double x3, double y3,
                                int color) {
        double minY = Math.min(y1, Math.min(y2, y3));
        double maxY = Math.max(y1, Math.max(y2, y3));
        int y0 = (int) Math.floor(minY);
        int yEnd = (int) Math.ceil(maxY);
        if (yEnd - y0 > 400) return;

        double[] xs = {x1, x2, x3};
        double[] ys = {y1, y2, y3};

        for (int y = y0; y < yEnd; y++) {
            double sy = y + 0.5;
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;

            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                double ax = xs[i], ay = ys[i], bx = xs[j], by = ys[j];
                if (Math.abs(ay - by) < 1.0E-6) {
                    if (Math.abs(sy - ay) < 0.5) {
                        lo = Math.min(lo, Math.min(ax, bx));
                        hi = Math.max(hi, Math.max(ax, bx));
                    }
                    continue;
                }
                double t = (sy - ay) / (by - ay);
                if (t < 0.0 || t > 1.0) continue;
                double x = ax + t * (bx - ax);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }

            if (hi > lo) {
                g.fill((int) Math.floor(lo), y, (int) Math.ceil(hi), y + 1, color);
            }
        }
    }
}
