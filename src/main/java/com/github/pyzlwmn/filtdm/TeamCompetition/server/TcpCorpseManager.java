package com.github.pyzlwmn.filtdm.TeamCompetition.server;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpCorpseS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端尸体管理器（v73）。
 *
 * <p>职责：死亡时广播尸体生成包；复活 / 重置 / 对局结束时广播移除包。
 * <b>不生成任何真实实体</b> —— 尸体完全是客户端的渲染表现，服务端只发数据。
 *
 * <p>这样保证：尸体不可碰撞、不可交互、不可拾取、不可伤害，也不需要清理实体。
 */
public final class TcpCorpseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-TDM-Corpse");

    /** 记录当前在场的尸体（UUID → 生成时 tick），用于超时兜底清理 */
    private static final Map<UUID, Integer> LIVE = new ConcurrentHashMap<>();

    /** 尸体兜底存活上限（tick），防止移除包丢失导致尸体永久残留 */
    private static final int MAX_LIFE_TICKS = 20 * 30;

    private TcpCorpseManager() {
    }

    /**
     * 广播生成尸体。
     *
     * @param level 目标维度
     * @param player 死者
     * @param gun 死时手持枪（可为 EMPTY）
     * @param team 阵营编号（0 红 / 1 蓝 / 2 无）
     * @param corpseY 尸体落点 Y（脚底）
     */
    public static void spawn(ServerLevel level, ServerPlayer player, ItemStack gun, int team, double corpseY) {
        if (level == null || player == null) return;
        Vec3 p = player.position();
        UUID id = player.getUUID();

        LIVE.put(id, level.getServer().getTickCount());

        TcpCorpseS2CPacket msg = TcpCorpseS2CPacket.spawn(
                id, player.getName().getString(),
                p.x, corpseY, p.z,
                player.getYRot(), player.getXRot(),
                team, gun);

        TcpNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.NEAR.with(
                        () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(
                                p.x, p.y, p.z, 128.0D, level.dimension())),
                msg);

        LOGGER.info("[TDM·尸体] 生成 {} @ {},{},{} team={} 枪={}",
                player.getName().getString(),
                String.format("%.2f", p.x), String.format("%.2f", corpseY), String.format("%.2f", p.z),
                team, gun.isEmpty() ? "空手" : gun.getDescriptionId());
    }

    /** 广播移除该玩家的尸体（复活 / 结束 / 重置时调用） */
    public static void remove(ServerPlayer player) {
        if (player == null) return;
        removeById(player.getUUID(), player.serverLevel());
    }

    public static void removeById(UUID id, ServerLevel level) {
        if (id == null) return;
        if (LIVE.remove(id) == null) return;
        if (level == null) return;

        TcpNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension),
                TcpCorpseS2CPacket.remove(id));

        LOGGER.info("[TDM·尸体] 移除 {}", id);
    }

    /** 清空所有尸体（对局结束 / 重置 / 换图） */
    public static void clearAll(ServerLevel level) {
        if (LIVE.isEmpty()) return;
        for (UUID id : LIVE.keySet()) {
            if (level != null) {
                TcpNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension),
                        TcpCorpseS2CPacket.remove(id));
            }
        }
        LOGGER.info("[TDM·尸体] 清空全部（{} 具）", LIVE.size());
        LIVE.clear();
    }

    /** 每 tick 兜底清理（移除包丢失时不残留） */
    public static void tick(ServerLevel level) {
        if (LIVE.isEmpty() || level == null) return;
        int now = level.getServer().getTickCount();
        for (Map.Entry<UUID, Integer> e : LIVE.entrySet()) {
            if (now - e.getValue() > MAX_LIFE_TICKS) {
                removeById(e.getKey(), level);
            }
        }
    }
}
