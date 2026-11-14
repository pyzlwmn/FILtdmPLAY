package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.GunItemFactory;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.GunStats;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.LoadoutConfig;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpAttachmentSelectC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpAttachmentS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.math.Axis;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TcpAttachmentScreen extends Screen {

    private static final float GUN_SCALE = 5.0f;

    private static final float GUN_OFFSET_X = 0.0f;
    private static final float GUN_OFFSET_Y = 0.0f;

    private static final int WANT_W = 440;
    private static final int WANT_H = 272;
    private static final int PER_PAGE = 9;

    private static final int PREVIEW_H = 150;
    private static final int LEFT_W = 168;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    private String activeGun = null;
    private String activeType = null;
    private int page = 0;
    private int builtVersion = -1;


    private static final float ROT_START_Y = 25.0f;

    private static final float ROT_MAX_X = 70.0f;
    private float rotY = ROT_START_Y;
    private float rotX = 0.0f;
    private boolean dragging = false;
    private double dragAnchorX = 0.0;
    private double dragAnchorY = 0.0;
    private float dragBaseRotY = 0.0f;
    private float dragBaseRotX = 0.0f;


    private String hoverType = null;
    private String hoverId = null;

    /** v74：悬停预览时客户端自己算一份「改后数值」，跟服务端发来的「当前数值」做对比条 */
    private String statCacheKey = "";
    private List<GunStats.Stat> statHover = List.of();

    private final List<AbstractWidget> listButtons = new ArrayList<>();
    private final List<String[]> listButtonIds = new ArrayList<>();

    private int pvX;
    private int pvY;
    private int pvW;
    private int pvH;


    private ItemStack cachedPreview = ItemStack.EMPTY;
    private String cachedPreviewKey = null;

    public TcpAttachmentScreen() {
        super(Component.literal("配件编辑"));
    }

    @Override
    protected void init() {
        this.panelW = Math.max(220, Math.min(WANT_W, this.width - 12));
        this.panelH = Math.max(170, Math.min(WANT_H, this.height - 12));
        this.left = Math.max(2, (this.width - panelW) / 2);
        this.top = Math.max(2, (this.height - panelH) / 2);
        List<TcpAttachmentS2CPacket.GunEntry> guns = TcpAttachmentData.guns();
        if (activeGun == null && !guns.isEmpty()) activeGun = guns.get(0).slotName();
        rebuild();
    }

    @Override
    public void tick() {
        if (builtVersion != TcpAttachmentData.version()) rebuild();
    }

    private TcpAttachmentS2CPacket.GunEntry currentGun() {
        return activeGun == null ? null : TcpAttachmentData.gun(activeGun);
    }

    private TcpAttachmentS2CPacket.TypeEntry currentType() {
        TcpAttachmentS2CPacket.GunEntry g = currentGun();
        if (g == null || activeType == null) return null;
        for (TcpAttachmentS2CPacket.TypeEntry t : g.types()) {
            if (t.type().equals(activeType)) return t;
        }
        return null;
    }

    private void rebuild() {
        clearWidgets();
        listButtons.clear();
        listButtonIds.clear();
        builtVersion = TcpAttachmentData.version();

        boolean editable = TcpAttachmentData.canEdit();
        List<TcpAttachmentS2CPacket.GunEntry> guns = TcpAttachmentData.guns();
        int leftColW = Math.max(60, Math.min(LEFT_W, panelW / 2 - 24));

        int rowCount = Math.max(1, (guns.size() + 1) / 2);
        // ★ v74：给数值面板留位置（最多 7 行 × 12px + 表头 ≈ 96px）
        int statRows = 1;
        for (TcpAttachmentS2CPacket.GunEntry ge : guns) {
            statRows = Math.max(statRows, Math.min(7, ge.stats().size()));
        }
        int statPanelH = statRows * 12 + 22;
        int previewH = Math.max(70, Math.min(PREVIEW_H,
                panelH - 96 - rowCount * 15 - statPanelH));

        this.pvX = left + 10;
        this.pvY = top + 30;
        this.pvW = leftColW;
        this.pvH = previewH;

        int gunY = top + 30 + previewH + 6;
        int halfW = (leftColW - 4) / 2;
        for (int i = 0; i < guns.size() && i < 6; i++) {
            TcpAttachmentS2CPacket.GunEntry g = guns.get(i);
            final String slotName = g.slotName();
            int col = i % 2;
            int row = i / 2;
            Button b = Button.builder(Component.literal((slotName.equals(activeGun) ? ">" : " ") + fit(g.label(), halfW - 6)),
                            btn -> {
                                activeGun = slotName;
                                activeType = null;
                                page = 0;
                                rotY = ROT_START_Y;
                                rotX = 0.0f;
                                rebuild();
                            })
                    .bounds(left + 10 + col * (halfW + 4), gunY + row * 15, halfW, 14)
                    .build();
            addRenderableWidget(b);
        }

        TcpAttachmentS2CPacket.GunEntry gun = currentGun();
        int rx = left + 10 + leftColW + 14;
        int rw = left + panelW - 10 - rx;
        int ry = top + 30;

        int typeY = ry;
        if (gun != null) {
            for (int i = 0; i < gun.types().size(); i++) {
                TcpAttachmentS2CPacket.TypeEntry t = gun.types().get(i);
                final String type = t.type();
                String cur = t.current() == null ? "—" : TcpGunNames.attachmentName(t.current());
                String label = LoadoutConfig.typeLabel(type) + "：" + cur;
                Button b = Button.builder(Component.literal((type.equals(activeType) ? "> " : "  ")
                                + fit(label, rw - 8)),
                                btn -> {
                                    activeType = type;
                                    page = 0;
                                    rebuild();
                                })
                        .bounds(rx, typeY + i * 16, rw, 15)
                        .build();
                addRenderableWidget(b);
            }
            typeY += gun.types().size() * 16 + 4;
        }

        TcpAttachmentS2CPacket.TypeEntry t = currentType();
        if (t != null) {
            List<String> options = t.options();
            int pages = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
            if (page >= pages) page = pages - 1;
            if (page < 0) page = 0;

            int bottomReserve = 46;
            int rows = Math.max(3, (top + panelH - bottomReserve - typeY) / 15);
            int shown = Math.min(Math.min(PER_PAGE, rows), Math.max(0, options.size() - page * PER_PAGE));
            for (int i = 0; i < shown; i++) {
                String id = options.get(page * PER_PAGE + i);
                boolean selected = id.equals(t.current());
                Button b = Button.builder(Component.literal((selected ? "> " : "   ")
                                + fit(TcpGunNames.attachmentName(id), rw - 8)), btn ->
                                TcpNetwork.CHANNEL.sendToServer(
                                        new TcpAttachmentSelectC2SPacket(activeGun, activeType, selected ? "" : id)))
                        .bounds(rx, typeY + i * 15, rw, 14)
                        .build();
                b.active = editable;
                addRenderableWidget(b);
                listButtons.add(b);
                listButtonIds.add(new String[]{activeType, id});
            }
            int btnY = top + panelH - 26;
            if (t.current() != null) {
                Button un = Button.builder(Component.literal("卸下"), btn ->
                                TcpNetwork.CHANNEL.sendToServer(
                                        new TcpAttachmentSelectC2SPacket(activeGun, activeType, "")))
                        .bounds(rx, btnY, 56, 16).build();
                un.active = editable;
                addRenderableWidget(un);
            }
            if (pages > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
                    page = Math.max(0, page - 1);
                    rebuild();
                }).bounds(rx + rw - 44, btnY, 20, 16).build());
                addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
                    page = Math.min(pages - 1, page + 1);
                    rebuild();
                }).bounds(rx + rw - 22, btnY, 20, 16).build());
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        UiDraw.panelEx(g, left, top, panelW, panelH, UiDraw.RADIUS_LARGE, 0xFF3A4A5A, 0xF01A2432, 0xE60D131C, 0xFFFFC93C);
        UiDraw.topBarRounded(g, left, top, panelW, 24, UiDraw.RADIUS_LARGE, 0xFF1B2A41);
        g.drawString(this.font, "配件编辑", left + 10, top + 8, 0xFFFFFFF0, false);

        if (!TcpAttachmentData.received()) {
            g.drawString(this.font, "读取中…", left + 14, top + 60, 0xFFCCCCCC, false);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        TcpAttachmentS2CPacket.GunEntry gun = currentGun();
        if (gun == null) {
            g.drawString(this.font, "没有可编辑的枪械（loadout.json 里没配枪 / TaCZ 没装）",
                    left + 14, top + 60, 0xFFFFAA66, false);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        hoverType = null;
        hoverId = null;
        AbstractWidget hoverW = null;
        if (TcpAttachmentData.canEdit()) {
            for (int i = 0; i < listButtons.size() && i < listButtonIds.size(); i++) {
                AbstractWidget w = listButtons.get(i);
                if (mouseX >= w.getX() && mouseX < w.getX() + w.getWidth()
                        && mouseY >= w.getY() && mouseY < w.getY() + w.getHeight()) {
                    hoverType = listButtonIds.get(i)[0];
                    hoverId = listButtonIds.get(i)[1];
                    hoverW = w;
                    break;
                }
            }
        }

        if (hoverW != null) {
            int ox = hoverW.getX() - 1, oy = hoverW.getY() - 1;
            int ow = hoverW.getWidth() + 2, oh = hoverW.getHeight() + 2;
            g.fill(ox, oy, ox + ow, oy + 1, 0xFFFFC93C);
            g.fill(ox, oy + oh - 1, ox + ow, oy + oh, 0xFFFFC93C);
            g.fill(ox, oy + 1, ox + 1, oy + oh - 1, 0xFFFFC93C);
            g.fill(ox + ow - 1, oy + 1, ox + ow, oy + oh - 1, 0xFFFFC93C);
        }

        int px = pvX;
        int py = pvY;
        int leftColW = pvW;
        int previewH = pvH;

        UiDraw.fillRounded(g, px - 1, py - 1, leftColW + 2, previewH + 2, UiDraw.RADIUS_MEDIUM + 1,
                hoverId != null ? 0xFFFFC93C : 0xFF2A3A4A);
        UiDraw.fillVGradient(g, px, py, leftColW, previewH, UiDraw.RADIUS_MEDIUM, 0xFF1B2634, 0xFF0E141C);

        ItemStack preview = previewStack(gun, hoverType, hoverId);
        if (!preview.isEmpty()) {

            markTaczGuiRender();
            Minecraft mc = Minecraft.getInstance();
            var buffers = mc.renderBuffers().bufferSource();
            float cx = px + leftColW / 2.0f;
            float cy = py + previewH / 2.0f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 100f);
            g.pose().scale(GUN_SCALE, GUN_SCALE, GUN_SCALE);

            g.pose().mulPose(Axis.YP.rotationDegrees(rotY));
            g.pose().mulPose(Axis.XP.rotationDegrees(rotX));
            boolean rendered = false;
            try {
                var ext = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(preview);
                var bewlr = ext == null ? null : ext.getCustomRenderer();
                if (bewlr != null && bewlr.getClass().getName().startsWith("com.tacz")) {
                    g.pose().pushPose();
                    g.pose().scale(16f, -16f, 16f);
                    if (GUN_OFFSET_X != 0f || GUN_OFFSET_Y != 0f) {
                        g.pose().translate(GUN_OFFSET_X, GUN_OFFSET_Y, 0f);
                    }

                    bewlr.renderByItem(preview, previewContext(), g.pose(), buffers,
                            0xF000F0, OverlayTexture.NO_OVERLAY);
                    g.pose().popPose();
                    rendered = true;
                }
            } catch (Throwable ignored) {
            }
            if (!rendered) {
                mc.getItemRenderer().renderStatic(preview, previewContext(), 0xF000F0,
                        OverlayTexture.NO_OVERLAY, g.pose(), buffers, mc.level, 0);
            }
            g.pose().popPose();
            buffers.endBatch();
        }

        StringBuilder sb = new StringBuilder();
        for (TcpAttachmentS2CPacket.TypeEntry t : gun.types()) {
            if (t.current() == null) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(LoadoutConfig.typeLabel(t.type())).append(' ').append(TcpGunNames.attachmentName(t.current()));
        }
        g.drawString(this.font, fit(sb.length() == 0 ? "（未装配件）" : sb.toString(), leftColW - 8),
                px + 4, py + previewH - 13, 0xFFBBD0E0, false);

        String hint = TcpAttachmentData.canEdit()
                ? "拖动旋转 · 右键复位 · ESC 返回"
                : "对局中不可修改配件 · ESC 返回";
        g.drawString(this.font, fit(hint, panelW - 20), left + 10, top + panelH - 14, 0xFFAAAAAA, false);

        if (hoverId != null && !hoverId.isBlank()) {
            String label = LoadoutConfig.typeLabel(hoverType) + " " + TcpGunNames.attachmentName(hoverId);
            g.drawString(this.font, fit("预览：" + label, leftColW - 8), px + 4, py + 4, 0xFFFFC93C, true);
            try {
                ItemStack hoverAtt = GunItemFactory.createAttachment(hoverId);
                if (!hoverAtt.isEmpty()) {
                    g.renderItem(hoverAtt, px + leftColW - 22, py + previewH - 20);
                }
            } catch (Throwable ignored) {
            }
        }

        // ★ v74：数值面板（画在预览框和说明文字之间，不抢配件列表的地盘）
        drawStats(g, gun, px, py, leftColW, previewH);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * v74 数值面板：
     * <ul>
     *   <li>左列 —— 枪名 + 每条数值</li>
     *   <li>悬停某个配件时，右侧显示「改后值」和 ↑绿/↓红 的差值</li>
     * </ul>
     * 数值来自服务端（当前生效配件）；悬停对比值在客户端用同一个 GunStats 反射算一遍。
     */
    private void drawStats(GuiGraphics g, TcpAttachmentS2CPacket.GunEntry gun,
                           int px, int py, int previewW, int previewH) {
        List<TcpAttachmentS2CPacket.StatEntry> stats = gun.stats();
        int w = Math.max(previewW, 150);
        int x = px;
        int y = py + previewH + 2;
        int rows = 1 + Math.max(1, stats.size());
        int panelH = rows * 12 + 8;

        UiDraw.fillRounded(g, x, y, w, panelH, UiDraw.RADIUS_MEDIUM, 0xFF1B2634);
        UiDraw.fillRounded(g, x, y, w, 14, UiDraw.RADIUS_MEDIUM, 0xFF243445);

        String title = "数值面板";
        g.drawString(this.font, title, x + 5, y + 3, 0xFFFFC93C, false);

        if (stats.isEmpty()) {
            g.drawString(this.font, "（TaCZ 未装 / 读不到数值）",
                    x + 5, y + 17, 0xFF8090A0, false);
            return;
        }

        // 悬停对比值（改后）
        Map<String, TcpAttachmentS2CPacket.StatEntry> hoverMap = null;
        if (hoverId != null && !hoverId.isBlank()) {
            List<GunStats.Stat> hov = hoverStats(gun);
            if (!hov.isEmpty()) {
                hoverMap = new LinkedHashMap<>();
                for (GunStats.Stat s : hov) {
                    hoverMap.put(s.key(), new TcpAttachmentS2CPacket.StatEntry(
                            s.key(), s.label(), s.value(), s.raw(), s.percent()));
                }
            }
        }

        int rowY = y + 16;
        for (TcpAttachmentS2CPacket.StatEntry s : stats) {
            g.drawString(this.font, fit(s.label(), 56), x + 5, rowY, 0xFFBBD0E0, false);

            String cur = s.value() == null ? "-" : s.value();
            int col = 0xFFE8ECF4;
            String extra = "";
            if (hoverMap != null) {
                TcpAttachmentS2CPacket.StatEntry h = hoverMap.get(s.key());
                if (h != null && Math.abs(h.raw() - s.raw()) > 1.0E-4F) {
                    double diff = h.raw() - s.raw();
                    // 后坐/开镜/重量：越小越好 → 减小算提升
                    boolean lowerBetter = "recoil".equals(s.key()) || "aim".equals(s.key())
                            || "weight".equals(s.key());
                    boolean better = lowerBetter ? diff < 0 : diff > 0;
                    col = better ? 0xFF6BE08A : 0xFFFF7B7B;
                    extra = (diff > 0 ? " ▲" : " ▼") + s.value();
                    cur = h.value();
                }
            }

            int valueX = x + 5 + Math.min(56, this.font.width(fit(s.label(), 56))) + 6;
            g.drawString(this.font, cur, valueX, rowY, col, false);
            if (!extra.isEmpty()) {
                g.drawString(this.font, extra, valueX + this.font.width(cur) + 4, rowY, col, false);
            }
            rowY += 12;
        }
    }

    /** 悬停某配件时的「改后数值」——客户端自己算，同一套 GunStats */
    private List<GunStats.Stat> hoverStats(TcpAttachmentS2CPacket.GunEntry gun) {
        String key = gun.gunId() + "#" + hoverType + "=" + hoverId;
        if (key.equals(statCacheKey)) return statHover;

        List<GunStats.Stat> out = List.of();
        try {
            Map<String, String> atts = new LinkedHashMap<>();
            for (TcpAttachmentS2CPacket.TypeEntry t : gun.types()) {
                if (t.current() != null && !t.current().isBlank()) atts.put(t.type(), t.current());
            }
            if (hoverType != null && hoverId != null && !hoverId.isBlank()) {
                atts.put(hoverType, hoverId);
            }
            out = GunStats.read(gun.gunId(), atts);
        } catch (Throwable ignored) {
        }
        statCacheKey = key;
        statHover = out;
        return out;
    }

    private static void markTaczGuiRender() {
        try {
            Class.forName("com.tacz.guns.util.RenderDistance")
                    .getMethod("markGuiRenderTimestamp")
                    .invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private static ItemDisplayContext previewContext() {
        try {
            return ItemDisplayContext.valueOf(LoadoutConfig.previewContext());
        } catch (Throwable t) {
            return ItemDisplayContext.FIXED;
        }
    }

    private ItemStack previewStack(TcpAttachmentS2CPacket.GunEntry gun, String hType, String hId) {
        try {
            java.util.Map<String, String> atts = new java.util.LinkedHashMap<>();
            for (TcpAttachmentS2CPacket.TypeEntry t : gun.types()) {
                if (t.current() != null && !t.current().isBlank()) atts.put(t.type(), t.current());
            }
            if (hType != null && hId != null && !hId.isBlank()) atts.put(hType, hId);

            String key = gun.gunId() + "#" + atts;
            if (key.equals(cachedPreviewKey)) return cachedPreview;

            int[] na = GunItemFactory.nativeAmmo(gun.gunId());
            ItemStack stack = GunItemFactory.create(gun.gunId(), na[0], na[1], "NATIVE", atts);
            cachedPreviewKey = key;
            cachedPreview = stack;
            return stack;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private String fit(String text, int maxPx) {
        if (text == null) return "";
        if (this.font.width(text) <= maxPx) return text;
        String ellipsis = "…";
        int w = this.font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            int cw = this.font.width(String.valueOf(c));
            if (w + cw > maxPx) break;
            sb.append(c);
            w += cw;
        }
        return sb + ellipsis;
    }


    private boolean inPreview(double mx, double my) {
        return mx >= pvX && mx < pvX + pvW && my >= pvY && my < pvY + pvH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (!inPreview(mx, my)) return false;
        if (button == 1) {
            rotY = ROT_START_Y;
            rotX = 0.0f;
            return true;
        }
        if (button == 0) {
            dragging = true;
            dragAnchorX = mx;
            dragAnchorY = my;
            dragBaseRotY = rotY;
            dragBaseRotX = rotX;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (dragging) {
            rotY = dragBaseRotY + (float) (mx - dragAnchorX) * 1.2f;
            rotX = Math.max(-ROT_MAX_X, Math.min(ROT_MAX_X,
                    dragBaseRotX + (float) (my - dragAnchorY) * 1.0f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
