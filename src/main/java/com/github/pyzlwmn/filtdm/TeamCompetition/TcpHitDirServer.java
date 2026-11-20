package com.github.pyzlwmn.filtdm.TeamCompetition;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpHitDirS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import net.ptcrys.fpsmatch.common.event.FPSMGunDamageEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ★ v1.1.1：受击方向的「服务端发送器」（独立出来，避免和别的监听器抢事件）。
 *
 * <p>两条来源：</p>
 * <ol>
 *   <li>{@link FPSMGunDamageEvent} —— <b>枪械命中的主通道</b>。FPSMatch 在这里明确给了
 *       {@code attacker} / {@code hurtEntity}，TaCZ 的 DamageSource 里是空的也不影响。</li>
 *   <li>{@link LivingHurtEvent} —— 兜底通道（爆炸/近战/其它伤害），
 *       且用 {@code receiveCanceled = true} + LOWEST 优先级，保证上游 cancel 了也能收到。</li>
 * </ol>
 *
 * <p>两个通道用 120ms 去重，同一发子弹只会推一次。</p>
 */
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TcpHitDirServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-HITDIR");
    private static final long DEDUPE_NANOS = 120_000_000L;   // 120ms

    /** 临时诊断开关：主人测通后我删掉日志。 */
    private static final boolean DEBUG = true;

    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();

    private TcpHitDirServer() {
    }

    // ---------------------------------------------------------------- 枪械主通道

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunDamage(FPSMGunDamageEvent event) {
        if (!(event.getHurtEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getAttacker() instanceof ServerPlayer shooter)) return;
        if (shooter == victim) return;

        TeamCompetitionMap map = TeamCompetitionCombatListener.mapOf(victim);
        boolean start = map != null && map.isStart();
        boolean protectedNow = map != null && map.isProtected(victim.getUUID());
        if (DEBUG) {
            LOGGER.info("[FIL-HITDIR] gun hit victim={} shooter={} amt={} start={} protected={}",
                    victim.getName().getString(), shooter.getName().getString(),
                    event.getBaseAmount(), start, protectedNow);
        }
        if (map == null || !start || protectedNow) return;

        push(victim, shooter, event.getBaseAmount());
    }

    // ---------------------------------------------------------------- 兜底通道

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (victim.level().isClientSide()) return;

        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (DEBUG) {
            LOGGER.info("[FIL-HITDIR] hurt victim={} amt={} src={} direct={} entity={} canceled={}",
                    victim.getName().getString(), event.getAmount(),
                    event.getSource().getMsgId(),
                    direct == null ? "null" : direct.getClass().getSimpleName(),
                    attacker == null ? "null" : attacker.getClass().getSimpleName(),
                    event.isCanceled());
        }

        if (attacker == null || attacker == victim) attacker = direct;
        if (attacker instanceof Projectile projectile && projectile.getOwner() != null) {
            attacker = projectile.getOwner();
        }
        if (!(attacker instanceof ServerPlayer shooter) || shooter == victim) return;

        TeamCompetitionMap map = TeamCompetitionCombatListener.mapOf(victim);
        if (map == null || !map.isStart() || map.isProtected(victim.getUUID())) return;

        push(victim, shooter, event.getAmount());
    }

    // ---------------------------------------------------------------- 公共

    /**
     * 算「受害者 → 开火者」的世界方位角并发包。
     *
     * <p>与 MC 一致：0° = +Z（南），90° = -X（西）。
     * 客户端只需 {@code bearing - playerYaw} 就能得到相对角。</p>
     */
    private static void push(ServerPlayer victim, Entity shooter, float amount) {
        long now = System.nanoTime();
        Long last = LAST_SENT.get(victim.getUUID());
        if (last != null && now - last < DEDUPE_NANOS) {
            if (DEBUG) LOGGER.info("[FIL-HITDIR] dedupe skip");
            return;
        }

        double dx = shooter.getX() - victim.getX();
        double dz = shooter.getZ() - victim.getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;

        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        LAST_SENT.put(victim.getUUID(), now);
        if (LAST_SENT.size() > 64) {
            LAST_SENT.clear();   // 简单防泄漏
        }

        if (DEBUG) {
            LOGGER.info("[FIL-HITDIR] SEND victim={} bearing={} dx={} dz={} amt={}",
                    victim.getName().getString(), String.format("%.1f", bearing),
                    String.format("%.2f", dx), String.format("%.2f", dz), amount);
        }

        try {
            TcpNetwork.sendToPlayer(victim, new TcpHitDirS2CPacket(bearing, amount));
        } catch (RuntimeException e) {
            LOGGER.warn("[FIL-HITDIR] 发包失败：{}", e.toString());
        }
    }
}
