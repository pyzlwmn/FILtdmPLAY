package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.UiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;

/**
 * 死亡 UI（v73，按 tdm-ui-design/death-ui.html 定稿实现，布局一行不改）。
 *
 * <p>分区：
 * <ul>
 *   <li>中上 13% —— 击杀横幅（骷髅 + 击杀者名 + 阵营标签）</li>
 *   <li>其下 —— 武器行（TaCZ 贴图 or 纯文字回退 + 弹药 + HEADSHOT）</li>
 *   <li>左下 —— 复活倒计时大字 + 渐变进度条 + 提示</li>
 *   <li>右下 —— 队伍比分快照（蓝/红 + 目标分 + 回合）</li>
 *   <li>中心 —— 留空，给尸体</li>
 * </ul>
 *
 * <p>配色严格对齐定稿：bg #0b0d12 / kill #ff3b4e / enemy #ff6b6b / ally #4da3ff / accent #ffc93c。
 */
@OnlyIn(Dist.CLIENT)
public final class DeathUiRenderer {

    // ---- 定稿配色 ----
    private static final int C_FG = 0xFFE8ECF4;
    private static final int C_MUTED = 0xFF8B95A8;
    private static final int C_KILL = 0xFFFF3B4E;
    private static final int C_ENEMY = 0xFFFF6B6B;
    private static final int C_ALLY = 0xFF4DA3FF;
    private static final int C_ACCENT = 0xFFFFC93C;
    private static final int C_ORANGE = 0xFFFF8A3C;

    private DeathUiRenderer() {
    }

