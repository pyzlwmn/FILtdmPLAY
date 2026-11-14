package com.github.pyzlwmn.filtdm.TeamCompetition.tactical;

import com.github.pyzlwmn.filtdm.Config;
import com.github.pyzlwmn.filtdm.TeamCompetition.TeamCompetitionMap;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.SkinRegistry;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpTacticalMapS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ptcrys.fpsmatch.core.team.MapTeams;
import net.ptcrys.fpsmatch.core.team.ServerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v80：战术地图服务端逻辑。
 *
 * <p>先做在 filtdm 里（主人要求「先写进 filtdm，后面再移出来」），
 * 所以本类只通过 {@link TeamCompetitionMap} 的公开接口取队伍/开局状态，
 * 不碰任何私有字段 —— 以后要拆成独立 mod，整个 tactical 包 + 一个包注册搬走即可。</p>
 *
 * <p>可见性判定（服务端权威）：
 * <ol>
 *   <li>距离 ≤ range</li>
 *   <li>在视锥内（viewer 朝向与目标方向夹角 ≤ fov/2）</li>
 *   <li>眼睛 → 目标胸口/头部 的方块射线不被挡</li>
 * </ol>
 * 队友永远可见（含死亡 ❌）；敌人看不见就不发包、客户端也就没数据可画。</p>
 */
public final class TacticalMapTracker {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-TacticalMap");

    private int timer = 0;
    private boolean warned = false;

    public void tick(TeamCompetitionMap map) {
        if (!Config.tacticalMapEnabled) return;
        if (map == null || !map.tacticalMapRunning()) {
            timer = 0;
            return;
        }
        int interval = Math.max(1, Config.tacticalMapIntervalTicks);
        if (++timer < interval) return;
        timer = 0;
        try {
            broadcast(map);
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                LOG.warn("[战术地图] 同步失败（后续静默）：{}", e.toString());
            }
        }
    }

    private void broadcast(TeamCompetitionMap map) {
        ServerLevel level = map.getServerLevel();
        if (level == null) return;

        MapTeams teams = map.getMapTeams();
        if (teams == null) return;

        float range = (float) Config.tacticalMapRange;
        double halfFovCos = Math.cos(Math.toRadians(Math.max(10.0, Config.tacticalMapFovDegrees) / 2.0));

        List<ServerPlayer> viewers = new ArrayList<>(teams.getOnlineWithSpec());
        if (viewers.isEmpty()) return;

        Map<UUID, String> skinCache = new HashMap<>();

        for (ServerPlayer viewer : viewers) {
            boolean spectator = viewer.isSpectator();
            ServerTeam ownTeam = teams.getTeamByPlayer(viewer).orElse(null);
            int ownCode = map.teamCode(ownTeam);

            List<TcpTacticalMapS2CPacket.Blip> out = new ArrayList<>();

            for (ServerTeam team : teams.getNormalTeams()) {
                int code = map.teamCode(team);
                boolean mate = ownTeam != null && team == ownTeam;

                for (ServerPlayer p : new ArrayList<>(team.getOnline())) {
                    if (p.getUUID().equals(viewer.getUUID())) continue;

                    boolean alive = p.isAlive() && !p.isSpectator();
                    boolean visible;
                    if (spectator || mate) {
                        visible = true;
                    } else {
                        visible = alive && canSee(level, viewer, p, range, halfFovCos);
                    }
                    if (!mate && !visible) continue;

                    String skin = skinCache.computeIfAbsent(p.getUUID(), k -> {
                        try {
                            return SkinRegistry.representativeSkin(p);
                        } catch (RuntimeException e) {
                            return "";
                        }
                    });

                    out.add(new TcpTacticalMapS2CPacket.Blip(
                            p.getUUID(),
                            p.getGameProfile().getName(),
                            p.getX(), p.getY(), p.getZ(),
                            p.getYRot(),
                            code,
                            alive,
                            visible,
                            skin));
                }
            }

            TcpNetwork.sendToPlayer(viewer, new TcpTacticalMapS2CPacket(range, ownCode, spectator, out));
        }
    }

    /** 距离 + 视锥 + 方块遮挡射线。 */
    private static boolean canSee(ServerLevel level, ServerPlayer viewer, ServerPlayer target,
                                  float range, double halfFovCos) {
        Vec3 eye = viewer.getEyePosition();
        Vec3 chest = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        double dist = eye.distanceTo(chest);
        if (dist > range) return false;

        Vec3 look = viewer.getViewVector(1.0f).normalize();
        Vec3 dir = chest.subtract(eye);
        if (dir.lengthSqr() < 1.0E-4) return true;
        if (look.dot(dir.normalize()) < halfFovCos) return false;

        return clearLine(level, viewer, eye, chest) || clearLine(level, viewer, eye, target.getEyePosition());
    }

    private static boolean clearLine(ServerLevel level, ServerPlayer viewer, Vec3 from, Vec3 to) {
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer);
        BlockHitResult hit = level.clip(ctx);
        return hit.getType() == HitResult.Type.MISS;
    }
}
