package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.ptcrys.fpsmatch.common.client.screen.hud.IHudRenderer;

/**
 * v81：自定义物品栏 —— 右下角 5 个等大格子（替代原版快捷栏）。
 *
 * <ul>
 *   <li>左上键位号 / 中间图标（0.75 缩放）/ 下方两行弹药</li>
 *   <li>原版 HOTBAR、经验条、以及 TaCZ 自带的枪械 HUD 全部取消</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class TcpHotbarHud implements IHudRenderer {

    public static final int SLOTS = 5;
    public static final int PAD = 6;
    public static final int SLOT = 48;
    public static final int GAP = 4;
    public static final int MARGIN = 14;

    /** TaCZ 自己的枪械数据 HUD 的 overlay id（BO 也是这么挡的）。 */
    private static final String TACZ_GUN_HUD = "tac_gun_hud_overlay";

    private static final int C_BG = 0xC70B1018;
    private static final int C_BORDER = 0x66A8B6C8;
    private static final int C_SLOT = 0x70141B24;
    private static final int C_SEL = 0xC820303F;
    private static final int C_SEL_BORDER = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFF9AA4B2;
    private static final int C_RED = 0xFFE63946;
    private static final int C_GOLD = 0xFFFFC93C;

    /** 图标缩放：48 格子里 16px 原图太大，缩到 0.75。 */
    private static final float ICON_SCALE = 0.75f;

    @Override
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!ClientConfig.hotbarEnabled) return;

        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            event.setCanceled(true);
            return;
        }

        // ★ v86：TaCZ 的枪械数据面板整体屏蔽（不只看一个 id，
        // 因为 TaCZ 不同版本 overlay id 不一样，之前按 id 精确匹配没挡住）
        ResourceLocation id = event.getOverlay().id();
        if (id != null) {
            if (TACZ_GUN_HUD.equals(id.getPath())) {
                event.setCanceled(true);
            } else if ("tacz".equals(id.getNamespace()) || "tac".equals(id.getNamespace())) {
                event.setCanceled(true);
            }
        }
    }

    @Override
    public void onSpectatorRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        // 观战不出物品栏
    }

    @Override
    public void onPlayerRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientConfig.hotbarEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        // v86：打开任何界面（配件/背包/暂停）时隐藏，避免盖住面板
        if (mc.screen != null) return;

        float s = hotbarScale();
        int[] rect = hotbarRect(screenWidth, screenHeight, s);
        int px = rect[0];
        int py = rect[1];
        int totalW = rect[2];
        int totalH = rect[3];

        UiDraw.panelEx(g, px, py, totalW, totalH, 8,
                C_BORDER, 0xE80F1620, 0xE8090E15, 0x55FFC93C);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(px + PAD * s, py + PAD * s, 0);
        pose.scale(s, s, 1.0f);

        Font font = mc.font;
        int selected = player.getInventory().selected;

        for (int i = 0; i < SLOTS; i++) {
            int x = i * (SLOT + GAP);
            int y = 0;
            boolean sel = selected == i;
            ItemStack stack = player.getInventory().getItem(i);

            UiDraw.panel(g, x, y, SLOT, SLOT, 5,
                    sel ? C_SEL_BORDER : C_BORDER, sel ? C_SEL : C_SLOT);

            g.drawString(font, String.valueOf(i + 1), x + 3, y + 3, sel ? C_GOLD : C_DIM, false);

            // ★ v81：图标缩小到 0.75，居中
            if (!stack.isEmpty()) {
                pose.pushPose();
                pose.translate(x + SLOT / 2.0f, y + 6 + 8, 0);
                pose.scale(ICON_SCALE, ICON_SCALE, 1.0f);
                g.renderItem(stack, -8, -8);
                g.renderItemDecorations(font, stack, -8, -8);
                pose.popPose();
            }

            // 弹药：枪的正下方
            if (ClientConfig.hotbarShowAmmo && !stack.isEmpty()) {
                GunAmmoClient.Ammo ammo = GunAmmoClient.of(stack);
                if (ammo.gun()) {
                    String mag = ammo.magSize() > 0 ? ammo.mag() + "/" + ammo.magSize() : String.valueOf(ammo.mag());
                    int color = ammo.mag() <= 0 ? C_RED : C_TEXT;
                    textScaled(g, font, mag, x + SLOT / 2f, y + 28, 0.72f, color);

                    if (ammo.reserve() >= 0) {
                        String reserve = ammo.reserve() >= 9999 ? "∞" : String.valueOf(ammo.reserve());
                        textScaled(g, font, reserve, x + SLOT / 2f, y + 37, 0.6f, C_DIM);
                    }
                } else if (stack.getCount() > 1) {
                    textScaled(g, font, "x" + stack.getCount(), x + SLOT / 2f, y + 30, 0.72f, C_DIM);
                }
            }
        }

        pose.popPose();
    }

    /** 物品栏缩放。 */
    public static float hotbarScale() {
        return (float) Math.max(0.5, ClientConfig.hotbarScale);
    }

    /** @return {x, y, w, h} 屏幕坐标 */
    public static int[] hotbarRect(int screenWidth, int screenHeight, float s) {
        int innerW = SLOTS * SLOT + (SLOTS - 1) * GAP;
        int totalW = (int) Math.ceil((innerW + PAD * 2) * s);
        int totalH = (int) Math.ceil((SLOT + PAD * 2) * s);
        int x = screenWidth - totalW - MARGIN + ClientConfig.hotbarOffsetX;
        int y = screenHeight - totalH - MARGIN + ClientConfig.hotbarOffsetY;
        return new int[]{x, y, totalW, totalH};
    }

    private static void textScaled(GuiGraphics g, Font font, String text, float centerX, float y,
                                   float scale, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, y, 0);
        pose.scale(scale, scale, 1.0f);
        float w = font.width(text);
        g.drawString(font, text, Math.round(-w / 2f), 0, color, false);
        pose.popPose();
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