    /**
     * 绘制整块死亡 UI。
     *
     * @param g            GuiGraphics
     * @param partialTick  渲染插值
     * @param isSpectator  当前是否旁观（死亡后服务端把玩家设成旁观）
     */
    public static void render(GuiGraphics g, float partialTick, boolean isSpectator) {
        if (!TcpDeathCameraClient.active()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();

        float elapsed = TcpDeathCameraClient.elapsedSeconds(partialTick);

        // ---- 底层视觉：暗角 + 顶部红晕 + 底部深色渐变 ----
        drawVignette(g, w, h, elapsed);
        drawBottomFade(g, w, h);

        // ---- 中上：击杀横幅 ----
        drawKillBanner(g, font, w, h, elapsed);

        // ---- 左下：复活倒计时 ----
        drawRespawn(g, font, w, h, partialTick);

        // ---- 右下：比分快照 ----
        drawScoreSnapshot(g, font, w, h);
    }

    // ==================== 视觉底层 ====================

    /** 红黑管状暗角，前 0.6s 淡入 */
    private static void drawVignette(GuiGraphics g, int w, int h, float elapsed) {
        float t = Math.min(1.0F, elapsed / 0.6F);

        // 顶部红色渐晕（受伤感）：高度 34%
        int topH = (int) (h * 0.34F);
        int a = (int) (0.34F * 255 * t);
        g.fillGradient(0, 0, w, topH, (a << 24) | 0x0096000C, 0x0096000C);

        // 四边暗角（用四条渐变模拟视网膜管状视野）
        int vA = (int) (0.55F * 255 * t);
        int vCol = (vA << 24);
        int bandX = (int) (w * 0.14F);
        int bandY = (int) (h * 0.12F);

        // 左
        g.fillGradient(0, 0, bandX, h, vCol, vCol);
        // 右
        g.fillGradient(w - bandX, 0, w, h, vCol, vCol);
        // 上
        g.fillGradient(0, 0, w, bandY, vCol, 0x00000000);
        // 下
        g.fillGradient(0, h - bandY, w, h, 0x00000000, vCol);

        // 四角加深
        int cA = (int) (0.72F * 255 * t);
        int cCol = (cA << 24);
        int cornerW = (int) (w * 0.20F);
        int cornerH = (int) (h * 0.22F);
        g.fillGradient(0, 0, cornerW, cornerH, cCol, 0x00000000);
        g.fillGradient(w - cornerW, 0, w, cornerH, cCol, 0x00000000);
    }

    /** 底部 58% 深色渐变，保证文字可读 */
    private static void drawBottomFade(GuiGraphics g, int w, int h) {
        int top = (int) (h * 0.42F);
        // 0x00 -> 0xF5 透明度，颜色 #06080C
        g.fillGradient(0, top, w, h, 0x0006080C, 0xF506080C);
        // 再压一层更深的底边（62% 以下）
        g.fillGradient(0, (int) (h * 0.62F), w, h, 0x0006080C, 0x8006080C);
    }

    // ==================== 击杀横幅 ====================

    private static void drawKillBanner(GuiGraphics g, Font font, int w, int h, float elapsed) {
        KillInfo info = TcpDeathCameraClient.lastKill();
        if (info == null) return;

        // 0.12s 后滑入，550ms 回弹；整体在 0.12~0.67s 完成
        float slide = clamp01((elapsed - 0.12F) / 0.55F);
        slide = easeOutBack(slide);
        // 1.5s 后淡到 55%
        float alpha = 1.0F;
        if (elapsed > 1.5F) {
            alpha = 1.0F - 0.45F * clamp01((elapsed - 1.5F) / 0.5F);
        }

        int bannerTop = (int) (h * 0.13F);
        int offsetY = (int) ((1.0F - slide) * -16.0F);

        int centerX = w / 2;
        int y = bannerTop + offsetY;

        // 「你被击杀」标签
        String label = "你 被 击 杀";
        int labelW = font.width(label);
        drawShadowed(g, font, label, centerX - labelW / 2, y, withAlpha(C_KILL, alpha), true);
        y += 16;

        // 骷髅图标（用简单几何拼，避免贴图依赖）
        int skullSize = 34;
        int skullX = centerX - skullSize / 2 - font.width(info.killer()) / 2 - 14;
        drawSkull(g, skullX, y, skullSize, withAlpha(C_KILL, alpha));

        // 击杀者名
        int nameColor = info.enemy() ? withAlpha(C_ENEMY, alpha) : withAlpha(C_ALLY, alpha);
        drawShadowed(g, font, info.killer(), skullX + skullSize + 12, y + 4, nameColor, true);
        y += 26;

        // 阵营标签
        String tag = info.enemy() ? "敌方 · 红队" : "友方 · 蓝队";
        int tagColor = info.enemy() ? C_KILL : C_ALLY;
        int tagW = font.width(tag) + 20;
        int tagX = centerX - tagW / 2;
        UiDraw.fillRounded(g, tagX, y, tagW, 14, 3, withAlpha(tagColor, 0.16F * alpha));
        drawShadowed(g, font, tag, tagX + 10, y + 3, withAlpha(C_FG, alpha * 0.9F), false);
        y += 22;

        // 武器行
        drawWeaponRow(g, font, centerX, y, info, alpha);
    }

    private static void drawWeaponRow(GuiGraphics g, Font font, int centerX, int y,
                                      KillInfo info, float alpha) {
        ItemStack gun = info.gun();
        String ammo = info.ammoText();
        boolean headshot = info.headshot();

        // 纯文字回退（TaCZ 贴图取不到时就是这条路）
        String gunName = gun == null || gun.isEmpty()
                ? "未知武器"
                : gun.getHoverName().getString();

        int textW = font.width(gunName);
        int ammoW = ammo == null ? 0 : font.width(ammo);
        int hsW = headshot ? font.width("HEADSHOT") + 18 : 0;
        int gap = 10;
        int total = textW + 20 + (ammoW > 0 ? ammoW + gap : 0) + (hsW > 0 ? hsW + gap : 0);
        int x = centerX - total / 2;

        // 武器名（琥珀色描边块）
        UiDraw.fillRounded(g, x - 6, y - 3, textW + 14, 16, 4, withAlpha(C_ACCENT, 0.09F * alpha));
        drawShadowed(g, font, gunName, x, y, withAlpha(C_ACCENT, alpha), true);
        x += textW + 20;

        if (ammoW > 0) {
            drawShadowed(g, font, ammo, x, y + 1, withAlpha(C_MUTED, alpha), false);
            x += ammoW + gap;
        }

        if (headshot) {
            UiDraw.fillRounded(g, x, y - 2, hsW, 14, 3, withAlpha(C_ACCENT, alpha));
            drawShadowed(g, font, "HEADSHOT", x + 9, y + 1, withAlpha(0xFF10131A, alpha), false);
        }
    }

    /** 简易骷髅：圆头 + 两个眼窝 + 下颚齿 */
    private static void drawSkull(GuiGraphics g, int x, int y, int s, int color) {
        int headH = (int) (s * 0.72F);
        // 头骨
        UiDraw.fillRounded(g, x, y, s, headH, (int) (s * 0.32F), color);
        // 下颚
        int jawW = (int) (s * 0.52F);
        UiDraw.fillRounded(g, x + (s - jawW) / 2, y + headH - 2, jawW, (int) (s * 0.24F), 3, color);
        // 眼窝
        int eye = (int) (s * 0.20F);
        int eyeY = y + (int) (headH * 0.32F);
        UiDraw.fillRounded(g, x + (int) (s * 0.20F), eyeY, eye, eye, eye / 2, 0xFF0B0D12);
        UiDraw.fillRounded(g, x + s - (int) (s * 0.20F) - eye, eyeY, eye, eye, eye / 2, 0xFF0B0D12);
        // 鼻
        int nose = (int) (s * 0.10F);
        UiDraw.fillRounded(g, x + s / 2 - nose / 2, eyeY + eye + 2, nose, nose + 2, 1, 0xFF0B0D12);
    }

    // ==================== 复活倒计时 ====================

    private static void drawRespawn(GuiGraphics g, Font font, int w, int h, float partialTick) {
        float remain = TcpDeathCameraClient.remainingSeconds(partialTick);
        float progress = TcpDeathCameraClient.progress(partialTick);

        int marginX = (int) Math.max(18, w * 0.03F);
        int bottomY = (int) (h - Math.max(14, h * 0.024F));

        // 从底往上排：提示 → 进度条 → 大字 → 标签
        int hintY = bottomY - font.lineHeight;

        String hint = "死亡视角锁定中 · 复活后恢复背包与装备";
        drawShadowed(g, font, hint, marginX, hintY, C_MUTED, false);

        int barH = 7;
        int barW = (int) Math.min(520, w * 0.42F);
        int barY = hintY - 12 - barH;
        UiDraw.fillRounded(g, marginX, barY, barW, barH, barH / 2, 0x1AFFFFFF);
        int fillW = (int) (barW * clamp01(progress));
        if (fillW > 0) {
            // 渐变条：kill → orange（用两段近似）
            int halfW = fillW / 2;
            UiDraw.fillRounded(g, marginX, barY, Math.max(2, halfW), barH, barH / 2, C_KILL);
            if (fillW > 2) {
                UiDraw.fillRounded(g, marginX + halfW, barY, Math.max(2, fillW - halfW), barH,
                        barH / 2, C_ORANGE);
            }
        }

        int numY = barY - 44;
        String num = String.format("%.1f", remain);
        g.pose().pushPose();
        g.pose().translate(marginX, numY, 0);
        g.pose().scale(3.2F, 3.2F, 1.0F);
        drawShadowed(g, font, num, 0, 0, C_FG, true);
        g.pose().popPose();

        int unitX = marginX + (int) (font.width(num) * 3.2F) + 10;
        drawShadowed(g, font, "秒后复活", unitX, numY + 26, C_MUTED, true);

        // 顶部标签
        drawShadowed(g, font, "复 活 倒 计 时", marginX, numY - 14, C_MUTED, false);
    }

    // ==================== 比分快照 ====================

    private static void drawScoreSnapshot(GuiGraphics g, Font font, int w, int h) {
        int blue = TcpDeathCameraClient.scoreBlue();
        int red = TcpDeathCameraClient.scoreRed();
        int target = TcpDeathCameraClient.scoreTarget();

        int marginX = (int) Math.max(18, w * 0.03F);
        int bottomY = (int) (h - Math.max(14, h * 0.024F));

        int boxW = 62;
        int boxH = 44;
        int gap = 10;
        int rightX = w - marginX;

        int redX = rightX - boxW;
        int blueX = redX - gap - boxW;
        int boxY = bottomY - boxH - 16;

        drawTeamBox(g, font, blueX, boxY, boxW, boxH, "蓝队", blue, C_ALLY);
        drawTeamBox(g, font, redX, boxY, boxW, boxH, "红队", red, C_ENEMY);

        String meta = "目标 " + target;
        int metaW = font.width(meta);
        drawShadowed(g, font, meta, rightX - metaW, bottomY - font.lineHeight, C_MUTED, false);
    }

    private static void drawTeamBox(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    String label, int score, int color) {
        UiDraw.fillRounded(g, x, y, w, h, 6, withAlpha(color, 0.10F));
        // 边框用细线近似
        UiDraw.fillRounded(g, x, y, w, 1, 0, withAlpha(color, 0.5F));
        UiDraw.fillRounded(g, x, y + h - 1, w, 1, 0, withAlpha(color, 0.5F));
        UiDraw.fillRounded(g, x, y, 1, h, 0, withAlpha(color, 0.5F));
        UiDraw.fillRounded(g, x + w - 1, y, 1, h, 0, withAlpha(color, 0.5F));

        int labelW = font.width(label);
        drawShadowed(g, font, label, x + (w - labelW) / 2, y + 5, withAlpha(color, 0.85F), false);

        String s = String.valueOf(score);
        g.pose().pushPose();
        g.pose().translate(x + (w - font.width(s) * 2.0F) / 2, y + 15, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        drawShadowed(g, font, s, 0, 0, color, true);
        g.pose().popPose();
    }

    // ==================== 工具 ====================

    private static void drawShadowed(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    private static int withAlpha(int rgb, float a) {
        int alpha = (int) (clamp01(a) * 255);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** 回弹缓动，对应 CSS cubic-bezier(.2,.9,.25,1) 的近似 */
    private static float easeOutBack(float t) {
        float c1 = 1.20F;
        float c3 = c1 + 1;
        float p = t - 1;
        return 1 + c3 * p * p * p + c1 * p * p;
    }

    // ---- 数据载体 ----
    public record KillInfo(String killer, boolean enemy, ItemStack gun, String ammoText, boolean headshot) {
    }

    /**
     * 供 TcpHud 调用的入口（死亡 UI 与其他 HUD 共存时，死亡 UI 画在最上层）。
     */
    public static void renderOverlay(net.minecraft.client.gui.GuiGraphics g, float partialTick) {
        render(g, partialTick, true);
    }
}
