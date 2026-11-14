package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpAttachmentS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpAttachmentSelectC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
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
 * v86：ModernUI 版配件编辑页（平滑 UI）。
 *
 * <p>{@link TcpAttachmentData#createAttachmentScreen()} 早就留了 ModernUI 分支，
 * 通过反射找这个类；之前类不存在，所以一直退回原版 {@link TcpAttachmentScreen}。
 * 这里补上实现：同一个数据源 {@link TcpAttachmentData}、同一个发包
 * {@link TcpAttachmentSelectC2SPacket}，只把界面换成 ModernUI。</p>
 *
 * <p>数据更新走版本号轮询（{@link TcpAttachmentData#version()}），
 * 服务端回包后自动重建列表，不需要手动刷新。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TcpAttachmentMuiScreen extends Fragment {

    private static final int C_TITLE = 0xFFF2F5F9;
    private static final int C_SECTION = 0xFF9FD0FF;
    private static final int C_LABEL = 0xFFB9C2CF;
    private static final int C_HINT = 0xFF6F7D8C;
    private static final int C_ON = 0xFF46C46A;

    private static final int VERTICAL = 1;
    private static final int HORIZONTAL = 0;

    private static TcpAttachmentMuiScreen INSTANCE;

    private LinearLayout root;
    private String activeGun;
    private String activeType;
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

        List<TcpAttachmentS2CPacket.GunEntry> guns = TcpAttachmentData.guns();
        if (activeGun == null && !guns.isEmpty()) activeGun = guns.get(0).slotName();

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
        TcpAttachmentMuiScreen screen = INSTANCE;
        if (screen == null) return;
        if (screen.root == null) return;
        if (screen.builtVersion != TcpAttachmentData.version() && !screen.rebuildQueued) {
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
        root.removeAllViews();
        builtVersion = TcpAttachmentData.version();

        boolean editable = TcpAttachmentData.canEdit();
        List<TcpAttachmentS2CPacket.GunEntry> guns = TcpAttachmentData.guns();

        root.addView(MuiStyle.title(requireContext(), "配件编辑"));
        root.addView(MuiStyle.label(requireContext(),
                editable ? "点配件即装 · 再点一次卸下" : "🔒 对局中不可改装（只能换背包/已配好的枪）",
                editable ? MuiStyle.TEXT_HINT : 0xFFFFA166, 12));
        root.addView(MuiStyle.divider(requireContext()));

        if (!TcpAttachmentData.received() || guns.isEmpty()) {
            addText("正在读取配件数据…", 13, C_LABEL);
            return;
        }

        // ---- 枪械选择 ----
        root.addView(MuiStyle.section(requireContext(), "枪械"));
        for (TcpAttachmentS2CPacket.GunEntry gun : guns) {
            root.addView(MuiStyle.cardButton(requireContext(),
                    (gun.slotName().equals(activeGun) ? "● " : "○ ") + gun.label(),
                    gun.slotName().equals(activeGun),
                    () -> {
                        activeGun = gun.slotName();
                        activeType = null;
                        rebuild();
                    }));
        }

        TcpAttachmentS2CPacket.GunEntry gun = currentGun();
        if (gun == null) return;

        // ---- 数值面板 ----
        if (gun.stats() != null && !gun.stats().isEmpty()) {
            root.addView(MuiStyle.section(requireContext(), "数据"));
            for (TcpAttachmentS2CPacket.StatEntry stat : gun.stats()) {
                root.addView(MuiStyle.label(requireContext(),
                        stat.label() + "：" + stat.value(), MuiStyle.TEXT_DIM, 12));
            }
        }

        // ---- 类型页签 ----
        if (!gun.types().isEmpty()) {
            if (activeType == null) activeType = gun.types().get(0).type();
            LinearLayout tabs = new LinearLayout(requireContext());
            tabs.setOrientation(HORIZONTAL);
            for (TcpAttachmentS2CPacket.TypeEntry type : gun.types()) {
                Button b = new Button(requireContext());
                b.setText((type.type().equals(activeType) ? "【" : "") + type.type() + (type.type().equals(activeType) ? "】" : ""));
                b.setTextSize(12);
                b.setOnClickListener(v -> {
                    activeType = type.type();
                    rebuild();
                });
                b.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                tabs.addView(b);
            }
            root.addView(tabs);
        }

        TcpAttachmentS2CPacket.TypeEntry type = currentType();
        if (type == null) return;

        // ---- 选项列表 ----
        root.addView(MuiStyle.section(requireContext(), "配件（当前：" + (type.current() == null || type.current().isEmpty()
                ? "无" : TcpGunNames.attachmentName(type.current())) + "）"));

        if (type.options().isEmpty()) {
            addText("这个槽位没有可选配件", 12, C_HINT);
        }
        for (String id : type.options()) {
            boolean selected = id.equals(type.current());
            Button opt = MuiStyle.cardButton(requireContext(),
                    (selected ? "✓ " : (editable ? "   " : "🔒 ")) + TcpGunNames.attachmentName(id),
                    selected,
                    () -> TcpNetwork.CHANNEL.sendToServer(new TcpAttachmentSelectC2SPacket(
                            activeGun, activeType, selected ? "" : id)));
            opt.setEnabled(editable);
            root.addView(opt);
        }

        // ---- 底部 ----
        LinearLayout bottom = new LinearLayout(requireContext());
        bottom.setOrientation(HORIZONTAL);

        if (type.current() != null && !type.current().isEmpty()) {
            Button unload = MuiStyle.ghostButton(requireContext(), "卸下", () ->
                    TcpNetwork.CHANNEL.sendToServer(
                            new TcpAttachmentSelectC2SPacket(activeGun, activeType, "")));
            unload.setEnabled(editable);
            bottom.addView(unload);
        }

        bottom.addView(MuiStyle.primaryButton(requireContext(), "关闭",
                () -> Minecraft.getInstance().setScreen(null)));

        root.addView(bottom);
    }

    private TcpAttachmentS2CPacket.GunEntry currentGun() {
        return activeGun == null ? null : TcpAttachmentData.gun(activeGun);
    }

    private TcpAttachmentS2CPacket.TypeEntry currentType() {
        TcpAttachmentS2CPacket.GunEntry gun = currentGun();
        if (gun == null) return null;
        for (TcpAttachmentS2CPacket.TypeEntry t : gun.types()) {
            if (t.type().equals(activeType)) return t;
        }
        return gun.types().isEmpty() ? null : gun.types().get(0);
    }

    private void addText(String text, int size, int color) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setPadding(0, 6, 0, 2);
        root.addView(tv);
    }
}
