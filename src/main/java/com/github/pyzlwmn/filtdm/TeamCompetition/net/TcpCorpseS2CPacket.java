package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/**
 * 尸体同步包（S2C，v73）。
 *
 * <p>ACTION_SPAWN：死亡瞬间 → 客户端在尸体位置生成一具带皮肤的躺倒人形 + 掉落的枪。
 * <br>ACTION_REMOVE：玩家复活 / 对局结束 / 重置 → 客户端清除该尸体。
 *
 * <p>尸体完全虚拟：客户端只渲染，不生成真实实体，不可交互、不可碰撞、不可拾取。
 */
public class TcpCorpseS2CPacket {

    public static final int ACTION_SPAWN = 0;
    public static final int ACTION_REMOVE = 1;

    private final int action;
    private final UUID owner;
    private final String name;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final int team;
    private final ItemStack gun;

    private TcpCorpseS2CPacket(int action, UUID owner, String name,
                               double x, double y, double z, float yaw, float pitch,
                               int team, ItemStack gun) {
        this.action = action;
        this.owner = owner;
        this.name = name == null ? "" : name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.team = team;
        this.gun = gun == null ? ItemStack.EMPTY : gun;
    }

    public static TcpCorpseS2CPacket spawn(UUID owner, String name,
                                          double x, double y, double z, float yaw, float pitch,
                                          int team, ItemStack gun) {
        return new TcpCorpseS2CPacket(ACTION_SPAWN, owner, name, x, y, z, yaw, pitch, team, gun);
    }

    public static TcpCorpseS2CPacket remove(UUID owner) {
        return new TcpCorpseS2CPacket(ACTION_REMOVE, owner, "", 0, 0, 0, 0, 0, -1, ItemStack.EMPTY);
    }

    public int action() {
        return action;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public int team() {
        return team;
    }

    public ItemStack gun() {
        return gun;
    }

    public static void encode(TcpCorpseS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeUUID(msg.owner);
        if (msg.action != ACTION_SPAWN) return;

        buf.writeUtf(msg.name, 48);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
        buf.writeVarInt(msg.team);

        buf.writeBoolean(!msg.gun.isEmpty());
        if (!msg.gun.isEmpty()) {
            buf.writeItem(msg.gun);
        }
    }

    public static TcpCorpseS2CPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        UUID owner = buf.readUUID();
        if (action != ACTION_SPAWN) {
            return remove(owner);
        }

        String name = buf.readUtf(48);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        int team = buf.readVarInt();
        ItemStack gun = buf.readBoolean() ? buf.readItem() : ItemStack.EMPTY;
        return spawn(owner, name, x, y, z, yaw, pitch, team, gun);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.github.pyzlwmn.filtdm.TeamCompetition.client.spec.TcpCorpseClient.accept(this);
        }
    }
}
