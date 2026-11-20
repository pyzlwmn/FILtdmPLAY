package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpHitDirData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * ★ v1.1.0：受击方向包（服务端 → 受害者客户端）。
 *
 * <p><b>只在「被击中的那一刻」算一次世界方位角</b>（以开火者当时的位置为准），
 * 之后无论开火者往哪跑、受害者往哪走，这个角度都不变；
 * 客户端每帧用 {@code bearing - 玩家当前 yaw} 得到相对角，所以只有「受害者转视角」时三角才会沿圆滑动。</p>
 *
 * <p>约定与 MC 一致：yaw 0° = +Z（南），90° = -X（西），
 * 服务端用 {@code toDegrees(atan2(-dx, dz))} 计算，客户端直接减 {@code player.getYRot()}。</p>
 */
public class TcpHitDirS2CPacket {

    /** 世界方位角（度） */
    private final float bearing;
    /** 本次伤害值（客户端可用来做轻微亮度区分，0 = 未知） */
    private final float damage;

    public TcpHitDirS2CPacket(float bearing, float damage) {
        this.bearing = bearing;
        this.damage = damage;
    }

    public static void encode(TcpHitDirS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.bearing);
        buf.writeFloat(msg.damage);
    }

    public static TcpHitDirS2CPacket decode(FriendlyByteBuf buf) {
        return new TcpHitDirS2CPacket(buf.readFloat(), buf.readFloat());
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpHitDirData.push(bearing, damage);
        }
    }
}
