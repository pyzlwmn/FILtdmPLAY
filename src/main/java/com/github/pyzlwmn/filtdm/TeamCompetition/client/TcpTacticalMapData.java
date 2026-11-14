package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpTacticalMapS2CPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * v80：战术地图客户端数据缓存（服务端算好可见性，客户端只负责画）。
 */
@OnlyIn(Dist.CLIENT)
public final class TcpTacticalMapData {

    private static volatile List<TcpTacticalMapS2CPacket.Blip> blips = List.of();
    private static volatile float range = 48.0f;
    private static volatile int viewerTeam = 2;
    private static volatile boolean spectator = false;
    private static volatile long lastUpdateMs = 0L;

    private TcpTacticalMapData() {
    }

    public static void update(float rangeIn, int viewerTeamIn, boolean spectatorIn,
                              List<TcpTacticalMapS2CPacket.Blip> blipsIn) {
        range = rangeIn;
        viewerTeam = viewerTeamIn;
        spectator = spectatorIn;
        blips = blipsIn == null ? List.of() : blipsIn;
        lastUpdateMs = System.currentTimeMillis();
    }

    public static List<TcpTacticalMapS2CPacket.Blip> blips() {
        return blips;
    }

    public static float range() {
        return range;
    }

    public static int viewerTeam() {
        return viewerTeam;
    }

    public static boolean spectator() {
        return spectator;
    }

    /** 超过 2 秒没收到包（退出对局 / 断线）就不再画雷达，避免残留。 */
    public static boolean fresh() {
        return lastUpdateMs > 0L && System.currentTimeMillis() - lastUpdateMs < 2000L;
    }

    public static void clear() {
        blips = List.of();
        lastUpdateMs = 0L;
    }
}
