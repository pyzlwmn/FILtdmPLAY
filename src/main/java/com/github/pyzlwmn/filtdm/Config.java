package com.github.pyzlwmn.filtdm;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = FilTdm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    // ★ v80 战术地图（先做在 filtdm 内，后续可整体抽出为独立 mod）
    private static final ForgeConfigSpec.BooleanValue TACTICAL_MAP_ENABLED = BUILDER
            .comment("战术地图开关：队友常驻显示，敌人仅在被看见时同步")
            .define("tacticalMap.enabled", true);

    private static final ForgeConfigSpec.IntValue TACTICAL_MAP_INTERVAL = BUILDER
            .comment("战术地图同步间隔（tick）：5 = 每秒 4 次")
            .defineInRange("tacticalMap.intervalTicks", 5, 1, 40);

    private static final ForgeConfigSpec.DoubleValue TACTICAL_MAP_RANGE = BUILDER
            .comment("战术地图雷达半径（格），同时也是敌人可见距离上限")
            .defineInRange("tacticalMap.range", 48.0, 8.0, 512.0);

    private static final ForgeConfigSpec.DoubleValue TACTICAL_MAP_FOV = BUILDER
            .comment("敌人可见视锥总角度（度）：110 = 左右各 55 度")
            .defineInRange("tacticalMap.fovDegrees", 110.0, 30.0, 360.0);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    public static boolean tacticalMapEnabled = true;
    public static int tacticalMapIntervalTicks = 5;
    public static double tacticalMapRange = 48.0;
    public static double tacticalMapFovDegrees = 110.0;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 2026-11-14 修崩溃：本方法挂在所有 ModConfigEvent 上，
        // CLIENT 配置先加载时会误读 COMMON 的值 → dev 环境直接抛
        // "Cannot get config value before config is loaded"。只处理自己的 SPEC。
        if (event.getConfig().getSpec() != SPEC) return;

        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        items = ITEM_STRINGS.get().stream().map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName))).collect(Collectors.toSet());

        tacticalMapEnabled = TACTICAL_MAP_ENABLED.get();
        tacticalMapIntervalTicks = TACTICAL_MAP_INTERVAL.get();
        tacticalMapRange = TACTICAL_MAP_RANGE.get();
        tacticalMapFovDegrees = TACTICAL_MAP_FOV.get();
    }
}
