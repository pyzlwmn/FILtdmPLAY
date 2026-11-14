package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.ptcrys.fpsmatch.common.client.screen.hud.IHudRenderer;

/**
 * v82：自定义状态条 —— 血条 + 护甲，**饥饿值直接不要**。
 *
 * <p>原版的 PLAYER_HEALTH / FOOD_LEVEL / ARMOR_LEVEL / MOUNT_HEALTH 全部取消，
 * 左下角自己画一条：</p>
 * <pre>
 *   ┌───────────────────────────┐
 *   │ ❤ 87    ████████████░░░░  │
 *   │ 🛡 12                     │
 *   └───────────────────────────┘
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class TcpStatusHud implements IHudRenderer {

    private static final int MARGIN = 14;
    private static final int BAR_W = 150;
    private static final int BAR_H = 8;
    private static final int PANEL_W = 186;
    private static final int PANEL_H = 40;

    private static final int C_BG = 0xC70B1018;
    private static final int C_BORDER = 0x66A8B6C8;
    private static final int C_TRACK = 0x66141B24;
    private static final int C_HP = 0xFF46C46A;
    private static final int C_HP_LOW = 0xFFE63946;
    private static final int C_ARMOR = 0xFF6FA8FF;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFF9AA4B2;

    @Override
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!ClientConfig.statusEnabled) return;

        // 原版血量 / 饥饿 / 护甲 / 坐骑血 全部藏掉
        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.MOUNT_HEALTH.type()) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onSpectatorRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        // 观战不画
    }

    @Override
    public void onPlayerRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientConfig.statusEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        // v86：打开任何界面时隐藏
        if (mc.screen != null) return;

        float s = (float) Math.max(0.5, ClientConfig.statusScale);

        int pw = Math.round(PANEL_W * s);
        int ph = Math.round(PANEL_H * s);
        int px = MARGIN + ClientConfig.statusOffsetX;
        int py = screenHeight - ph - MARGIN + ClientConfig.statusOffsetY;

        UiDraw.panelEx(g, px, py, pw, ph, 8,
                C_BORDER, 0xE80F1620, 0xE8090E15, 0x5546C46A);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(px, py, 0);
        pose.scale(s, s, 1.0f);

        Font font = mc.font;

        float hp = player.getHealth();
        float maxHp = Math.max(1.0f, player.getMaxHealth());
        float ratio = Math.max(0.0f, Math.min(1.0f, hp / maxHp));

        // ❤ 数值
        String hpText = String.valueOf(Math.round(hp));
        g.drawString(font, "❤", 8, 8, C_HP_LOW, false);
        g.drawString(font, hpText, 20, 8, C_TEXT, false);

        // 血条
        int barX = 50;
        int barY = 8;
        int barW = BAR_W - 56;
        UiDraw.fillRounded(g, barX, barY, barW, BAR_H, BAR_H / 2, C_TRACK);
        int fill = Math.round(barW * ratio);
        int hpColor = ratio > 0.3f ? C_HP : C_HP_LOW;
        if (fill > 0) {
            UiDraw.fillRounded(g, barX, barY, Math.max(BAR_H, fill), BAR_H, BAR_H / 2, hpColor);
        }

        // 护甲（有才显示）
        int armor = player.getArmorValue();
        if (armor > 0) {
            g.drawString(font, "🛡", 8, 24, C_ARMOR, false);
            g.drawString(font, String.valueOf(armor), 20, 24, C_DIM, false);
        } else {
            g.drawString(font, "无护甲", 8, 24, 0xFF5D6B7A, false);
        }

        pose.popPose();
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
