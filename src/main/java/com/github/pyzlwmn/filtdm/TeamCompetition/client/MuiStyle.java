package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * v87：ModernUI 统一样式（照 BlockOffensive 1.3.0 的写法）。
 *
 * <p>BO 1.3.0 的商店界面就是真 ModernUI：`ShapeDrawable` 画圆角底 + `setStroke` 描边 +
 * 半透明 + 动画。这里抽一套公共样式，给设置页/配件页/背包页复用，
 * 不再用原版那种方块按钮。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MuiStyle {

    /** 面板底色（深蓝黑，半透明） */
    public static final int PANEL = 0xE60D131A;
    /** 卡片底色 */
    public static final int CARD = 0xD9161F29;
    /** 卡片 hover / 选中 */
    public static final int CARD_SELECTED = 0xF0203040;
    /** 主色（强调） */
    public static final int ACCENT = 0xFF4A9EFF;
    /** 描边 */
    public static final int STROKE = 0x553A4655;
    /** 分割线 */
    public static final int LINE = 0x33A8B6C8;

    public static final int TEXT = 0xFFF2F5F9;
    public static final int TEXT_DIM = 0xFFB9C2CF;
    public static final int TEXT_HINT = 0xFF6F7D8C;
    public static final int TEXT_OK = 0xFF46C46A;

    private static final float RADIUS_PANEL = 10.0f;
    private static final float RADIUS_CARD = 6.0f;

    private MuiStyle() {
    }

    /** 圆角纯色底（BO 同款 ShapeDrawable 写法）。 */
    public static ShapeDrawable round(int color, float radius) {
        ShapeDrawable d = new ShapeDrawable();
        d.setShape(0);           // RECTANGLE
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** 圆角 + 描边。 */
    public static ShapeDrawable roundStroke(int color, float radius, int strokeColor, int strokeWidth) {
        ShapeDrawable d = round(color, radius);
        d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    public static ShapeDrawable panel() {
        return roundStroke(PANEL, RADIUS_PANEL, STROKE, 1);
    }

    public static ShapeDrawable card(boolean selected) {
        return roundStroke(selected ? CARD_SELECTED : CARD, RADIUS_CARD,
                selected ? ACCENT : STROKE, selected ? 2 : 1);
    }

    /** 圆角卡片按钮（带内边距、自动换行）。 */
    public static Button cardButton(Context ctx, String text, boolean selected, Runnable action) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setTextSize(13.0f);
        b.setTextColor(selected ? ACCENT : TEXT);
        b.setPadding(12, 8, 12, 8);
        b.setBackground(card(selected));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 3, 0, 3);
        b.setLayoutParams(lp);
        if (action != null) {
            b.setOnClickListener(v -> action.run());
        }
        return b;
    }

    /** 主按钮（强调色）。 */
    public static Button primaryButton(Context ctx, String text, Runnable action) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setTextSize(13.0f);
        b.setTextColor(0xFFFFFFFF);
        b.setPadding(16, 9, 16, 9);
        b.setBackground(roundStroke(0xFF1F5FA8, RADIUS_CARD, 0xFF2F7FD0, 1));
        if (action != null) {
            b.setOnClickListener(v -> action.run());
        }
        return b;
    }

    /** 次级按钮。 */
    public static Button ghostButton(Context ctx, String text, Runnable action) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setTextSize(13.0f);
        b.setTextColor(TEXT_DIM);
        b.setPadding(14, 8, 14, 8);
        b.setBackground(card(false));
        if (action != null) {
            b.setOnClickListener(v -> action.run());
        }
        return b;
    }

    public static TextView title(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(20.0f);
        tv.setTextColor(TEXT);
        tv.setPadding(0, 0, 0, 6);
        return tv;
    }

    public static TextView section(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(14.0f);
        tv.setTextColor(ACCENT);
        tv.setPadding(0, 16, 0, 6);
        return tv;
    }

    public static TextView label(Context ctx, String text, int color, int size) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize((float) size);
        tv.setTextColor(color);
        tv.setPadding(0, 4, 0, 4);
        return tv;
    }

    /** 细分割线。 */
    public static View divider(Context ctx) {
        View v = new View(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, 8, 0, 8);
        v.setLayoutParams(lp);
        v.setBackground(round(LINE, 0.5f));
        return v;
    }
}
