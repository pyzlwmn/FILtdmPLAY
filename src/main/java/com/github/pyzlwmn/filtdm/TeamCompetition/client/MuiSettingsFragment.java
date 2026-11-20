package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.SeekBar;
import icyllis.modernui.widget.SwitchButton;
import icyllis.modernui.widget.TextView;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * v85：ModernUI 版「FIL 对战设置」（平滑 UI）。
 *
 * <p>全部用 ModernUI 的控件（圆角、阴影、平滑滚动、原生动画），
 * 不再用原版 Screen 那种方块按钮。入口仍然是 Esc 菜单里的按钮，
 * 用 {@code MuiModApi.openScreen(new MuiSettingsFragment())} 打开。</p>
 *
 * <p>改动即时生效（直接写 ClientConfig 静态字段 + spec），
 * 关闭界面时统一落盘。</p>
 */
@OnlyIn(Dist.CLIENT)
public class MuiSettingsFragment extends Fragment {

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

    private static final int C_TITLE = 0xFFF2F5F9;
    private static final int C_SECTION = 0xFF9FD0FF;
    private static final int C_LABEL = 0xFFB9C2CF;
    private static final int C_HINT = 0xFF6F7D8C;

    /** 关闭时回到哪个界面（由入口设置）。 */
    public static net.minecraft.client.gui.screens.Screen previousScreen = null;

    private static final int VERTICAL = 1;
    private static final int HORIZONTAL = 0;

    // 小地图
    private boolean radarOn;
    private double radarScale;
    private String anchor;
    private int radarX;
    private int radarY;

    // 物品栏
    private boolean hotbarOn;
    private double hotbarScale;
    private int hotbarX;
    private int hotbarY;
    private boolean hotbarAmmo;

    // 状态条
    private boolean statusOn;
    private double statusScale;
    private int statusX;
    private int statusY;

    // 受击方向（v1.1.0）
    private boolean hitdirOn;
    private double hitdirRadius;
    private double hitdirScale;
    private double hitdirDuration;
    private double hitdirAlpha;
    private String hitdirColor;
    private int hitdirX;
    private int hitdirY;

    private LinearLayout root;

    private TextView radarSwitchLabel;
    private TextView radarScaleLabel;
    private TextView radarXLabel;
    private TextView radarYLabel;

    private TextView hotbarSwitchLabel;
    private TextView hotbarScaleLabel;
    private TextView hotbarXLabel;
    private TextView hotbarYLabel;
    private TextView hotbarAmmoLabel;

    private TextView statusSwitchLabel;
    private TextView statusScaleLabel;
    private TextView statusXLabel;
    private TextView statusYLabel;

    private TextView hitdirSwitchLabel;
    private TextView hitdirRadiusLabel;
    private TextView hitdirScaleLabel;
    private TextView hitdirDurationLabel;
    private TextView hitdirAlphaLabel;
    private TextView hitdirColorLabel;
    private TextView hitdirXLabel;
    private TextView hitdirYLabel;

    private final TextView[] anchorViews = new TextView[9];

