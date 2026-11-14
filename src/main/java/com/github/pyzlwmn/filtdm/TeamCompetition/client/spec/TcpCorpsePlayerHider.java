package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v83：尸体存在期间，把「死亡玩家本体」藏掉。
 *
 * <p>症状：玩家死后立刻切旁观，可以自由飞行；别人眼里那个活的玩家模型
 * 会飘来飘去（就是「实体跟着人跑」），而地上的真尸体反而看不见。
 * 这里在尸体存续期间取消原版玩家渲染，只留地上的尸体 + 枪。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TcpCorpsePlayerHider {

    private TcpCorpsePlayerHider() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity() == null) return;
        if (CorpseRenderer.has(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }
}
