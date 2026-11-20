package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ★ v1.1.0：受击方向的客户端缓存。
 *
 * <p>只保存「最后一次命中」的世界方位角 + 命中时刻；不保存开火者实体，
 * 所以开火者之后怎么跑都不会影响渲染（需求：只认开火时的位置）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TcpHitDirData {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-HITDIR-C");

    private static float bearing = 0f;
    private static float damage = 0f;
    private static long hitAtMs = 0L;
    private static int seq = 0;

    private TcpHitDirData() {
    }

    /** 收到服务端的受击方向包。连续命中 = 刷新计时 + 把三角推向新方向。 */
    public static void push(float bearingDeg, float dmg) {
        bearing = bearingDeg;
        damage = dmg;
        hitAtMs = System.currentTimeMillis();
        seq++;
        LOGGER.info("[FIL-HITDIR-C] recv bearing={} amt={}", String.format("%.1f", bearingDeg), dmg);
    }

    /** 清空（离场 / 对局结束 / 切旁观时调用）。 */
    public static void clear() {
        hitAtMs = 0L;
        seq++;
    }

    public static float bearing() {
        return bearing;
    }

    public static float damage() {
        return damage;
    }

    public static long hitAtMs() {
        return hitAtMs;
    }

    public static boolean active() {
        return hitAtMs > 0L;
    }
}
