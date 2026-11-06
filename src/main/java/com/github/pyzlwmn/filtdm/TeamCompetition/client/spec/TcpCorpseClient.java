package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpCorpseS2CPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端尸体数据仓库（v73）。
 *
 * <p>只存数据；渲染载体由 {@link CorpseRenderer} 持有（客户端假实体）。
 * 尸体完全虚拟：无碰撞、无交互、不可拾取、不可伤害。
 */
public final class TcpCorpseClient {

    /** 单具尸体的快照 */
    public record Corpse(UUID owner, String name, Vec3 pos, float yaw, float pitch,
                         int team, ItemStack gun, long spawnedAtMs) {
    }

    private static final Map<UUID, Corpse> CORPSES = new ConcurrentHashMap<>();

    private TcpCorpseClient() {
    }

    public static void accept(TcpCorpseS2CPacket msg) {
        if (msg.action() == TcpCorpseS2CPacket.ACTION_SPAWN) {
            Corpse c = new Corpse(
                    msg.owner(), msg.name(),
                    new Vec3(msg.x(), msg.y(), msg.z()),
                    msg.yaw(), msg.pitch(), msg.team(), msg.gun(),
                    System.currentTimeMillis());
            CORPSES.put(msg.owner(), c);
            CorpseRenderer.onSpawn(msg);
        } else {
            CorpseRenderer.onRemove(msg.owner());
            CORPSES.remove(msg.owner());
        }
    }

    public static Corpse get(UUID owner) {
        return CORPSES.get(owner);
    }

    public static void remove(UUID owner) {
        CORPSES.remove(owner);
    }

    public static Map<UUID, Corpse> all() {
        return CORPSES;
    }

    public static void clear() {
        CORPSES.clear();
        CorpseRenderer.clearAll();
    }

    /** 断线/换维度时清理，避免残留 */
    public static void clearIfLevelChanged() {
        if (net.minecraft.client.Minecraft.getInstance().level == null) {
            clear();
        }
    }
}
