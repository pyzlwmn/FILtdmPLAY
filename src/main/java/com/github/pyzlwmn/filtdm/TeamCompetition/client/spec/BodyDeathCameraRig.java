package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.ptcrys.fpsmatch.common.client.camera.CameraFrame;
import net.ptcrys.fpsmatch.common.client.camera.CameraPose;
import net.ptcrys.fpsmatch.common.client.camera.CameraRig;

/**
 * 死亡镜头 rig（v73 重写，自研，弃用 BO 的 DeathCameraRig）。
 *
 * <p>行为：
 * <ul>
 *   <li>起点 = 死亡瞬间的眼位（服务端下发）</li>
 *   <li>目标 = 尸体位置（倒地后大约在脚底 + 0.3），全程 lookAt</li>
 *   <li>2.5 秒内向上浮约 1 格（服务端可下发时长）</li>
 *   <li>死亡瞬间有 5 tick 轻微抖动，之后完全稳定</li>
 * </ul>
 *
 * <p>关键点：不再像旧版那样「原地下沉 0.85 格抬头看天」，而是<b>上升 + 俯视尸体</b>，
 * 让玩家死的一瞬间能看到自己的尸体躺在那里。
 */
public record BodyDeathCameraRig(Vec3 origin, Vec3 corpse, float startYaw, float startPitch,
                                 double riseY) implements CameraRig {

    /** 抖动强度衰减用 */
    private static final float SHAKE_YAW = 1.1F;
    private static final float SHAKE_PITCH = 0.9F;
    private static final float SHAKE_POS = 0.035F;

    @Override
    public CameraFrame sample(double ticks) {
        DeathCameraTimeline tl = TIMELINE;
        float elapsed = (float) ticks;

        // 上浮进度（smoothstep，2 tick 后开始）
        float rise = riseProgress(elapsed, tl);
        // 受击抖动强度
        float shake = shakeProgress(elapsed);

        // 位置：起点 + 向上浮 riseY * rise
        double px = origin.x;
        double py = origin.y + riseY * rise;
        double pz = origin.z;

        // 抖动只作用在位置微扰
        px += Math.sin(elapsed * 31) * SHAKE_POS * shake;
        py += Math.cos(elapsed * 37) * SHAKE_POS * 0.8 * shake;
        pz += Math.sin(elapsed * 27 + 0.7) * SHAKE_POS * shake;

        // 朝向：从起点朝向平滑过渡到「看向尸体」
        float[] look = lookAt(px, py, pz, corpse.x, corpse.y, corpse.z);

        // 前 3 tick 保留玩家原本朝向（不要一死就猛甩），再 lerp 到 lookAt
        float blend = Mth.clamp((elapsed - 1.0F) / 8.0F, 0.0F, 1.0F);
        blend = blend * blend * (3.0F - 2.0F * blend);

        float yaw = lerpAngle(startYaw, look[0], blend);
        float pitch = Mth.lerp(blend, startPitch, look[1]);

        // 抖动叠加角度微扰
        yaw += (float) Math.sin(elapsed * 22) * SHAKE_YAW * shake;
        pitch += (float) Math.cos(elapsed * 25) * SHAKE_PITCH * shake;

        // pitch 夹紧，避免翻过头
        pitch = Mth.clamp(pitch, -89.5F, 89.5F);

        // 死透后轻微侧倾，增强「倒地」的观感；抖动期不做
        float roll = 0.0F;
        if (elapsed > 8) {
            float t = Mth.clamp((elapsed - 8.0F) / 14.0F, 0.0F, 1.0F);
            roll = -5.5F * (t * t * (3.0F - 2.0F * t));
        }
        roll += (float) Math.sin(elapsed * 29) * 1.4F * shake;

        return CameraFrame.independent(new CameraPose(
                new Vec3(px, py, pz), yaw, pitch, roll, Double.NaN));
    }

    /** 供 TcpDeathCameraClient 注入当前时序（rig 是 record，无法持有可变状态） */
    private static DeathCameraTimeline TIMELINE = new DeathCameraTimeline();

    public static void bindTimeline(DeathCameraTimeline timeline) {
        TIMELINE = timeline == null ? new DeathCameraTimeline() : timeline;
    }

    private static float riseProgress(float elapsed, DeathCameraTimeline tl) {
        float denom = Math.max(1, tl.riseEndTicks() - DeathCameraTimeline.RISE_START_TICKS);
        float t = Mth.clamp((elapsed - DeathCameraTimeline.RISE_START_TICKS) / denom, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float shakeProgress(float elapsed) {
        float t = Mth.clamp(elapsed / DeathCameraTimeline.HIT_TICKS, 0.0F, 1.0F);
        return 1.0F - (t * t * (3.0F - 2.0F * t));
    }

    /** 计算从 (x,y,z) 看向目标的 yaw/pitch（MC 角度制：yaw 0=南/-Z，pitch 正=向下） */
    private static float[] lookAt(double x, double y, double z,
                                 double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        return new float[]{yaw, pitch};
    }

    /** 角度插值，走最短路径（避免 359°→1° 转一大圈） */
    private static float lerpAngle(float a, float b, float t) {
        float diff = Mth.wrapDegrees(b - a);
        return a + diff * t;
    }
}
