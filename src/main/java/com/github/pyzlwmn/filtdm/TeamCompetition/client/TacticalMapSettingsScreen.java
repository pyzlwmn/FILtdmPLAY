package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * v84：游戏内「FIL 对战设置」（Esc 菜单右下角入口）。
 *
 * <p>三个分区（页签）：**小地图 / 物品栏 / 状态条**，全部热更新，
 * 关闭时统一写回 {@code config/filtdm-client.toml}。</p>
 *
 * <p>面板整体按屏幕等比缩放，GUI 缩放调大也不会超出屏幕。</p>
 */
@OnlyIn(Dist.CLIENT)
public class TacticalMapSettingsScreen extends Screen {

    private static final String[] ANCHORS = {
            "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
            "MIDDLE_LEFT", "MIDDLE_CENTER", "MIDDLE_RIGHT",
            "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"
    };
    private static final String[] ANCHOR_NAMES = {
            "左上", "中上", "右上",
            "左中", "正中", "右中",
            "左下", "中下", "右下"
    };
    private static final String[] TABS = {"小地图", "物品栏", "状态条"};

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 320;
    private static final int ROW_H = 20;
    private static final int GAP = 8;

    private static final int C_PANEL = 0xF00D131A;
    private static final int C_BORDER = 0xFF3A4655;
    private static final int C_TITLE = 0xFFFFFFFF;
    private static final int C_HINT = 0xFF8B97A6;

    private final Screen parent;

    // ---- 小地图 ----
    private boolean radarOn;
    private double radarScale;
    private String anchor;
    private int radarX;
    private int radarY;

    // ---- 物品栏 ----
    private boolean hotbarOn;
    private double hotbarScaleV;
    private int hotbarX;
    private int hotbarY;
    private boolean hotbarAmmo;

    // ---- 状态条 ----
    private boolean statusOn;
    private double statusScaleV;
    private int statusX;
    private int statusY;

    private int tab = 0;

    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private Button toggleBtn;
    private Button ammoBtn;
    private final List<Button> anchorButtons = new ArrayList<>();

    private int panelX;
    private int panelY;
    private float uiScale = 1.0f;

    public TacticalMapSettingsScreen(Screen parent) {
        super(Component.literal("FIL 对战设置"));
        this.parent = parent;
        readFromConfig();
    }

    private void readFromConfig() {
        radarOn = ClientConfig.radarEnabled;
        radarScale = ClientConfig.radarScale;
        anchor = ClientConfig.radarAnchor;
        radarX = ClientConfig.radarOffsetX;
        radarY = ClientConfig.radarOffsetY;

        hotbarOn = ClientConfig.hotbarEnabled;
        hotbarScaleV = ClientConfig.hotbarScale;
        hotbarX = ClientConfig.hotbarOffsetX;
        hotbarY = ClientConfig.hotbarOffsetY;
        hotbarAmmo = ClientConfig.hotbarShowAmmo;

        statusOn = ClientConfig.statusEnabled;
        statusScaleV = ClientConfig.statusScale;
        statusX = ClientConfig.statusOffsetX;
        statusY = ClientConfig.statusOffsetY;
    }

