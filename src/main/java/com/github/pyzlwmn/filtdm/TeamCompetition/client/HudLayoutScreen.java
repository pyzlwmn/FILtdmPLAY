package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * v1.2.0：HUD 布局设置界面（Esc →「界面布局」）。
 *
 * <p>和 Esc 菜单一样是半透明压暗背景，把当前五块 HUD 按真实位置 / 大小 / 透明度
 * 直接渲染出来：</p>
 * <ul>
 *   <li><b>点一下部件</b> → 旁边自动弹出长方形设置框（右边放不下就自动翻到左边）</li>
 *   <li><b>按住左键拖</b> → 移动该部件</li>
 *   <li>设置框里：显示开关 · 大小滑条 · 透明度滑条 · 位置复位 · 恢复默认</li>
 *   <li>Esc / 完成 → 写回 {@code config/filtdm-client.toml}（全部热更新，不用重启）</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class HudLayoutScreen extends Screen {

    // ---------------- 配色（和游戏内 HUD 保持同一套感觉）
    private static final int C_ACCENT = 0xFF4DA3FF;
    private static final int C_ACCENT_SOFT = 0x884DA3FF;
    private static final int C_PANEL_TOP = 0xF00E141C;
    private static final int C_PANEL_BOTTOM = 0xF00A0F16;
    private static final int C_BORDER = 0xFF3A4655;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFF9AA4B2;
    private static final int C_MUTED = 0xFF6E7A8A;

    // ---------------- 设置框几何
    private static final int PANEL_W = 208;
    private static final int PANEL_H = 146;
    private static final int PANEL_GAP = 12;
    private static final int ROW_TOGGLE_Y = 28;
    private static final int ROW_SIZE_Y = 50;
    private static final int ROW_ALPHA_Y = 74;
    private static final int ROW_BTN_Y = 98;
    private static final int SLIDER_X = 66;
    private static final int SLIDER_W = 92;
    private static final int SLIDER_H = 6;

    private final Screen parent;

    private HudLayout.Element selected = null;
    private boolean dragging = false;
    private double dragAccX;
    private double dragAccY;
    private int activeSlider = 0; // 0=无 1=大小 2=透明度

    private int[] panelRect = null;
    private int[] sizeTrack = null;
    private int[] alphaTrack = null;
    private int[] toggleRect = null;
    private int[] resetPosRect = null;
    private int[] resetAllRect = null;

    public HudLayoutScreen(Screen parent) {
        super(Component.literal("HUD 布局设置"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ================================================================== 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明压暗，观感和 Esc 菜单一致
        renderBackground(g);
        g.fill(0, 0, this.width, this.height, 0x66000000);

        drawTopBar(g);

        HudLayout.Element hovered = pick(mouseX, mouseY);
        for (HudLayout.Element e : HudLayout.ALL) {
            preview(g, e);
            if (e == hovered || e == selected) {
                outline(g, HudLayout.bounds(e, this.width, this.height),
                        e == selected ? C_ACCENT : C_ACCENT_SOFT);
            }
            label(g, e);
        }

        if (selected != null) {
            layoutPanel();
            drawPanel(g, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawTopBar(GuiGraphics g) {
        g.fill(0, 0, this.width, 24, 0xB005080C);
        g.fill(0, 23, this.width, 24, 0x664DA3FF);

        String title = "HUD 布局设置";
        g.drawString(this.font, title, 10, 8, C_TEXT, false);

        String hint = "点击部件打开设置 · 按住左键拖动位置 · Esc 保存并退出";
        g.drawString(this.font, hint, this.width - this.font.width(hint) - 10, 8, C_MUTED, false);
    }

    // ================================================================== 各部件预览

    private void preview(GuiGraphics g, HudLayout.Element e) {
        float a = (float) HudLayout.alpha(e);
        if (!HudLayout.enabled(e)) a = Math.min(a, 0.3f);

        switch (e) {
            case SCORE -> previewScore(g, a);
            case RADAR -> previewRadar(g, a);
            case HOTBAR -> previewHotbar(g, a);
            case STATUS -> previewStatus(g, a);
            case HITDIR -> previewHitDir(g, a);
        }
    }

    /** 顶部比分条：按真实内容尺寸 × 缩放画一个占位比分。 */
    private void previewScore(GuiGraphics g, float a) {
        int[] size = TcpHud.scoreContentSize();
        int total = size[0];
        int contentH = size[1];
        int[] b = TcpHud.scoreBounds(this.width, this.height);
        float s = 0.8f * (float) Math.max(0.5, ClientConfig.scoreScale);

        int cBg = UiDraw.mulAlpha(0xB0101620, a);
        int cBorder = UiDraw.mulAlpha(0x663A465A, a);
        int cBlue = UiDraw.mulAlpha(0xFF3B82F6, a);
        int cRed = UiDraw.mulAlpha(0xFFE63946, a);
        int cText = UiDraw.mulAlpha(0xFFFFFFFF, a);
        int cDim = UiDraw.mulAlpha(0xFFB9C2CF, a);
        int cGold = UiDraw.mulAlpha(0xFFFFC93C, a);

        g.pose().pushPose();
        g.pose().translate(b[0] + total * s / 2f, b[1] + 2f * s, 0f);
        g.pose().scale(s, s, 1f);

        UiDraw.panel(g, -total / 2, -2, total, contentH, 5, cBorder, cBg);
        UiDraw.fillRounded(g, -total / 2, -1, 3, contentH - 2, 2, cBlue);
        UiDraw.fillRounded(g, total / 2 - 3, -1, 3, contentH - 2, 2, cRed);

        String score = "12 : 01:24 : 9";
        g.drawString(this.font, score, -this.font.width(score) / 2, 6, cText, true);

        String sep = "·";
        g.drawString(this.font, sep, -this.font.width(sep) / 2, 19, cDim, false);

        String target = "目标：30";
        g.drawString(this.font, target, -this.font.width(target) / 2, 26, cGold, true);

        g.pose().popPose();
    }

    /** 小地图：圆盘 + 圆环 + 十字 + 自己 + 两个队友 / 一个敌人。 */
    private void previewRadar(GuiGraphics g, float a) {
        int[] b = TcpTacticalMapHud.bounds(this.width, this.height);
        int r = b[2] / 2;
        int cx = b[0] + r;
        int cy = b[1] + r;

        int cBg = UiDraw.mulAlpha(0xB00B1018, a);
        int cRing = UiDraw.mulAlpha(0x66A8B6C8, a);
        int cTick = UiDraw.mulAlpha(0x55202836, a);
        int cSelf = UiDraw.mulAlpha(0xFFF5F7FA, a);
        int cMate = UiDraw.mulAlpha(0xFF3B82F6, a);
        int cMate2 = UiDraw.mulAlpha(0xFF46C46A, a);
        int cEnemy = UiDraw.mulAlpha(0xFFE63946, a);

        disc(g, cx, cy, r, cBg);
        ring(g, cx, cy, r, cRing);
        g.fill(cx - r + 4, cy, cx + r - 4, cy + 1, cTick);
        g.fill(cx, cy - r + 4, cx + 1, cy + r - 4, cTick);

        triangle(g, cx, cy, Math.max(3, Math.round(4 * (r / 78f))), cSelf);

        int d = Math.max(2, Math.round(3 * (r / 78f)));
        dot(g, cx - r / 3, cy - r / 4, d, cMate);
        dot(g, cx + r / 4, cy + r / 3, d, cMate2);
        dot(g, cx + r / 2, cy - r / 3, d, cEnemy);
    }

    /** 物品栏：5 个格子 + 序号 + 弹药占位。 */
    private void previewHotbar(GuiGraphics g, float a) {
        float s = TcpHotbarHud.hotbarScale();
        int[] b = TcpHotbarHud.hotbarRect(this.width, this.height, s);

        int cBorder = UiDraw.mulAlpha(0x66A8B6C8, a);
        int cTop = UiDraw.mulAlpha(0xE80F1620, a);
        int cBottom = UiDraw.mulAlpha(0xE8090E15, a);
        int cSlot = UiDraw.mulAlpha(0x70141B24, a);
        int cSel = UiDraw.mulAlpha(0xC820303F, a);
        int cSelBorder = UiDraw.mulAlpha(0xFFFFFFFF, a);
        int cDim = UiDraw.mulAlpha(0xFF9AA4B2, a);
        int cGold = UiDraw.mulAlpha(0xFFFFC93C, a);

        UiDraw.panelEx(g, b[0], b[1], b[2], b[3], 8, cBorder, cTop, cBottom,
                UiDraw.mulAlpha(0x55FFC93C, a));

        g.pose().pushPose();
        g.pose().translate(b[0] + TcpHotbarHud.PAD * s, b[1] + TcpHotbarHud.PAD * s, 0f);
        g.pose().scale(s, s, 1f);

        for (int i = 0; i < TcpHotbarHud.SLOTS; i++) {
            int x = i * (TcpHotbarHud.SLOT + TcpHotbarHud.GAP);
            boolean sel = i == 0;
            UiDraw.panel(g, x, 0, TcpHotbarHud.SLOT, TcpHotbarHud.SLOT, 5,
                    sel ? cSelBorder : cBorder, sel ? cSel : cSlot);
            g.drawString(this.font, String.valueOf(i + 1), x + 3, 3,
                    sel ? cGold : cDim, false);
            if (i < 2) {
                String ammo = i == 0 ? "30/30" : "12/12";
                g.drawString(this.font, ammo,
                        x + (TcpHotbarHud.SLOT - this.font.width(ammo)) / 2,
                        TcpHotbarHud.SLOT - 12, UiDraw.mulAlpha(0xFFFFFFFF, a), false);
            }
        }
        g.pose().popPose();
    }

    /** 状态条：血条 + 护甲。 */
    private void previewStatus(GuiGraphics g, float a) {
        int[] b = TcpStatusHud.bounds(this.width, this.height);
        float s = (float) Math.max(0.5, ClientConfig.statusScale);

        int cBorder = UiDraw.mulAlpha(0x66A8B6C8, a);
        int cTop = UiDraw.mulAlpha(0xE80F1620, a);
        int cBottom = UiDraw.mulAlpha(0xE8090E15, a);
        int cTrack = UiDraw.mulAlpha(0x66141B24, a);
        int cHp = UiDraw.mulAlpha(0xFF46C46A, a);
        int cArmor = UiDraw.mulAlpha(0xFF6FA8FF, a);
        int cText = UiDraw.mulAlpha(0xFFFFFFFF, a);
        int cDim = UiDraw.mulAlpha(0xFF9AA4B2, a);

        UiDraw.panelEx(g, b[0], b[1], b[2], b[3], 8, cBorder, cTop, cBottom,
                UiDraw.mulAlpha(0x5546C46A, a));

        g.pose().pushPose();
        g.pose().translate(b[0], b[1], 0f);
        g.pose().scale(s, s, 1f);

        g.drawString(this.font, "❤", 8, 8, cHp, false);
        g.drawString(this.font, "87", 20, 8, cText, false);

        UiDraw.fillRounded(g, 50, 8, 94, 8, 4, cTrack);
        UiDraw.fillRounded(g, 50, 8, 66, 8, 4, cHp);

        g.drawString(this.font, "🛡", 8, 24, cArmor, false);
        g.drawString(this.font, "12", 20, 24, cDim, false);

        g.pose().popPose();
    }

    /** 受击指示：虚拟圆参考线 + 右侧三角。 */
    private void previewHitDir(GuiGraphics g, float a) {
        int[] b = TcpHitDirHud.bounds(this.width, this.height);
        int cx = b[0] + b[2] / 2;
        int cy = b[1] + b[3] / 2;
        int r = Math.max(18, b[2] / 2);

        int cGuide = UiDraw.mulAlpha(0x33A8B6C8, a);
        ring(g, cx, cy, r, cGuide);

        TcpHitDirHud.previewTriangle(g, this.width, this.height, 90f,
                (float) Math.min(1.0, HudLayout.alpha(HudLayout.Element.HITDIR)));
    }

    // ================================================================== 选中框 / 标签

    private void outline(GuiGraphics g, int[] b, int color) {
        int x = b[0] - 2;
        int y = b[1] - 2;
        int w = b[2] + 4;
        int h = b[3] + 4;
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void label(GuiGraphics g, HudLayout.Element e) {
        int[] b = HudLayout.bounds(e, this.width, this.height);
        String text = e.label + (HudLayout.enabled(e) ? "" : "（已隐藏）");
        int tw = this.font.width(text);
        int tx = b[0];
        int ty = b[1] - 11;
        if (ty < 26) ty = b[1] + 2;
        if (tx + tw + 6 > this.width) tx = this.width - tw - 6;
        if (tx < 2) tx = 2;

        g.fill(tx - 2, ty - 1, tx + tw + 2, ty + 9, 0x66000000);
        g.drawString(this.font, text, tx, ty, e == selected ? C_ACCENT : C_MUTED, false);
    }

    // ================================================================== 设置框

    private void layoutPanel() {
        int[] b = HudLayout.bounds(selected, this.width, this.height);

        int px = b[0] + b[2] + PANEL_GAP;              // 默认放在部件右边
        if (px + PANEL_W > this.width - 6) {
            px = b[0] - PANEL_GAP - PANEL_W;           // 右边放不下 → 翻到左边
        }
        if (px < 6) {                                  // 两边都放不下 → 塞屏幕里
            px = Math.max(6, Math.min(this.width - PANEL_W - 6, b[0] + b[2] / 2 - PANEL_W / 2));
        }

        int py = b[1] + b[3] / 2 - PANEL_H / 2;        // 垂直居中贴合部件
        py = Math.max(28, Math.min(this.height - PANEL_H - 6, py));

        panelRect = new int[]{px, py, PANEL_W, PANEL_H};
        toggleRect = new int[]{px + 12, py + ROW_TOGGLE_Y, 12, 12};
        sizeTrack = new int[]{px + SLIDER_X, py + ROW_SIZE_Y, SLIDER_W, SLIDER_H};
        alphaTrack = new int[]{px + SLIDER_X, py + ROW_ALPHA_Y, SLIDER_W, SLIDER_H};
        resetPosRect = new int[]{px + 12, py + ROW_BTN_Y, 88, 18};
        resetAllRect = new int[]{px + 106, py + ROW_BTN_Y, 82, 18};
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        int px = panelRect[0];
        int py = panelRect[1];

        UiDraw.panelEx(g, px, py, PANEL_W, PANEL_H, 7,
                C_BORDER, C_PANEL_TOP, C_PANEL_BOTTOM, C_ACCENT_SOFT);

        g.drawString(this.font, selected.label + " · 设置", px + 12, py + 9, C_TEXT, false);
        g.drawString(this.font, "Esc 保存", px + PANEL_W - this.font.width("Esc 保存") - 10,
                py + 9, C_MUTED, false);

        // ---- 显示开关
        boolean on = HudLayout.enabled(selected);
        int[] t = toggleRect;
        UiDraw.panel(g, t[0], t[1], t[2], t[3], 3,
                on ? C_ACCENT : C_BORDER, on ? 0xFF1B3A5C : 0xFF141A22);
        if (on) {
            g.fill(t[0] + 3, t[1] + 3, t[0] + t[2] - 3, t[1] + t[3] - 3, C_ACCENT);
        }
        g.drawString(this.font, "显示此部件", t[0] + 18, t[1] + 2, on ? C_TEXT : C_DIM, false);

        // ---- 大小
        g.drawString(this.font, "大小", px + 12, py + ROW_SIZE_Y - 1, C_DIM, false);
        double sv = HudLayout.scale(selected);
        double smin = HudLayout.minScale(selected);
        double smax = HudLayout.maxScale(selected);
        drawSlider(g, sizeTrack, norm(sv, smin, smax), mouseX, mouseY);
        g.drawString(this.font, String.format("%.2f×", sv), px + 162, py + ROW_SIZE_Y - 1,
                C_TEXT, false);

        // ---- 透明度
        g.drawString(this.font, "透明度", px + 12, py + ROW_ALPHA_Y - 1, C_DIM, false);
        double av = HudLayout.alpha(selected);
        drawSlider(g, alphaTrack, norm(av, 0.2, 1.0), mouseX, mouseY);
        g.drawString(this.font, String.format("%.2f", av), px + 162, py + ROW_ALPHA_Y - 1,
                C_TEXT, false);

        // ---- 按钮
        drawButton(g, resetPosRect, "位置复位", mouseX, mouseY);
        drawButton(g, resetAllRect, "恢复默认", mouseX, mouseY);

        // ---- 底部提示
        g.drawString(this.font, "按住部件左键拖动可移动", px + 12, py + 124, C_MUTED, false);
    }

    private void drawSlider(GuiGraphics g, int[] track, float value, int mouseX, int mouseY) {
        int x = track[0];
        int y = track[1];
        int w = track[2];
        int h = track[3];

        UiDraw.fillRounded(g, x, y, w, h, h / 2, 0xFF1A222C);
        int fill = Math.round(w * value);
        if (fill > 0) {
            UiDraw.fillRounded(g, x, y, Math.max(h, fill), h, h / 2, C_ACCENT);
        }
        int knob = x + fill;
        boolean hover = inside(track, mouseX, mouseY, 4);
        UiDraw.fillRounded(g, knob - 4, y - 3, 8, h + 6, 4,
                hover || activeSlider != 0 ? 0xFFFFFFFF : 0xFFCFD8E3);
    }

    private void drawButton(GuiGraphics g, int[] r, String text, int mouseX, int mouseY) {
        boolean hover = inside(r, mouseX, mouseY, 0);
        UiDraw.panel(g, r[0], r[1], r[2], r[3], 4,
                hover ? C_ACCENT : C_BORDER,
                hover ? 0xFF1B3A5C : 0xFF141A22);
        g.drawString(this.font, text,
                r[0] + (r[2] - this.font.width(text)) / 2, r[1] + 5,
                hover ? C_TEXT : C_DIM, false);
    }

    // ================================================================== 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 1) 设置框优先（它在部件旁边，可能压住其它部件）
        if (selected != null && panelRect != null && inside(panelRect, mouseX, mouseY, 0)) {
            handlePanelClick(mouseX, mouseY);
            return true;
        }

        // 2) 点部件 → 选中 + 开始拖
        HudLayout.Element hit = pick(mouseX, mouseY);
        if (hit != null) {
            selected = hit;
            dragging = true;
            dragAccX = 0;
            dragAccY = 0;
            return true;
        }

        // 3) 点空白 → 收起设置框
        selected = null;
        panelRect = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handlePanelClick(double mouseX, double mouseY) {
        // 显示开关
        if (inside(toggleRect, mouseX, mouseY, 6) || inside(new int[]{
                toggleRect[0], toggleRect[1], PANEL_W - 24, 14}, mouseX, mouseY, 0)) {
            HudLayout.setEnabled(selected, !HudLayout.enabled(selected));
            ClientConfig.save();
            return;
        }

        // 滑条
        if (inside(sizeTrack, mouseX, mouseY, 6)) {
            activeSlider = 1;
            setScaleFromMouse(mouseX);
            return;
        }
        if (inside(alphaTrack, mouseX, mouseY, 6)) {
            activeSlider = 2;
            setAlphaFromMouse(mouseX);
            return;
        }

        // 按钮
        if (inside(resetPosRect, mouseX, mouseY, 0)) {
            HudLayout.resetPosition(selected);
            ClientConfig.save();
            return;
        }
        if (inside(resetAllRect, mouseX, mouseY, 0)) {
            HudLayout.resetElement(selected);
            ClientConfig.save();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (activeSlider == 1) {
                setScaleFromMouse(mouseX);
                return true;
            }
            if (activeSlider == 2) {
                setAlphaFromMouse(mouseX);
                return true;
            }
            if (dragging && selected != null) {
                dragAccX += dragX;
                dragAccY += dragY;
                int dx = (int) dragAccX;
                int dy = (int) dragAccY;
                if (dx != 0 || dy != 0) {
                    dragAccX -= dx;
                    dragAccY -= dy;
                    HudLayout.move(selected, dx, dy, this.width, this.height);
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (dragging || activeSlider != 0) {
                ClientConfig.save();
            }
            dragging = false;
            activeSlider = 0;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setScaleFromMouse(double mouseX) {
        if (selected == null || sizeTrack == null) return;
        double t = norm((mouseX - sizeTrack[0]) / (double) sizeTrack[2]);
        double v = HudLayout.minScale(selected)
                + t * (HudLayout.maxScale(selected) - HudLayout.minScale(selected));
        HudLayout.setScale(selected, v);
    }

    private void setAlphaFromMouse(double mouseX) {
        if (selected == null || alphaTrack == null) return;
        double t = norm((mouseX - alphaTrack[0]) / (double) alphaTrack[2]);
        HudLayout.setAlpha(selected, 0.2 + t * 0.8);
    }

    @Override
    public void onClose() {
        ClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    // ================================================================== 命中 / 工具

    private HudLayout.Element pick(double mouseX, double mouseY) {
        for (HudLayout.Element e : HudLayout.ALL) {
            int[] b = HudLayout.bounds(e, this.width, this.height);
            if (e == HudLayout.Element.HITDIR) {
                double cx = b[0] + b[2] / 2.0;
                double cy = b[1] + b[3] / 2.0;
                double r = b[2] / 2.0;
                double dx = mouseX - cx;
                double dy = mouseY - cy;
                if (dx * dx + dy * dy <= r * r) return e;
            } else if (inside(b, mouseX, mouseY, 0)) {
                return e;
            }
        }
        return null;
    }

    private static boolean inside(int[] r, double x, double y, int pad) {
        return x >= r[0] - pad && x <= r[0] + r[2] + pad
                && y >= r[1] - pad && y <= r[1] + r[3] + pad;
    }

    private static float norm(double v, double min, double max) {
        return (float) Math.max(0.0, Math.min(1.0, (v - min) / (max - min)));
    }

    private static float norm(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    // ---------------- 基础图形

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    private static void dot(GuiGraphics g, int cx, int cy, int r, int color) {
        disc(g, cx, cy, r, color);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int steps = 200;
        int prevX = cx + r;
        int prevY = cy;
        for (int i = 1; i <= steps; i++) {
            double ang = Math.PI * 2.0 * i / steps;
            int x = cx + (int) Math.round(Math.cos(ang) * r);
            int y = cy + (int) Math.round(Math.sin(ang) * r);
            line(g, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    private static void triangle(GuiGraphics g, int cx, int cy, int size, int color) {
        int x1 = cx;
        int y1 = cy - size;
        int x2 = cx + size;
        int y2 = cy + (int) Math.round(size * 0.8);
        int x3 = cx - size;
        int y3 = y2;
        line(g, x1, y1, x2, y2, color);
        line(g, x2, y2, x3, y3, color);
        line(g, x3, y3, x1, y1, color);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int guard = 0;
        while (guard++ < 800) {
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
