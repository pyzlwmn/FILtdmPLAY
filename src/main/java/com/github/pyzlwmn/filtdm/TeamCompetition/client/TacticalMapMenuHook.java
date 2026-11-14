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
    }
}