    @Override
    protected void init() {
        uiScale = Math.max(0.45f, Math.min(1.0f, Math.min(
                (this.width - 16f) / PANEL_W,
                (this.height - 16f) / PANEL_H)));

        int pw = Math.round(PANEL_W * uiScale);
        int ph = Math.round(PANEL_H * uiScale);
        panelX = (this.width - pw) / 2;
        panelY = (this.height - ph) / 2;

        // 页签
        tabButtons.clear();
        int tabW = Math.round((pw - 24 * uiScale * 2) / 3f) - 2;
        int tabH = Math.max(14, Math.round(18 * uiScale));
        int tx = panelX + Math.round(24 * uiScale);
        int ty = panelY + Math.round(30 * uiScale);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(TABS[i] + (tab == i ? " ●" : "")), x -> {
                tab = idx;
                refreshTabs();
                rebuildContent();
            }).bounds(tx + i * (tabW + 3), ty, tabW, tabH).build();
            tabButtons.add(b);
            addRenderableWidget(b);
        }

        rebuildContent();
    }

    private void refreshTabs() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).setMessage(Component.literal(TABS[i] + (tab == i ? " ●" : "")));
        }
    }

    private void clearContent() {
        for (AbstractWidget w : content) {
            this.removeWidget(w);
        }
        content.clear();
        anchorButtons.clear();
        toggleBtn = null;
        ammoBtn = null;
    }

    private void rebuildContent() {
        clearContent();

        int pad = Math.round(24 * uiScale);
        int pw = Math.round(PANEL_W * uiScale);
        int rowH = Math.max(14, Math.round(ROW_H * uiScale));
        int gap = Math.max(4, Math.round(GAP * uiScale));
        int x = panelX + pad;
        int w = pw - pad * 2;
        int y = panelY + Math.round(58 * uiScale);

        if (tab == 0) {
            buildRadarTab(x, y, w, rowH, gap, pw);
        } else if (tab == 1) {
            buildHotbarTab(x, y, w, rowH, gap);
        } else {
            buildStatusTab(x, y, w, rowH, gap);
        }

        // 底部按钮（固定）
        int by = panelY + Math.round(PANEL_H * uiScale) - Math.round(34 * uiScale);
        int bw = Math.round(92 * uiScale);
        Button reset = Button.builder(Component.literal("恢复默认"), b -> {
            ClientConfig.defaults();
            readFromConfig();
            rebuildContent();
            refreshTabs();
            pushAll();
        }).bounds(panelX + pad, by, bw, rowH).build();
        Button done = Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(panelX + pw - pad - bw, by, bw, rowH).build();
        content.add(reset);
        content.add(done);
        addRenderableWidget(reset);
        addRenderableWidget(done);
    }

    private void buildRadarTab(int x, int y, int w, int rowH, int gap, int pw) {
        toggleBtn = Button.builder(radarToggleLabel(), b -> {
            radarOn = !radarOn;
            b.setMessage(radarToggleLabel());
            pushAll();
        }).bounds(x, y, w, rowH).build();
        content.add(toggleBtn);
        addRenderableWidget(toggleBtn);

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                radarScale, 0.5, 3.0, false,
                v -> radarScale = v,
                v -> Component.literal(String.format("雷达大小：%.2f×", v)))));

        y += rowH + gap;
        int cellW = Math.round(52 * uiScale);
        int cellGap = Math.max(2, Math.round(4 * uiScale));
        int cellH = Math.max(12, Math.round(18 * uiScale));
        int gridW = 3 * cellW + 2 * cellGap;
        int gx = panelX + (pw - gridW) / 2;
        for (int i = 0; i < 9; i++) {
            final String value = ANCHORS[i];
            int col = i % 3;
            int row = i / 3;
            Button b = Button.builder(anchorLabel(i), btn -> {
                anchor = value;
                refreshAnchors();
                pushAll();
            }).bounds(gx + col * (cellW + cellGap), y + row * (cellH + cellGap), cellW, cellH).build();
            anchorButtons.add(b);
            content.add(b);
            addRenderableWidget(b);
        }

        y += 3 * cellH + 2 * cellGap + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                radarX, -400, 400, true,
                v -> radarX = (int) Math.round(v),
                v -> Component.literal("水平偏移：" + Math.round(v) + " px"))));

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                radarY, -400, 400, true,
                v -> radarY = (int) Math.round(v),
                v -> Component.literal("垂直偏移：" + Math.round(v) + " px"))));
    }

    private void buildHotbarTab(int x, int y, int w, int rowH, int gap) {
        toggleBtn = Button.builder(hotbarToggleLabel(), b -> {
            hotbarOn = !hotbarOn;
            b.setMessage(hotbarToggleLabel());
            pushAll();
        }).bounds(x, y, w, rowH).build();
        content.add(toggleBtn);
        addRenderableWidget(toggleBtn);

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                hotbarScaleV, 0.5, 3.0, false,
                v -> hotbarScaleV = v,
                v -> Component.literal(String.format("物品栏大小：%.2f×", v)))));

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                hotbarX, -400, 400, true,
                v -> hotbarX = (int) Math.round(v),
                v -> Component.literal("水平偏移：" + Math.round(v) + " px"))));

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                hotbarY, -400, 400, true,
                v -> hotbarY = (int) Math.round(v),
                v -> Component.literal("垂直偏移：" + Math.round(v) + " px"))));

        y += rowH + gap;
        ammoBtn = Button.builder(ammoLabel(), b -> {
            hotbarAmmo = !hotbarAmmo;
            b.setMessage(ammoLabel());
            pushAll();
        }).bounds(x, y, w, rowH).build();
        content.add(ammoBtn);
        addRenderableWidget(ammoBtn);
    }

    private void buildStatusTab(int x, int y, int w, int rowH, int gap) {
        toggleBtn = Button.builder(statusToggleLabel(), b -> {
            statusOn = !statusOn;
            b.setMessage(statusToggleLabel());
            pushAll();
        }).bounds(x, y, w, rowH).build();
        content.add(toggleBtn);
        addRenderableWidget(toggleBtn);

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                statusScaleV, 0.5, 3.0, false,
                v -> statusScaleV = v,
                v -> Component.literal(String.format("血条大小：%.2f×", v)))));

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                statusX, -400, 400, true,
                v -> statusX = (int) Math.round(v),
                v -> Component.literal("水平偏移：" + Math.round(v) + " px"))));

        y += rowH + gap;
        content.add(addRenderableWidget(new NumSlider(x, y, w, rowH,
                statusY, -400, 400, true,
                v -> statusY = (int) Math.round(v),
                v -> Component.literal("垂直偏移：" + Math.round(v) + " px"))));
    }

    private Component radarToggleLabel() {
        return Component.literal("显示小地图：" + (radarOn ? "开" : "关"));
    }

    private Component hotbarToggleLabel() {
        return Component.literal("显示物品栏：" + (hotbarOn ? "开" : "关"));
    }

    private Component statusToggleLabel() {
        return Component.literal("显示血条：" + (statusOn ? "开" : "关"));
    }

    private Component ammoLabel() {
        return Component.literal("显示弹药数：" + (hotbarAmmo ? "开" : "关"));
    }

    private Component anchorLabel(int i) {
        return Component.literal(ANCHOR_NAMES[i] + (anchor.equals(ANCHORS[i]) ? " ✓" : ""));
    }

    private void refreshAnchors() {
        for (int i = 0; i < anchorButtons.size(); i++) {
            anchorButtons.get(i).setMessage(anchorLabel(i));
        }
    }

    /** 改动立刻生效（热更新，写盘在关闭时）。 */
    private void pushAll() {
        ClientConfig.apply(radarOn, radarScale, anchor, radarX, radarY);
        ClientConfig.applyHotbar(hotbarOn, hotbarScaleV, hotbarX, hotbarY, hotbarAmmo);
        ClientConfig.applyStatus(statusOn, statusScaleV, statusX, statusY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int pw = Math.round(PANEL_W * uiScale);
        int ph = Math.round(PANEL_H * uiScale);

        g.fill(panelX, panelY, panelX + pw, panelY + ph, C_PANEL);
        border(g, panelX, panelY, pw, ph, C_BORDER);
        int lineY = panelY + Math.round(26 * uiScale);
        g.fill(panelX + 1, lineY, panelX + pw - 1, lineY + 1, C_BORDER);

        super.render(g, mouseX, mouseY, partialTick);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(panelX + pw / 2f, panelY + Math.round(6 * uiScale), 0);
        pose.scale(uiScale, uiScale, 1.0f);
        g.drawString(this.font, this.title, -this.font.width(this.title) / 2, 0, C_TITLE, false);
        pose.popPose();

        pose.pushPose();
        pose.translate(panelX + pw / 2f, panelY + ph - Math.round(12 * uiScale), 0);
        pose.scale(uiScale * 0.9f, uiScale * 0.9f, 1.0f);
        Component hint = Component.literal("拖动立即生效 · 关闭自动保存");
        g.drawString(this.font, hint, -this.font.width(hint) / 2, 0, C_HINT, false);
        pose.popPose();
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        pushAll();
        ClientConfig.save();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    /** 通用数值滑条（整数/小数都行）。 */
    private class NumSlider extends AbstractSliderButton {

        private final double min;
        private final double max;
        private final boolean integer;
        private final DoubleConsumer onApply;
        private final DoubleFunction<Component> labelFn;
        private boolean ready = false;

        NumSlider(int x, int y, int w, int h, double value, double min, double max, boolean integer,
                  DoubleConsumer onApply, DoubleFunction<Component> labelFn) {
            super(x, y, w, h, Component.empty(), (value - min) / (max - min));
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.onApply = onApply;
            this.labelFn = labelFn;
            this.ready = true;
            updateMessage();
        }

        private double realValue() {
            double v = min + (max - min) * this.value;
            return integer ? Math.round(v) : Math.round(v * 100.0) / 100.0;
        }

        @Override
        protected void updateMessage() {
            if (!ready) return; // 父类构造里会先调用一次，此时字段还没赋值
            setMessage(labelFn.apply(realValue()));
        }

        @Override
        protected void applyValue() {
            if (!ready) return;
            onApply.accept(realValue());
            pushAll();
        }
    }
}
