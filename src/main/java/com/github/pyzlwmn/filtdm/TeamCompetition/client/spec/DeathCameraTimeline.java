package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import net.ptcrys.fpsmatch.common.camera.SequenceClock;

/**
 * 死亡镜头时序（v73 重写）。
 *
 * <p>设计目标：死亡瞬间镜头<b>从原视角原地接管</b>，然后平滑上浮约 1 格并全程 lookAt 尸体，
 * 2.5 秒（50 tick）内完成。全程不淡全黑，只在最后做轻微收尾。
 *
 * <p>参数改由服务端下发（respawnDelaySeconds 驱动 TOTAL），避免以前写死 100 tick 和后端耦合脆弱。
 */
public final class DeathCameraTimeline {

    /** 受击抖动持续 tick 数（死亡瞬间的轻微晃动，必须短，否则像手雷震屏） */
    public static final int HIT_TICKS = 5;

    /** 上浮起始 tick */
    public static final int RISE_START_TICKS = 2;

    /** 上浮归属：默认 50 tick = 2.5 秒 */
    public static final int DEFAULT_RISE_END_TICKS = 50;

    /** 默认总时长（= 上浮结束，之后保持不动等复活） */
    public static final int DEFAULT_TOTAL_TICKS = 50;

    private final SequenceClock clock = new SequenceClock();
    private boolean active;

    /** 本次会话的上浮结束 tick（由服务端下发，兜底 DEFAULT_RISE_END_TICKS） */
    private int riseEndTicks = DEFAULT_RISE_END_TICKS;

    /** 本次会话的总时长 tick */
    private int totalTicks = DEFAULT_TOTAL_TICKS;

    public SequenceClock clock() {
        return clock;
    }

    public void start() {
        start(DEFAULT_TOTAL_TICKS);
    }

    /**
     * @param total 服务端下发的总时长（tick）。上浮在 total 内完成，之后保持静止直到 stop。
     */
    public void start(int total) {
        this.totalTicks = Math.max(10, total);
        this.riseEndTicks = this.totalTicks;
        clock.reset();
        active = true;
    }

    public void reset() {
        clock.reset();
        active = false;
        riseEndTicks = DEFAULT_RISE_END_TICKS;
        totalTicks = DEFAULT_TOTAL_TICKS;
    }

    public void tick() {
        if (active) clock.tick();
    }

    public boolean active() {
        return active;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public int riseEndTicks() {
        return riseEndTicks;
    }

    /** 上浮是否走完（走完后镜头稳定悬停，等复活包 stop） */
    public boolean riseFinished() {
        return active && clock.ticks() >= riseEndTicks;
    }

    public boolean finished() {
        return active && clock.ticks() >= totalTicks;
    }

    public float elapsed(float partialTick) {
        return active ? clock.ticks() + Math.max(0, Math.min(1, partialTick)) : 0.0F;
    }

    /** 受击抖动强度 1 → 0 */
    public float shakeProgress(float partialTick) {
        if (!active) return 0;
        float time = elapsed(partialTick);
        return time < HIT_TICKS ? 1.0F - smooth(time / HIT_TICKS) : 0.0F;
    }

    /** 上浮进度 0 → 1（smoothstep） */
    public float riseProgress(float partialTick) {
        if (!active) return 0;
        float time = elapsed(partialTick);
        float denom = Math.max(1, riseEndTicks - RISE_START_TICKS);
        return smooth((time - RISE_START_TICKS) / denom);
    }

    private static float smooth(float value) {
        value = Math.max(0.0F, Math.min(1.0F, value));
        return value * value * (3.0F - 2.0F * value);
    }
}
