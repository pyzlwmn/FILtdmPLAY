package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpDeathCameraS2CPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.ptcrys.fpsmatch.common.camera.CameraEndReason;
import net.ptcrys.fpsmatch.common.client.camera.CameraDirector;
import net.ptcrys.fpsmatch.common.client.camera.CameraLifetime;
import net.ptcrys.fpsmatch.common.client.camera.CameraPolicy;
import net.ptcrys.fpsmatch.common.client.camera.CameraSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 死亡镜头 + 死亡 UI 数据中枢（v73）。
 *
 * <p>★ 关键修复（2026-11-06 晚，动画不生效）：
 * <p>{@code CameraDirector.prepareFrame()} 每个渲染帧都会跑：
 * 有 session 才 {@code frame = rig.sample(...)}，<b>没有 session 就走
 * {@code baseFrame()}，而 baseFrame() 在「不是旁观者」时直接返回 null → frame 被清空</b>。
 * 所以只要开镜那一瞬间客户端还是 ADVENTURE，镜头就立刻被打回原样，动画完全看不见。
 *
 * <p>修法：把「收包」和「开镜」解耦。
 * <ol>
 *   <li>收到 START：只登记数据 + 挂 PENDING 标记，不当场 play</li>
 *   <li>{@link #tick()} 里检测到 {@code player.isSpectator()} 之后才真正 play</li>
 *   <li>开镜成功后立刻用第一次采样把 rig 绑定到 CameraBackend，避免空帧</li>
 * </ol>
 */
public final class TcpDeathCameraClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-TDM-DeathCam");

    private static CameraSession session;
    private static final DeathCameraTimeline TIMELINE = new DeathCameraTimeline();

    // ---- 待开镜数据（START 先登记，spectator 生效后由 tick 真正开镜） ----
    private static boolean pending;
    private static Vec3 pendingOrigin = Vec3.ZERO;
    private static Vec3 pendingCorpse = Vec3.ZERO;
    private static float pendingYaw;
    private static float pendingPitch;
    private static int pendingDuration = DeathCameraTimeline.DEFAULT_TOTAL_TICKS;

    /** 待开镜的最长等待 tick（服务端 5s 复活 → 最多等 40 tick 也该切好了） */
    private static final int PENDING_MAX_TICKS = 40;
    private static int pendingTicks;

    // ---- 死亡 UI 数据（由 START 包填充，stop 时清空） ----
    private static DeathUiRenderer.KillInfo lastKill;
    private static int scoreBlue;
    private static int scoreRed;
    private static int scoreTarget = 25;

    private TcpDeathCameraClient() {
    }

    public static void accept(TcpDeathCameraS2CPacket msg) {
        LOGGER.info("[TDM·死亡相机] 收到包 action={}", msg.action());
        if (msg.action() == TcpDeathCameraS2CPacket.ACTION_START) {
            start(msg);
        } else {
            stop();
        }
    }

    private static void start(TcpDeathCameraS2CPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        stop();

        pendingOrigin = new Vec3(msg.x(), msg.y(), msg.z());
        pendingCorpse = msg.hasCorpse()
                ? new Vec3(msg.corpseX(), msg.corpseY(), msg.corpseZ())
                : pendingOrigin.add(0, -3.0, 0);
        pendingYaw = msg.yaw();
        pendingPitch = msg.pitch();
        pendingDuration = msg.durationTicks() > 0
                ? msg.durationTicks()
                : DeathCameraTimeline.DEFAULT_TOTAL_TICKS;
        pending = true;
        pendingTicks = 0;

        ItemStack gun = msg.hasGun() ? msg.gun() : ItemStack.EMPTY;
        lastKill = new DeathUiRenderer.KillInfo(
                msg.killer().isEmpty() ? "未知击杀者" : msg.killer(),
                msg.killerTeam() == 0,
                gun,
                msg.ammoText(),
                msg.headshot());
        scoreBlue = msg.scoreBlue();
        scoreRed = msg.scoreRed();
        scoreTarget = msg.scoreTarget() > 0 ? msg.scoreTarget() : 25;

        LOGGER.info("[TDM·死亡相机] START 登记 origin={},{},{} corpse={},{},{} 时长={}t spectator={}",
                String.format("%.2f", pendingOrigin.x), String.format("%.2f", pendingOrigin.y),
                String.format("%.2f", pendingOrigin.z),
                String.format("%.2f", pendingCorpse.x), String.format("%.2f", pendingCorpse.y),
                String.format("%.2f", pendingCorpse.z),
                pendingDuration, mc.player.isSpectator());

        // 服务端已先 setGameMode 的正常路径：当场就能开镜
        tryStartIfReady();
    }

    /** tick() 与收包后都会调；只有「已切旁观模式」才真正开镜 */
    private static void tryStartIfReady() {
        if (!pending || session != null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            pending = false;
            return;
        }

        if (++pendingTicks > PENDING_MAX_TICKS) {
            LOGGER.warn("[TDM·死亡相机] 等待旁观模式超时（{} tick，spectator={}）→ 放弃开镜",
                    pendingTicks, mc.player.isSpectator());
            pending = false;
            return;
        }

        if (!mc.player.isSpectator()) {
            LOGGER.debug("[TDM·死亡相机] 尚未进入旁观模式，等下一 tick（已等 {}）", pendingTicks);
            return;
        }

        TIMELINE.start(pendingDuration);
        BodyDeathCameraRig.bindTimeline(TIMELINE);
        BodyDeathCameraRig rig = new BodyDeathCameraRig(
                pendingOrigin, pendingCorpse, pendingYaw, pendingPitch, 1.0D);

        session = CameraDirector.play(
                "filtdm_body_death_camera",
                CameraDirector.DEATH_PRIORITY,
                rig,
                CameraPolicy.DEATH,
                TIMELINE.clock(),
                TcpDeathCameraClient::stillValid,
                CameraLifetime.SCENE,
                reason -> session = null);

        pending = false;

        if (session == null) {
            LOGGER.warn("[TDM·死亡相机] play() 返回 null（有更高优先级相机占用？）");
            TIMELINE.reset();
        } else {
            // 立刻把 rig 采样一帧绑到 CameraBackend，避免首帧空窗被 refresh 清掉
            CameraDirector.refresh();
            LOGGER.info("[TDM·死亡相机] 已开镜：origin={},{},{} → corpse={},{},{} 上浮 1.0 时长 {}t 击杀者={}",
                    String.format("%.2f", pendingOrigin.x), String.format("%.2f", pendingOrigin.y),
                    String.format("%.2f", pendingOrigin.z),
                    String.format("%.2f", pendingCorpse.x), String.format("%.2f", pendingCorpse.y),
                    String.format("%.2f", pendingCorpse.z),
                    TIMELINE.totalTicks(),
                    lastKill == null ? "-" : lastKill.killer());
        }
    }

    public static void stop() {
        pending = false;
        pendingTicks = 0;
        if (session != null) {
            try {
                session.stop(CameraEndReason.CANCELLED);
            } catch (RuntimeException ignored) {
            }
            session = null;
        }
        TIMELINE.reset();
        lastKill = null;
    }

    private static boolean stillValid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        return !TIMELINE.finished();
    }

    public static void tick() {
        if (pending && session == null) {
            tryStartIfReady();
        }
        if (session != null) {
            TIMELINE.tick();
        }
    }

    /** UI 用：待开镜也算「死亡中」，否则倒计时会在开镜前闪一下消失 */
    public static boolean active() {
        return session != null || pending;
    }

    public static DeathUiRenderer.KillInfo lastKill() {
        return lastKill;
    }

    public static int scoreBlue() {
        return scoreBlue;
    }

    public static int scoreRed() {
        return scoreRed;
    }

    public static int scoreTarget() {
        return scoreTarget;
    }

    /** 已过秒数（UI 动画时序用） */
    public static float elapsedSeconds(float partialTick) {
        if (!active()) return 0.0F;
        if (session == null) return 0.0F;
        return Math.max(0.0F, TIMELINE.elapsed(partialTick) / 20.0F);
    }

    /** 剩余秒数（复活倒计时） */
    public static float remainingSeconds(float partialTick) {
        if (!active()) return 0.0F;
        if (session == null) return pendingDuration / 20.0F;
        float left = TIMELINE.totalTicks() - TIMELINE.elapsed(partialTick);
        return Math.max(0.0F, left / 20.0F);
    }

    /** 0 → 1 的进度（进度条） */
    public static float progress(float partialTick) {
        if (!active()) return 0.0F;
        if (session == null) return 0.0F;
        float total = Math.max(1, TIMELINE.totalTicks());
        return Math.min(1.0F, TIMELINE.elapsed(partialTick) / total);
    }
}
