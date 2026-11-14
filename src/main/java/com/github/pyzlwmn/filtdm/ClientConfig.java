package com.github.pyzlwmn.filtdm;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * v80：客户端配置（只影响本机显示，不影响服务端判定）。
 *
 * <p>配置文件：{@code config/filtdm-client.toml}。
 * 游戏内「Esc → 战术地图设置」改完会立刻 {@link #apply} + {@link #save()}（热更新，不用重启）。</p>
 */
@Mod.EventBusSubscriber(modid = FilTdm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientConfig {

    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    // ---------- 战术地图雷达 ----------
    private static final ForgeConfigSpec.BooleanValue RADAR_ENABLED = B
            .comment("显示战术地图雷达")
            .define("radar.enabled", true);

    private static final ForgeConfigSpec.DoubleValue RADAR_SCALE = B
            .comment("雷达大小倍率：1.0 = 半径 78px")
            .defineInRange("radar.scale", 1.0, 0.5, 3.0);

    private static final ForgeConfigSpec.ConfigValue<String> RADAR_ANCHOR = B
            .comment("雷达锚点：TOP_LEFT / TOP_CENTER / TOP_RIGHT / MIDDLE_LEFT / MIDDLE_CENTER / MIDDLE_RIGHT / BOTTOM_LEFT / BOTTOM_CENTER / BOTTOM_RIGHT")
            .define("radar.anchor", "BOTTOM_RIGHT");

    private static final ForgeConfigSpec.IntValue RADAR_OFFSET_X = B
            .comment("雷达水平微调（像素，正数向右）")
            .defineInRange("radar.offsetX", 0, -2000, 2000);

    private static final ForgeConfigSpec.IntValue RADAR_OFFSET_Y = B
            .comment("雷达垂直微调（像素，正数向下）。默认 -78 = 叠在右下角物品栏上方")
            .defineInRange("radar.offsetY", -78, -2000, 2000);

    private static final ForgeConfigSpec.BooleanValue RADAR_TERRAIN = B
            .comment("小地图显示地形（客户端本地采样最高方块颜色）")
            .define("radar.terrain", true);

    private static final ForgeConfigSpec.BooleanValue RADAR_SNAP_ABOVE_HOTBAR = B
            .comment("雷达自动吸附在物品栏正上方（避免和物品栏重叠；关掉后完全按 anchor+offset 摆）")
            .define("radar.snapAboveHotbar", true);

    // ---------- 右下角物品栏 ----------
    private static final ForgeConfigSpec.BooleanValue HOTBAR_ENABLED = B
            .comment("自定义物品栏（右下角 5 格）替换原版快捷栏")
            .define("hotbar.enabled", true);

    private static final ForgeConfigSpec.DoubleValue HOTBAR_SCALE = B
            .comment("物品栏大小倍率：1.0 = 格子 48px")
            .defineInRange("hotbar.scale", 1.0, 0.5, 3.0);

    private static final ForgeConfigSpec.IntValue HOTBAR_OFFSET_X = B
            .comment("物品栏水平微调（像素，正数向右）")
            .defineInRange("hotbar.offsetX", 0, -2000, 2000);

    private static final ForgeConfigSpec.IntValue HOTBAR_OFFSET_Y = B
            .comment("物品栏垂直微调（像素，正数向下）")
            .defineInRange("hotbar.offsetY", 0, -2000, 2000);

    private static final ForgeConfigSpec.BooleanValue HOTBAR_SHOW_AMMO = B
            .comment("格子里显示弹药数（需要 TaCZ）")
            .define("hotbar.showAmmo", true);

    // ---------- 状态条（血条/护甲，饥饿值不要） ----------
    private static final ForgeConfigSpec.BooleanValue STATUS_ENABLED = B
            .comment("自定义血条（同时隐藏原版血量/饥饿/护甲）")
            .define("status.enabled", true);

    private static final ForgeConfigSpec.DoubleValue STATUS_SCALE = B
            .comment("状态条大小倍率")
            .defineInRange("status.scale", 1.0, 0.5, 3.0);

    private static final ForgeConfigSpec.IntValue STATUS_OFFSET_X = B
            .comment("状态条水平微调（像素）")
            .defineInRange("status.offsetX", 0, -2000, 2000);

    private static final ForgeConfigSpec.IntValue STATUS_OFFSET_Y = B
            .comment("状态条垂直微调（像素，正数向下）")
            .defineInRange("status.offsetY", 0, -2000, 2000);

    public static final ForgeConfigSpec SPEC = B.build();

    public static boolean radarEnabled = true;
    public static double radarScale = 1.0;
    public static String radarAnchor = "BOTTOM_RIGHT";
    public static int radarOffsetX = 0;
    public static int radarOffsetY = -78;
    public static boolean radarSnapAboveHotbar = true;
    public static boolean radarTerrain = true;

    public static boolean hotbarEnabled = true;
    public static double hotbarScale = 1.0;
    public static int hotbarOffsetX = 0;
    public static int hotbarOffsetY = 0;
    public static boolean hotbarShowAmmo = true;

    public static boolean statusEnabled = true;
    public static double statusScale = 1.0;
    public static int statusOffsetX = 0;
    public static int statusOffsetY = 0;

    private static ModConfig config;

    private ClientConfig() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        config = event.getConfig();
        read();
    }

    private static void read() {
        radarEnabled = RADAR_ENABLED.get();
        radarScale = RADAR_SCALE.get();
        radarAnchor = RADAR_ANCHOR.get();
        radarOffsetX = RADAR_OFFSET_X.get();
        radarOffsetY = RADAR_OFFSET_Y.get();
        radarSnapAboveHotbar = RADAR_SNAP_ABOVE_HOTBAR.get();
        radarTerrain = RADAR_TERRAIN.get();

        hotbarEnabled = HOTBAR_ENABLED.get();
        hotbarScale = HOTBAR_SCALE.get();
        hotbarOffsetX = HOTBAR_OFFSET_X.get();
        hotbarOffsetY = HOTBAR_OFFSET_Y.get();
        hotbarShowAmmo = HOTBAR_SHOW_AMMO.get();

        statusEnabled = STATUS_ENABLED.get();
        statusScale = STATUS_SCALE.get();
        statusOffsetX = STATUS_OFFSET_X.get();
        statusOffsetY = STATUS_OFFSET_Y.get();
    }

    /** 设置界面用：改完立刻热更新（内存生效），写盘由 {@link #save()} 在关闭界面时做。 */
    public static void apply(boolean radarOn, double scale, String anchor, int offX, int offY) {
        radarEnabled = radarOn;
        radarScale = scale;
        radarAnchor = anchor;
        radarOffsetX = offX;
        radarOffsetY = offY;

        if (config == null) return;
        try {
            RADAR_ENABLED.set(radarOn);
            RADAR_SCALE.set(scale);
            RADAR_ANCHOR.set(anchor);
            RADAR_OFFSET_X.set(offX);
            RADAR_OFFSET_Y.set(offY);
        } catch (RuntimeException ignored) {
            // 内存值已经生效，写盘失败不影响本局显示
        }
    }

    public static void save() {
        if (config == null) return;
        try {
            RADAR_ENABLED.set(radarEnabled);
            RADAR_SCALE.set(radarScale);
            RADAR_ANCHOR.set(radarAnchor);
            RADAR_OFFSET_X.set(radarOffsetX);
            RADAR_OFFSET_Y.set(radarOffsetY);
            RADAR_SNAP_ABOVE_HOTBAR.set(radarSnapAboveHotbar);
            RADAR_TERRAIN.set(radarTerrain);

            HOTBAR_ENABLED.set(hotbarEnabled);
            HOTBAR_SCALE.set(hotbarScale);
            HOTBAR_OFFSET_X.set(hotbarOffsetX);
            HOTBAR_OFFSET_Y.set(hotbarOffsetY);
            HOTBAR_SHOW_AMMO.set(hotbarShowAmmo);

            STATUS_ENABLED.set(statusEnabled);
            STATUS_SCALE.set(statusScale);
            STATUS_OFFSET_X.set(statusOffsetX);
            STATUS_OFFSET_Y.set(statusOffsetY);
            config.save();
        } catch (RuntimeException ignored) {
        }
    }

    /** 设置界面用：物品栏分区热更新。 */
    public static void applyHotbar(boolean on, double scale, int offX, int offY, boolean showAmmo) {
        hotbarEnabled = on;
        hotbarScale = scale;
        hotbarOffsetX = offX;
        hotbarOffsetY = offY;
        hotbarShowAmmo = showAmmo;
        if (config == null) return;
        try {
            HOTBAR_ENABLED.set(on);
            HOTBAR_SCALE.set(scale);
            HOTBAR_OFFSET_X.set(offX);
            HOTBAR_OFFSET_Y.set(offY);
            HOTBAR_SHOW_AMMO.set(showAmmo);
        } catch (RuntimeException ignored) {
        }
    }

    /** 设置界面用：状态条分区热更新。 */
    public static void applyStatus(boolean on, double scale, int offX, int offY) {
        statusEnabled = on;
        statusScale = scale;
        statusOffsetX = offX;
        statusOffsetY = offY;
        if (config == null) return;
        try {
            STATUS_ENABLED.set(on);
            STATUS_SCALE.set(scale);
            STATUS_OFFSET_X.set(offX);
            STATUS_OFFSET_Y.set(offY);
        } catch (RuntimeException ignored) {
        }
    }

    /** 恢复默认值（设置界面的「恢复默认」）。 */
    public static void defaults() {
        radarEnabled = true;
        radarScale = 1.0;
        radarAnchor = "BOTTOM_RIGHT";
        radarOffsetX = 0;
        radarOffsetY = -78;
        radarSnapAboveHotbar = true;
        radarTerrain = true;
    }
}
