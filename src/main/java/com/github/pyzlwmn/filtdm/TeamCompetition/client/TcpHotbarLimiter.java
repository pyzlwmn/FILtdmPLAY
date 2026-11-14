package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v80：把快捷栏选择锁死在 1~5。
 *
 * <p>filtdm 只给 1~5 发装备（步枪/手枪/投掷物/道具/刀），6~9 永远是空的。
 * 数字键 6~9 和滚轮滑到空格子都会被这里立刻拉回：</p>
 * <ul>
 *   <li>超过 4 → 拉回 4（第 5 格）</li>
 *   <li>小于 0 → 拉回 0</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TcpHotbarLimiter {

    public static final int MAX_SLOT = 4;

    private TcpHotbarLimiter() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ClientConfig.hotbarEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        // ★ v88：小地图地形刷新（自带 8 tick 节流）
        RadarTerrain.tick(mc.level);

        Player player = mc.player;
        if (player == null || mc.screen != null) return;

        int sel = player.getInventory().selected;
        if (sel > MAX_SLOT) {
            player.getInventory().selected = MAX_SLOT;
        } else if (sel < 0) {
            player.getInventory().selected = 0;
        }
    }
}
