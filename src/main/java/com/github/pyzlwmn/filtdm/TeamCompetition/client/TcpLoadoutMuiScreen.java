package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpAttachmentRequestC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutPresetC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutSelectC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpRefitC2SPacket;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * v89：ModernUI 版「装备编辑 / 背包页」（平滑 UI）。
 *
 * <p>替换原版 {@link TcpLoadoutScreen}：数据源 {@link TcpLoadoutData}、发包
 * ({@link TcpLoadoutSelectC2SPacket} / {@link TcpLoadoutPresetC2SPacket} /
 * {@link TcpRefitC2SPacket} / {@link TcpAttachmentRequestC2SPacket}) 全部复用，
 * 只把界面换成 ModernUI + {@link MuiStyle} 圆角样式。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TcpLoadoutMuiScreen extends Fragment {

    private static final int PER_PAGE = 9;
    private static final int VERTICAL = 1;
    private static final int HORIZONTAL = 0;

    private static TcpLoadoutMuiScreen INSTANCE;

    private LinearLayout root;
    private int activeSlot = 0;
    private int page = 0;
    private boolean renaming = false;
    private int builtVersion = -1;
    private boolean rebuildQueued = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context ctx = requireContext();
        INSTANCE = this;

        ScrollView scroll = new ScrollView(ctx);
        root = new LinearLayout(ctx);
        root.setOrientation(VERTICAL);
        root.setPadding(26, 20, 26, 24);
        root.setBackground(MuiStyle.panel());
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rebuild();
        return scroll;
    }

    @Override
    public void onDestroyView() {
        if (INSTANCE == this) INSTANCE = null;
        root = null;
        super.onDestroyView();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TcpLoadoutMuiScreen screen = INSTANCE;
        if (screen == null || screen.root == null) return;
        if (screen.builtVersion != TcpLoadoutData.version() && !screen.rebuildQueued) {
            // ★ ModernUI 的 View 只能在它自己的 UI 线程改，客户端 tick 是渲染线程
            screen.rebuildQueued = true;
            screen.root.post(() -> {
                screen.rebuildQueued = false;
                screen.rebuild();
            });
        }
    }

    private void rebuild() {
        if (root == null) return;
        Context ctx = requireContext();
        root.removeAllViews();
        builtVersion = TcpLoadoutData.version();

        boolean editable = TcpLoadoutData.canEdit();
        boolean switchable = TcpLoadoutData.canSwitch();

        root.addView(MuiStyle.title(ctx, "装备编辑"));
        root.addView(MuiStyle.label(ctx,
                TcpLoadoutData.inMatch() ? "对局中：可换背包，装备锁定" : "未开局：可自由编辑",
                MuiStyle.TEXT_HINT, 12));
        root.addView(MuiStyle.divider(ctx));

        if (!TcpLoadoutData.received()) {
            root.addView(MuiStyle.label(ctx, "正在读取你的可用装备…", MuiStyle.TEXT_DIM, 13));
            return;
        }
        if (!TcpLoadoutData.enabled()) {
            root.addView(MuiStyle.label(ctx, "背包系统已关闭（地图设置 loadout/enabled）",
                    MuiStyle.TEXT_HINT, 13));
            return;
        }

        // ---- 背包预设 ----
        root.addView(MuiStyle.section(ctx, "背包预设"));
        LinearLayout presetRow = new LinearLayout(ctx);
        presetRow.setOrientation(HORIZONTAL);

        for (TcpLoadoutS2CPacket.PresetData preset : TcpLoadoutData.presets()) {
            final String id = preset.id();
            Button b = MuiStyle.ghostButton(ctx,
                    (preset.active() ? "▶ " : "") + preset.name(), null);
            b.setTextSize(12);
            b.setEnabled(switchable && !preset.active());
            b.setOnClickListener(v -> TcpNetwork.CHANNEL.sendToServer(
                    new TcpLoadoutPresetC2SPacket("switch", id, "")));
            presetRow.addView(b, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        }
        root.addView(presetRow);

        LinearLayout presetTools = new LinearLayout(ctx);
        presetTools.setOrientation(HORIZONTAL);

        Button add = MuiStyle.ghostButton(ctx, "＋ 新建", () ->
                TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("create", "", "")));
        add.setEnabled(editable && TcpLoadoutData.presets().size() < TcpLoadoutData.maxPresets());
        presetTools.addView(add);

        Button rename = MuiStyle.ghostButton(ctx, renaming ? "取消改名" : "改名", () -> {
            renaming = !renaming;
            rebuild();
        });
        rename.setEnabled(editable);
        presetTools.addView(rename);

        TcpLoadoutS2CPacket.PresetData active = TcpLoadoutData.activePreset();
        Button del = MuiStyle.ghostButton(ctx, "删除", () -> {
            if (active != null) {
                TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("delete", active.id(), ""));
            }
        });
        del.setEnabled(editable && TcpLoadoutData.presets().size() > 1);
        presetTools.addView(del);
        root.addView(presetTools);

        if (renaming && active != null) {
            EditText input = new EditText(ctx);
            input.setText(active.name());
            input.setTextSize(13);
            root.addView(input);

            Button save = MuiStyle.primaryButton(ctx, "保存名字", () ->
                    TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket(
                            "rename", active.id(),
                            String.valueOf(input.getText()).trim())));
            root.addView(save);
        }

        // ---- 槽位 ----
        root.addView(MuiStyle.divider(ctx));
        root.addView(MuiStyle.section(ctx, "槽位"));

        List<TcpLoadoutS2CPacket.SlotData> slots = TcpLoadoutData.slots();
        for (int i = 0; i < slots.size(); i++) {
            TcpLoadoutS2CPacket.SlotData data = slots.get(i);
            final int slot = data.slot();
            root.addView(MuiStyle.cardButton(ctx,
                    (slot == activeSlot ? "● " : "○ ") + data.label(),
                    slot == activeSlot,
                    () -> {
                        activeSlot = slot;
                        page = 0;
                        rebuild();
                    }));
        }

        LinearLayout tools = new LinearLayout(ctx);
        tools.setOrientation(HORIZONTAL);

        Button refit = MuiStyle.ghostButton(ctx, "改装", () ->
                TcpNetwork.CHANNEL.sendToServer(new TcpRefitC2SPacket("start", slotNameOf(activeSlot))));
        refit.setEnabled(editable && !TcpLoadoutData.inMatch());
        tools.addView(refit);

        Button attach = MuiStyle.ghostButton(ctx, "配件编辑", () ->
                TcpNetwork.CHANNEL.sendToServer(new TcpAttachmentRequestC2SPacket()));
        attach.setEnabled(editable);
        tools.addView(attach);
        root.addView(tools);

        // ---- 可选装备 ----
        root.addView(MuiStyle.divider(ctx));
        TcpLoadoutS2CPacket.SlotData sel = TcpLoadoutData.slot(activeSlot);
        List<String> options = sel == null ? List.of() : sel.options();
        String current = sel == null ? null : sel.current();

        int pages = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        root.addView(MuiStyle.section(ctx, "可选装备"
                + (pages > 1 ? "（第 " + (page + 1) + "/" + pages + " 页）" : "")));

        if (options.isEmpty()) {
            root.addView(MuiStyle.label(ctx, "这个槽位没有可用装备（配置没写 / 皮肤未解锁）",
                    MuiStyle.TEXT_HINT, 12));
        }

        int shown = Math.min(PER_PAGE, Math.max(0, options.size() - page * PER_PAGE));
        for (int i = 0; i < shown; i++) {
            String id = options.get(page * PER_PAGE + i);
            boolean selected = id.equals(current);
            boolean presetPick = !editable && switchable && selected;
            Button b = MuiStyle.cardButton(ctx,
                    (selected ? "✓ " : "   ") + TcpGunNames.display(id),
                    selected,
                    () -> TcpNetwork.CHANNEL.sendToServer(
                            new TcpLoadoutSelectC2SPacket(activeSlot, id)));
            b.setEnabled(editable || presetPick);
            root.addView(b);
        }

        if (pages > 1) {
            LinearLayout nav = new LinearLayout(ctx);
            nav.setOrientation(HORIZONTAL);
            Button prev = MuiStyle.ghostButton(ctx, "< 上一页", () -> {
                page = Math.max(0, page - 1);
                rebuild();
            });
            Button next = MuiStyle.ghostButton(ctx, "下一页 >", () -> {
                page = Math.min(pages - 1, page + 1);
                rebuild();
            });
            nav.addView(prev);
            nav.addView(next);
            root.addView(nav);
        }

        // ---- 关闭 ----
        root.addView(MuiStyle.divider(ctx));
        root.addView(MuiStyle.primaryButton(ctx, "关闭",
                () -> Minecraft.getInstance().setScreen(null)));
    }

    private String slotNameOf(int slot) {
        return switch (slot) {
            case 0 -> "rifle";
            case 1 -> "pistol";
            case 2 -> "grenade";
            case 3 -> "utility";
            case 4 -> "knife";
            default -> "rifle";
        };
    }
}
