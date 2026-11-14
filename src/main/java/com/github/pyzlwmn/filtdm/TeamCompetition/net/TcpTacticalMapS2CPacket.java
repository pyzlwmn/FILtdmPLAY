package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpTacticalMapData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v80：战术地图同步包（服务端 → 客户端）。
 *
 * <p>一个玩家一个包：包内只有「他能看到的东西」——
 * 队友（全员常驻，含死亡标记）+ 敌人（仅当判定为可见时才出现）。
 * 判定在服务端做（距离 / 视锥 / 方块遮挡射线），客户端不做任何作弊相关判断。</p>
 */
public class TcpTacticalMapS2CPacket {

    /** 雷达上的一个光点。 */
    public record Blip(UUID id,
                       String name,
                       double x, double y, double z,
                       float yaw,
                       int team,
                       boolean alive,
                       boolean visible,
                       String skin) {
    }

    private final float range;
    private final int viewerTeam;
    private final boolean spectator;
    private final List<Blip> blips;

    public TcpTacticalMapS2CPacket(float range, int viewerTeam, boolean spectator, List<Blip> blips) {
        this.range = range;
        this.viewerTeam = viewerTeam;
        this.spectator = spectator;
        this.blips = blips == null ? List.of() : blips;
    }

    public static void encode(TcpTacticalMapS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.range);
        buf.writeVarInt(msg.viewerTeam);
        buf.writeBoolean(msg.spectator);
        buf.writeVarInt(msg.blips.size());
        for (Blip b : msg.blips) {
            buf.writeUUID(b.id());
            buf.writeUtf(b.name(), 32);
            buf.writeDouble(b.x());
            buf.writeDouble(b.y());
            buf.writeDouble(b.z());
            buf.writeFloat(b.yaw());
            buf.writeVarInt(b.team());
            buf.writeBoolean(b.alive());
            buf.writeBoolean(b.visible());
            buf.writeUtf(b.skin() == null ? "" : b.skin(), 96);
        }
    }

    public static TcpTacticalMapS2CPacket decode(FriendlyByteBuf buf) {
        float range = buf.readFloat();
        int viewerTeam = buf.readVarInt();
        boolean spectator = buf.readBoolean();
        int n = buf.readVarInt();
        List<Blip> blips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            blips.add(new Blip(
                    buf.readUUID(),
                    buf.readUtf(32),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(96)));
        }
        return new TcpTacticalMapS2CPacket(range, viewerTeam, spectator, blips);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpTacticalMapData.update(range, viewerTeam, spectator, blips);
        }
    }
}
