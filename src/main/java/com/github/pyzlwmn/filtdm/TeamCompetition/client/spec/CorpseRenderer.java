package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpCorpseS2CPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 尸体渲染器（v73，A 方案：真尸体）。
 *
 * <p>做法：<b>服务端不生成实体</b>，客户端为每具尸体在 {@link ClientLevel} 里建一个
 * {@link RemotePlayer} 假实体（只存在于客户端，不参与碰撞、不进服务端世界），
 * 摆成躺倒姿势后用标准实体渲染管线绘制 → 皮肤、手脚、模型全部自动正确。
 *
 * <p>保证：
 * <ul>
 *   <li>不可碰撞（noPhysics = true）</li>
 *   <li>不可伤害、不可交互（客户端实体，服务端根本不知道它存在）</li>
 *   <li>不可拾取（枪用物品模型单独渲染，不是掉落物实体）</li>
 *   <li>玩家复活 → 服务端发 REMOVE → 客户端立刻清除</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class CorpseRenderer {

    private static final Map<UUID, Dummy> DUMMIES = new ConcurrentHashMap<>();

    /** 尸体贴地偏移（让模型躺在地上而不是陷进方块） */
    private static final double LIE_Y = 0.06D;

    private CorpseRenderer() {
    }

    /**
     * 假尸体实体：RemotePlayer 子类，屏蔽一切交互。
     *
     * <p>注意：只覆写<b>确定存在于 1.20.1 MCP 映射</b>的方法。
     * RemotePlayer 本身已自带 {@code isPushable=false / isSpectator=false / tick 行为}，
     * 这里只补「不可碰撞 / 不可攻击 / 不可伤害 / 不可选中」。
     */
    private static final class Dummy extends RemotePlayer {
        Dummy(ClientLevel level, com.mojang.authlib.GameProfile profile) {
            super(level, profile);
            // 客户端假实体：无重力、无物理、不参与碰撞
            this.setNoGravity(true);
            this.noPhysics = true;
        }

        @Override
        public boolean isPickable() {
            return false;   // 准星选不中
        }

        @Override
        public boolean isAttackable() {
            return false;   // 打不了
        }

        @Override
        public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
            return true;    // 免伤
        }

        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            return false;   // 任何伤害事件直接拒绝（同时挡住 hurt 触发的动画/音效）
        }

        @Override
        public void tick() {
            // 完全不 tick：不走 RemotePlayer 的插值/动画状态机，节约性能也避免尸体乱动
        }
    }

    /** 收到 SPAWN 包：建立假实体 */
    public static void onSpawn(TcpCorpseS2CPacket msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
                msg.owner(), msg.name() == null || msg.name().isEmpty() ? "Corpse" : msg.name());
        Dummy d = new Dummy(level, profile);
        d.setPos(msg.x(), msg.y(), msg.z());
        d.setYRot(msg.yaw());
        d.setXRot(0.0F);
        d.setYHeadRot(msg.yaw());
        d.yBodyRot = msg.yaw();
        d.yHeadRot = msg.yaw();
        d.setPose(Pose.SLEEPING);
        d.setNoGravity(true);
        d.noPhysics = true;
        d.setInvisible(false);
        d.setSprinting(false);
        d.setDeltaMovement(Vec3.ZERO);
        d.tickCount = 0;

        DUMMIES.put(msg.owner(), d);
    }

    /** 收到 REMOVE 包：清除 */
    public static void onRemove(UUID owner) {
        DUMMIES.remove(owner);
    }

    public static void clearAll() {
        DUMMIES.clear();
    }

    /** 取假实体（供渲染与相机 lookAt 用） */
    public static Entity dummy(UUID owner) {
        return DUMMIES.get(owner);
    }

    public static boolean has(UUID owner) {
        return DUMMIES.containsKey(owner);
    }

    /**
     * 在世界渲染阶段绘制所有尸体。
     *
     * @param poseStack 世界 PoseStack
     * @param buffers   buffer source
     * @param partialTick 插值
     */
    public static void renderAll(PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
        if (DUMMIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        for (Map.Entry<UUID, Dummy> entry : DUMMIES.entrySet()) {
            Dummy d = entry.getValue();
            TcpCorpseClient.Corpse info = TcpCorpseClient.get(entry.getKey());

            poseStack.pushPose();
            // 相对相机的位置（dispatcher.render 内部还会再 translate 一次 x/y/z，故这里传相对量）
            double rx = d.getX() - cam.x;
            double ry = d.getY() - cam.y + LIE_Y;
            double rz = d.getZ() - cam.z;

            // 躺倒旋转：先绕 Y 按死亡朝向，再绕 Z 90° 让身体平躺，最后轻微歪斜
            poseStack.mulPose(new Quaternionf().rotateY(
                    (float) Math.toRadians(-d.getYRot() + 180.0F)));
            poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(90.0F)));
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(5.0F)));

            try {
                dispatcher.render(d, rx, ry, rz, d.getYRot(), partialTick,
                        poseStack, buffers, 0xF000F0);
            } catch (RuntimeException ignored) {
                // 渲染失败不刷屏
            }

            poseStack.popPose();

            if (info != null && !info.gun().isEmpty()) {
                renderGun(poseStack, buffers, info, info.gun(), cam);
            }
        }
    }

    private static void renderGun(PoseStack poseStack, MultiBufferSource buffers,
                                  TcpCorpseClient.Corpse info, ItemStack gun, Vec3 cam) {
        Vec3 p = info.pos();
        double yawRad = Math.toRadians(info.yaw());
        double offX = Math.cos(yawRad) * 0.45D;
        double offZ = Math.sin(yawRad) * 0.45D;

        poseStack.pushPose();
        poseStack.translate(p.x - cam.x + offX, p.y - cam.y + 0.06D, p.z - cam.z + offZ);
        poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-info.yaw())));
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(90.0F)));
        poseStack.scale(0.9F, 0.9F, 0.9F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                gun,
                net.minecraft.world.item.ItemDisplayContext.GROUND,
                0xF000F0,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack, buffers,
                info.owner().hashCode() & 0xFFFF);

        poseStack.popPose();
    }
}
