package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.spec.CorpseRenderer;
import com.github.pyzlwmn.filtdm.TeamCompetition.client.spec.TcpCorpseClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端世界渲染事件（v73）。
 *
 * <p>尸体在 {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} 阶段绘制：
 * 此时实体已经画完、PoseStack 已归位到相机空间，适合叠加自定义渲染。
 */
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TcpWorldRenderer {

    private TcpWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            TcpCorpseClient.clearIfLevelChanged();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        CorpseRenderer.renderAll(poseStack, buffers, event.getPartialTick());
    }
}
