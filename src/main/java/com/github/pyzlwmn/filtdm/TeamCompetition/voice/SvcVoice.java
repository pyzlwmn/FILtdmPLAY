package com.github.pyzlwmn.filtdm.TeamCompetition.voice;

import com.github.pyzlwmn.filtdm.TeamCompetition.TeamCompetitionMap;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpKillFeedS2CPacket;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.server.level.ServerPlayer;
import net.ptcrys.fpsmatch.core.team.ServerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ★ v1.1.4：SVC 队伍语音适配。
 *
 * <p>规则（对局期间）：</p>
 * <ul>
 *   <li>红队 / 蓝队各一个 {@link Group.Type#ISOLATED} 组 —— 组内互相听得到，组外听不到</li>
 *   <li>死亡 / 旁观 → 切到「死亡频道」（同样是隔离组），活着的人听不到死人说话</li>
 *   <li>复活 → 自动切回本队组</li>
 *   <li>离队 / 对局结束 → 退出所有自建组（回到原版距离语音），并销毁对局组</li>
 * </ul>
 *
 * <p>实现方式：每秒同步一次（同队才 setGroup，切组次数很少，几乎零开销）。
 * 之所以用轮询而不是事件驱动，是为了少改主逻辑、也避免 FPSMatch 各种钩子顺序问题。</p>
 */
public final class SvcVoice {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-TDM-VOICE");

    private static final String NAME_RED = "TDM 红队";
    private static final String NAME_BLUE = "TDM 蓝队";
    private static final String NAME_OTHER = "TDM 队伍";
    private static final String NAME_DEAD = "TDM 死亡频道";

    private static final Map<String, Group> TEAM_GROUPS = new HashMap<>();
    private static Group deadGroup;

    private static int cooldown = 0;
    private static boolean warnedOnce = false;

    private SvcVoice() {
    }

    public static void tick(TeamCompetitionMap map) {
        VoicechatServerApi api = FilTdmVoicePlugin.SERVER_API;
        if (api == null) return;
        if (--cooldown > 0) return;
        cooldown = 20;   // 1 秒

        try {
            sync(api, map);
        } catch (Throwable t) {
            if (!warnedOnce) {
                warnedOnce = true;
                LOGGER.warn("[TDM-VOICE] 语音分组同步失败（后续不再刷屏）：{}", t.toString());
            }
        }
    }

    private static void sync(VoicechatServerApi api, TeamCompetitionMap map) {
        boolean running = map.isStart();
        Set<UUID> managed = managedIds();

        for (ServerPlayer player : map.getMapTeams().getOnlineWithSpec()) {
            VoicechatConnection conn = api.getConnectionOf(player.getUUID());
            if (conn == null || !conn.isConnected()) continue;

            Group want = null;
            if (running) {
                boolean dead = player.isSpectator() || !player.isAlive() || player.getHealth() <= 0.0F;
                if (dead) {
                    want = deadGroup(api);
                } else {
                    ServerTeam team = map.getMapTeams().getTeamByPlayer(player).orElse(null);
                    if (team != null) want = teamGroup(api, team, map);
                }
            }

            if (want == null) {
                Group current = conn.getGroup();
                if (current != null && managed.contains(current.getId())) {
                    conn.setGroup(null);   // 回到普通距离语音
                }
            } else {
                Group current = conn.getGroup();
                if (current == null || !want.getId().equals(current.getId())) {
                    conn.setGroup(want);
                }
            }
        }

        if (!running) {
            cleanup(api);
        }
    }

    private static Group teamGroup(VoicechatServerApi api, ServerTeam team, TeamCompetitionMap map) {
        String name = switch (map.teamCode(team)) {
            case TcpKillFeedS2CPacket.TEAM_RED -> NAME_RED;
            case TcpKillFeedS2CPacket.TEAM_BLUE -> NAME_BLUE;
            default -> NAME_OTHER;
        };
        return ensureGroup(api, TEAM_GROUPS, name);
    }

    private static Group deadGroup(VoicechatServerApi api) {
        if (deadGroup != null && api.getGroup(deadGroup.getId()) != null) return deadGroup;
        deadGroup = api.createGroup(NAME_DEAD, null);
        if (deadGroup != null) {
            LOGGER.info("[TDM-VOICE] 创建语音组 {}", NAME_DEAD);
        }
        return deadGroup;
    }

    private static Group ensureGroup(VoicechatServerApi api, Map<String, Group> cache, String name) {
        Group g = cache.get(name);
        if (g != null && api.getGroup(g.getId()) != null) return g;
        g = api.createGroup(name, null);
        if (g != null) {
            cache.put(name, g);
            LOGGER.info("[TDM-VOICE] 创建语音组 {}", name);
        }
        return g;
    }

    private static Set<UUID> managedIds() {
        Set<UUID> ids = new HashSet<>();
        for (Group g : TEAM_GROUPS.values()) {
            if (g != null) ids.add(g.getId());
        }
        if (deadGroup != null) ids.add(deadGroup.getId());
        return ids;
    }

    /** 对局结束：销毁所有自建组，玩家回到默认语音。 */
    private static void cleanup(VoicechatServerApi api) {
        Collection<Group> groups = TEAM_GROUPS.values();
        for (Group g : groups) {
            if (g != null) api.removeGroup(g.getId());
        }
        TEAM_GROUPS.clear();
        if (deadGroup != null) {
            api.removeGroup(deadGroup.getId());
            deadGroup = null;
        }
    }
}