    public MuiSettingsFragment() {
        radarOn = ClientConfig.radarEnabled;
        radarScale = ClientConfig.radarScale;
        anchor = ClientConfig.radarAnchor;
        radarX = ClientConfig.radarOffsetX;
        radarY = ClientConfig.radarOffsetY;

        hotbarOn = ClientConfig.hotbarEnabled;
        hotbarScale = ClientConfig.hotbarScale;
        hotbarX = ClientConfig.hotbarOffsetX;
        hotbarY = ClientConfig.hotbarOffsetY;
        hotbarAmmo = ClientConfig.hotbarShowAmmo;

        statusOn = ClientConfig.statusEnabled;
        statusScale = ClientConfig.statusScale;
        statusX = ClientConfig.statusOffsetX;
        statusY = ClientConfig.statusOffsetY;

        hitdirOn = ClientConfig.hitDirEnabled;
        hitdirRadius = ClientConfig.hitDirRadius;
        hitdirScale = ClientConfig.hitDirScale;
        hitdirDuration = ClientConfig.hitDirDuration;
        hitdirAlpha = ClientConfig.hitDirAlpha;
        hitdirColor = ClientConfig.hitDirColor;
        hitdirX = ClientConfig.hitDirOffsetX;
        hitdirY = ClientConfig.hitDirOffsetY;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context ctx = requireContext();

        ScrollView scroll = new ScrollView(ctx);

        root = new LinearLayout(ctx);
        root.setOrientation(VERTICAL);
        root.setPadding(28, 24, 28, 24);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(MuiStyle.title(ctx, "FIL 对战设置"));
        root.addView(MuiStyle.label(ctx, "拖动立即生效 · 关闭自动保存", MuiStyle.TEXT_HINT, 12));

        // ---------- 小地图 ----------
        root.addView(MuiStyle.divider(ctx));
        root.addView(MuiStyle.section(ctx, "小地图"));

        radarSwitchLabel = addRowLabel(ctx, "显示雷达：" + (radarOn ? "开" : "关"));
        addSwitch(ctx, radarOn, on -> {
            radarOn = on;
            radarSwitchLabel.setText("显示雷达：" + (radarOn ? "开" : "关"));
            push();
        });

        radarScaleLabel = addRowLabel(ctx, String.format("大小：%.2f×", radarScale));
        addSeek(ctx, 50, 300, radarScale, v -> {
            radarScale = v / 100.0;
            radarScaleLabel.setText(String.format("大小：%.2f×", radarScale));
            push();
        });

        addText(ctx, "位置", 12, C_LABEL);
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(VERTICAL);
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                final int i = row * 3 + col;
                Button b = new Button(ctx);
                b.setText(anchorText(i));
                b.setTextSize(12);
                b.setOnClickListener(v -> {
                    anchor = ANCHORS[i];
                    refreshAnchors();
                    push();
                });
                b.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                line.addView(b);
                anchorViews[i] = b;
            }
            grid.addView(line);
        }
        root.addView(grid);

        radarXLabel = addRowLabel(ctx, "水平偏移：" + radarX + " px");
        addSeek(ctx, -400, 400, radarX, v -> {
            radarX = (int) Math.round(v);
            radarXLabel.setText("水平偏移：" + radarX + " px");
            push();
        });

        radarYLabel = addRowLabel(ctx, "垂直偏移：" + radarY + " px");
        addSeek(ctx, -400, 400, radarY, v -> {
            radarY = (int) Math.round(v);
            radarYLabel.setText("垂直偏移：" + radarY + " px");
            push();
        });

        // ---------- 物品栏 ----------
        root.addView(MuiStyle.divider(ctx));
        root.addView(MuiStyle.section(ctx, "物品栏"));

        hotbarSwitchLabel = addRowLabel(ctx, "显示物品栏：" + (hotbarOn ? "开" : "关"));
        addSwitch(ctx, hotbarOn, on -> {
            hotbarOn = on;
            hotbarSwitchLabel.setText("显示物品栏：" + (hotbarOn ? "开" : "关"));
            push();
        });

        hotbarScaleLabel = addRowLabel(ctx, String.format("大小：%.2f×", hotbarScale));
        addSeek(ctx, 50, 300, hotbarScale, v -> {
            hotbarScale = v / 100.0;
            hotbarScaleLabel.setText(String.format("大小：%.2f×", hotbarScale));
            push();
        });

        hotbarXLabel = addRowLabel(ctx, "水平偏移：" + hotbarX + " px");
        addSeek(ctx, -400, 400, hotbarX, v -> {
            hotbarX = (int) Math.round(v);
            hotbarXLabel.setText("水平偏移：" + hotbarX + " px");
            push();
        });

        hotbarYLabel = addRowLabel(ctx, "垂直偏移：" + hotbarY + " px");
        addSeek(ctx, -400, 400, hotbarY, v -> {
            hotbarY = (int) Math.round(v);
            hotbarYLabel.setText("垂直偏移：" + hotbarY + " px");
            push();
        });

        hotbarAmmoLabel = addRowLabel(ctx, "显示弹药：" + (hotbarAmmo ? "开" : "关"));
        addSwitch(ctx, hotbarAmmo, on -> {
            hotbarAmmo = on;
            hotbarAmmoLabel.setText("显示弹药：" + (hotbarAmmo ? "开" : "关"));
            push();
        });

        // ---------- 状态条 ----------
        root.addView(MuiStyle.divider(ctx));
        root.addView(MuiStyle.section(ctx, "状态条"));

        statusSwitchLabel = addRowLabel(ctx, "显示血条：" + (statusOn ? "开" : "关"));
        addSwitch(ctx, statusOn, on -> {
            statusOn = on;
            statusSwitchLabel.setText("显示血条：" + (statusOn ? "开" : "关"));
            push();
        });

        statusScaleLabel = addRowLabel(ctx, String.format("大小：%.2f×", statusScale));
        addSeek(ctx, 50, 300, statusScale, v -> {
            statusScale = v / 100.0;
            statusScaleLabel.setText(String.format("大小：%.2f×", statusScale));
            push();
        });

        statusXLabel = addRowLabel(ctx, "水平偏移：" + statusX + " px");
        addSeek(ctx, -400, 400, statusX, v -> {
            statusX = (int) Math.round(v);
            statusXLabel.setText("水平偏移：" + statusX + " px");
            push();
        });

        statusYLabel = addRowLabel(ctx, "垂直偏移：" + statusY + " px");
        addSeek(ctx, -400, 400, statusY, v -> {
            statusY = (int) Math.round(v);
            statusYLabel.setText("垂直偏移：" + statusY + " px");
            push();
        });

        // ---------- 受击方向（v1.1.0） ----------
        root.addView(MuiStyle.divider(ctx));
        root.addView(MuiStyle.section(ctx, "受击方向"));
        addText(ctx, "被打中时在屏幕中心的虚拟圆上显示三角（圆本身不画）", 12, C_HINT);

        hitdirSwitchLabel = addRowLabel(ctx, "显示受击方向：" + (hitdirOn ? "开" : "关"));
        addSwitch(ctx, hitdirOn, on -> {
            hitdirOn = on;
            hitdirSwitchLabel.setText("显示受击方向：" + (hitdirOn ? "开" : "关"));
            push();
        });

        hitdirRadiusLabel = addRowLabel(ctx, "圆半径：" + Math.round(hitdirRadius) + " px");
        addSeek(ctx, 60, 260, hitdirRadius, v -> {
            hitdirRadius = Math.round(v);
            hitdirRadiusLabel.setText("圆半径：" + Math.round(hitdirRadius) + " px");
            push();
        });

        hitdirScaleLabel = addRowLabel(ctx, String.format("三角大小：%.2f×", hitdirScale));
        addSeek(ctx, 40, 200, hitdirScale * 100.0, v -> {
            hitdirScale = v / 100.0;
            hitdirScaleLabel.setText(String.format("三角大小：%.2f×", hitdirScale));
            push();
        });

        hitdirDurationLabel = addRowLabel(ctx, String.format("显示时长：%.1f s", hitdirDuration));
        addSeek(ctx, 10, 80, hitdirDuration * 10.0, v -> {
            hitdirDuration = v / 10.0;
            hitdirDurationLabel.setText(String.format("显示时长：%.1f s", hitdirDuration));
            push();
        });

        hitdirAlphaLabel = addRowLabel(ctx, String.format("不透明度：%.2f", hitdirAlpha));
        addSeek(ctx, 30, 100, hitdirAlpha * 100.0, v -> {
            hitdirAlpha = v / 100.0;
            hitdirAlphaLabel.setText(String.format("不透明度：%.2f", hitdirAlpha));
            push();
        });

        hitdirColorLabel = addRowLabel(ctx, "颜色：" + colorName(hitdirColor));
        Button hitdirColorBtn = new Button(ctx);
        hitdirColorBtn.setText("切换颜色");
        hitdirColorBtn.setTextSize(13);
        hitdirColorBtn.setOnClickListener(v -> {
            hitdirColor = nextColor(hitdirColor);
            hitdirColorLabel.setText("颜色：" + colorName(hitdirColor));
            push();
        });
        root.addView(hitdirColorBtn);

        hitdirXLabel = addRowLabel(ctx, "圆心水平偏移：" + hitdirX + " px");
        addSeek(ctx, -300, 300, hitdirX, v -> {
            hitdirX = (int) Math.round(v);
            hitdirXLabel.setText("圆心水平偏移：" + hitdirX + " px");
            push();
        });

        hitdirYLabel = addRowLabel(ctx, "圆心垂直偏移：" + hitdirY + " px");
        addSeek(ctx, -300, 300, hitdirY, v -> {
            hitdirY = (int) Math.round(v);
            hitdirYLabel.setText("圆心垂直偏移：" + hitdirY + " px");
            push();
        });

        // ---------- 底部按钮 ----------
        LinearLayout bottom = new LinearLayout(ctx);
        bottom.setOrientation(HORIZONTAL);

        Button reset = new Button(ctx);
        reset.setText("恢复默认");
        reset.setTextSize(13);
        reset.setOnClickListener(v -> {
            ClientConfig.defaults();
            reloadFromConfig();
            push();
            refreshAllLabels();
        });

        Button done = new Button(ctx);
        done.setText("完成");
        done.setTextSize(13);
        done.setOnClickListener(v -> {
            push();
            ClientConfig.save();
            net.minecraft.client.Minecraft.getInstance().setScreen(previousScreen);
        });

        bottom.addView(reset);
        bottom.addView(done);
        root.addView(bottom);

        return scroll;
    }

    private void reloadFromConfig() {
        radarOn = ClientConfig.radarEnabled;
        radarScale = ClientConfig.radarScale;
        anchor = ClientConfig.radarAnchor;
        radarX = ClientConfig.radarOffsetX;
        radarY = ClientConfig.radarOffsetY;

        hotbarOn = ClientConfig.hotbarEnabled;
        hotbarScale = ClientConfig.hotbarScale;
        hotbarX = ClientConfig.hotbarOffsetX;
        hotbarY = ClientConfig.hotbarOffsetY;
        hotbarAmmo = ClientConfig.hotbarShowAmmo;

        statusOn = ClientConfig.statusEnabled;
        statusScale = ClientConfig.statusScale;
        statusX = ClientConfig.statusOffsetX;
        statusY = ClientConfig.statusOffsetY;

        hitdirOn = ClientConfig.hitDirEnabled;
        hitdirRadius = ClientConfig.hitDirRadius;
        hitdirScale = ClientConfig.hitDirScale;
        hitdirDuration = ClientConfig.hitDirDuration;
        hitdirAlpha = ClientConfig.hitDirAlpha;
        hitdirColor = ClientConfig.hitDirColor;
        hitdirX = ClientConfig.hitDirOffsetX;
        hitdirY = ClientConfig.hitDirOffsetY;
    }

    private void refreshAllLabels() {
        if (radarSwitchLabel != null) radarSwitchLabel.setText("显示雷达：" + (radarOn ? "开" : "关"));
        if (radarScaleLabel != null) radarScaleLabel.setText(String.format("大小：%.2f×", radarScale));
        if (radarXLabel != null) radarXLabel.setText("水平偏移：" + radarX + " px");
        if (radarYLabel != null) radarYLabel.setText("垂直偏移：" + radarY + " px");
        refreshAnchors();

        if (hotbarSwitchLabel != null) hotbarSwitchLabel.setText("显示物品栏：" + (hotbarOn ? "开" : "关"));
        if (hotbarScaleLabel != null) hotbarScaleLabel.setText(String.format("大小：%.2f×", hotbarScale));
        if (hotbarXLabel != null) hotbarXLabel.setText("水平偏移：" + hotbarX + " px");
        if (hotbarYLabel != null) hotbarYLabel.setText("垂直偏移：" + hotbarY + " px");
        if (hotbarAmmoLabel != null) hotbarAmmoLabel.setText("显示弹药：" + (hotbarAmmo ? "开" : "关"));

        if (statusSwitchLabel != null) statusSwitchLabel.setText("显示血条：" + (statusOn ? "开" : "关"));
        if (statusScaleLabel != null) statusScaleLabel.setText(String.format("大小：%.2f×", statusScale));
        if (statusXLabel != null) statusXLabel.setText("水平偏移：" + statusX + " px");
        if (statusYLabel != null) statusYLabel.setText("垂直偏移：" + statusY + " px");

        if (hitdirSwitchLabel != null) hitdirSwitchLabel.setText("显示受击方向：" + (hitdirOn ? "开" : "关"));
        if (hitdirRadiusLabel != null) hitdirRadiusLabel.setText("圆半径：" + Math.round(hitdirRadius) + " px");
        if (hitdirScaleLabel != null) hitdirScaleLabel.setText(String.format("三角大小：%.2f×", hitdirScale));
        if (hitdirDurationLabel != null) hitdirDurationLabel.setText(String.format("显示时长：%.1f s", hitdirDuration));
        if (hitdirAlphaLabel != null) hitdirAlphaLabel.setText(String.format("不透明度：%.2f", hitdirAlpha));
        if (hitdirColorLabel != null) hitdirColorLabel.setText("颜色：" + colorName(hitdirColor));
        if (hitdirXLabel != null) hitdirXLabel.setText("圆心水平偏移：" + hitdirX + " px");
        if (hitdirYLabel != null) hitdirYLabel.setText("圆心垂直偏移：" + hitdirY + " px");
    }

    private static final String[] COLORS = {"RED", "WHITE", "AMBER", "CYAN"};

    private static String colorName(String key) {
        if (key == null) return "伤害红";
        return switch (key.toUpperCase(java.util.Locale.ROOT)) {
            case "WHITE" -> "纯白";
            case "AMBER" -> "琥珀黄";
            case "CYAN" -> "冷青";
            default -> "伤害红";
        };
    }

    private static String nextColor(String key) {
        int idx = 0;
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equalsIgnoreCase(key)) {
                idx = i;
                break;
            }
        }
        return COLORS[(idx + 1) % COLORS.length];
    }

    private String anchorText(int i) {
        return ANCHOR_NAMES[i] + (anchor.equals(ANCHORS[i]) ? " ✓" : "");
    }

    private void refreshAnchors() {
        for (int i = 0; i < anchorViews.length; i++) {
            if (anchorViews[i] != null) anchorViews[i].setText(anchorText(i));
        }
    }

    private void push() {
        ClientConfig.apply(radarOn, radarScale, anchor, radarX, radarY);
        ClientConfig.applyHotbar(hotbarOn, hotbarScale, hotbarX, hotbarY, hotbarAmmo);
        ClientConfig.applyStatus(statusOn, statusScale, statusX, statusY);
        ClientConfig.applyHitDir(hitdirOn, hitdirRadius, hitdirScale, hitdirDuration,
                hitdirAlpha, hitdirColor, hitdirX, hitdirY);
    }

    @Override
    public void onPause() {
        super.onPause();
        push();
        ClientConfig.save();
    }

    // ---------- 小工具 ----------

    private void addTitle(Context ctx, String text, int size, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setPadding(0, 14, 0, 6);
        root.addView(tv);
    }

    private void addText(Context ctx, String text, int size, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setPadding(0, 2, 0, 4);
        root.addView(tv);
    }

    private TextView addRowLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(C_LABEL);
        tv.setPadding(0, 10, 0, 2);
        root.addView(tv);
        return tv;
    }

    private interface BoolConsumer {
        void accept(boolean v);
    }

    private interface DoubleConsumer {
        void accept(double v);
    }

    private void addSwitch(Context ctx, boolean checked, BoolConsumer onChange) {
        SwitchButton sw = new SwitchButton(ctx);
        sw.setChecked(checked);
        sw.setOnClickListener(v -> onChange.accept(sw.isChecked()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sw.setLayoutParams(lp);
        root.addView(sw);
    }

    private void addSeek(Context ctx, int min, int max, double value, DoubleConsumer onChange) {
        SeekBar bar = new SeekBar(ctx);
        int span = max - min;
        bar.setMax(span);
        bar.setProgress((int) Math.round((value - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                onChange.accept(min + progress);
            }
        });
        root.addView(bar);
    }
}
