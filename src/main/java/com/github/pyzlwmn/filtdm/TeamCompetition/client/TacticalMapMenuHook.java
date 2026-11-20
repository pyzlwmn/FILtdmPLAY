package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v80：把「战术地图设置」入口放进 Esc 暂停菜单。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TacticalMapMenuHook {

    private TacticalMapMenuHook() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) return;

        int w = 200;
        // v85：按主人要求改回原位（暂停菜单按钮组下面居中）
        int x = pause.width / 2 - w / 2;
        int y = pause.height / 4 + 168;

        event.addListener(Button.builder(Component.literal("FIL对战设置"), b -> {
                    // v85：换用 ModernUI 平滑界面
                    MuiSettingsFragment.previousScreen = pause;
                    icyllis.modernui.mc.MuiModApi.openScreen(new MuiSettingsFragment());
                })
                .bounds(x, y, w, 20)
                .build());

        // ★ v1.2.0：HUD 布局编辑器（拖动 / 大小 / 透明度 全部可视化调）
        event.addListener(Button.builder(Component.literal("HUD 界面布局"), b -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(new HudLayoutScreen(pause)));
                })
                .bounds(x, y + 24, w, 20)
                .build());
    }
}
