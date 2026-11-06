package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * 死亡镜头控制包（v73 扩展）。
 *
 * <p>START 携带：
 * <ul>
 *   <li>origin：死亡瞬间眼位（镜头起点）</li>
 *   <li>corpse：尸体位置（镜头全程 lookAt）；无尸体写 NaN</li>
 *   <li>durationTicks：本次死亡镜头总时长（按 respawnDelaySeconds 下发）</li>
 *   <li>yaw / pitch：死亡瞬间朝向</li>
 *   <li>killer / killerTeam / gun / ammo / headshot：死亡 UI 击杀横幅数据</li>
 *   <li>scoreBlue / scoreRed / scoreTarget：死亡 UI 右下比分快照</li>
 * </ul>
 */
public class TcpDeathCameraS2CPacket {

    public static final int ACTION_STOP = 0;
    public static final int ACTION_START = 1;

    private final int action;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private final double corpseX;
    private final double corpseY;
    private final double corpseZ;
    private final int durationTicks;

    private final String killer;
    private final int killerTeam;
    private final ItemStack gun;
    private final String ammoText;
    private final boolean headshot;

    private final int scoreBlue;
    private final int scoreRed;
    private final int scoreTarget;

    private TcpDeathCameraS2CPacket(int action, double x, double y, double z, float yaw, float pitch,
                                    double corpseX, double corpseY, double corpseZ, int durationTicks,
                                    String killer, int killerTeam, ItemStack gun, String ammoText,
                                    boolean headshot, int scoreBlue, int scoreRed, int scoreTarget) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.corpseX = corpseX;
        this.corpseY = corpseY;
        this.corpseZ = corpseZ;
        this.durationTicks = durationTicks;
        this.killer = killer == null ? "" : killer;
        this.killerTeam = killerTeam;
        this.gun = gun == null ? ItemStack.EMPTY : gun;
        this.ammoText = ammoText == null ? "" : ammoText;
        this.headshot = headshot;
        this.scoreBlue = scoreBlue;
        this.scoreRed = scoreRed;
        this.scoreTarget = scoreTarget;
    }

    /** 兼容旧构造：无尸体、默认时长、无击杀信息 */
    public TcpDeathCameraS2CPacket(int action, double x, double y, double z, float yaw, float pitch) {
        this(action, x, y, z, yaw, pitch, Double.NaN, Double.NaN, Double.NaN, 0,
                "", -1, ItemStack.EMPTY, "", false, 0, 0, 25);
    }

    /** 完整 START 构造 */
    public static TcpDeathCameraS2CPacket start(double x, double y, double z, float yaw, float pitch,
                                               double corpseX, double corpseY, double corpseZ,
                                               int durationTicks,
                                               String killer, int killerTeam, ItemStack gun,
                                               String ammoText, boolean headshot,
                                               int scoreBlue, int scoreRed, int scoreTarget) {
        return new TcpDeathCameraS2CPacket(ACTION_START, x, y, z, yaw, pitch,
                corpseX, corpseY, corpseZ, durationTicks,
                killer, killerTeam, gun, ammoText, headshot, scoreBlue, scoreRed, scoreTarget);
    }

    /** 简版 START：无尸体、无击杀信息 */
    public static TcpDeathCameraS2CPacket start(double x, double y, double z, float yaw, float pitch) {
        return new TcpDeathCameraS2CPacket(ACTION_START, x, y, z, yaw, pitch,
                Double.NaN, Double.NaN, Double.NaN, 0,
                "", -1, ItemStack.EMPTY, "", false, 0, 0, 25);
    }

    public static TcpDeathCameraS2CPacket stop() {
        return new TcpDeathCameraS2CPacket(ACTION_STOP, 0, 0, 0, 0, 0,
                Double.NaN, Double.NaN, Double.NaN, 0,
                "", -1, ItemStack.EMPTY, "", false, 0, 0, 25);
    }

    public int action() {
        return action;
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

    public double corpseX() {
        return corpseX;
    }

    public double corpseY() {
        return corpseY;
    }

    public double corpseZ() {
        return corpseZ;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public String killer() {
        return killer;
    }

    public int killerTeam() {
        return killerTeam;
    }

    public ItemStack gun() {
        return gun;
    }

    public boolean hasGun() {
        return gun != null && !gun.isEmpty();
    }

    public String ammoText() {
        return ammoText;
    }

    public boolean headshot() {
        return headshot;
    }

    public int scoreBlue() {
        return scoreBlue;
    }

    public int scoreRed() {
        return scoreRed;
    }

    public int scoreTarget() {
        return scoreTarget;
    }

    public boolean hasCorpse() {
        return !Double.isNaN(corpseX) && !Double.isNaN(corpseY) && !Double.isNaN(corpseZ);
    }

    public static void encode(TcpDeathCameraS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        if (msg.action != ACTION_START) return;

        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
        buf.writeVarInt(msg.durationTicks);

        buf.writeBoolean(msg.hasCorpse());
        if (msg.hasCorpse()) {
            buf.writeDouble(msg.corpseX);
            buf.writeDouble(msg.corpseY);
            buf.writeDouble(msg.corpseZ);
        }

        buf.writeUtf(msg.killer, 48);
        buf.writeVarInt(msg.killerTeam + 1);
        buf.writeBoolean(msg.hasGun());
        if (msg.hasGun()) {
            buf.writeItem(msg.gun);
        }
        buf.writeUtf(msg.ammoText, 24);
        buf.writeBoolean(msg.headshot);
        buf.writeVarInt(msg.scoreBlue);
        buf.writeVarInt(msg.scoreRed);
        buf.writeVarInt(msg.scoreTarget);
    }

    public static TcpDeathCameraS2CPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        if (action != ACTION_START) {
            return stop();
        }
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        int duration = buf.readVarInt();

        double cx = Double.NaN;
        double cy = Double.NaN;
        double cz = Double.NaN;
        if (buf.readBoolean()) {
            cx = buf.readDouble();
            cy = buf.readDouble();
            cz = buf.readDouble();
        }

        String killer = buf.readUtf(48);
        int killerTeam = buf.readVarInt() - 1;
        ItemStack gun = buf.readBoolean() ? buf.readItem() : ItemStack.EMPTY;
        String ammo = buf.readUtf(24);
        boolean headshot = buf.readBoolean();
        int sb = buf.readVarInt();
        int sr = buf.readVarInt();
        int st = buf.readVarInt();

        return start(x, y, z, yaw, pitch, cx, cy, cz, duration,
                killer, killerTeam, gun, ammo, headshot, sb, sr, st);
    }

    /** 在客户端线程接收（TcpNetwork 已 enqueueWork） */
    public static void receive(TcpDeathCameraS2CPacket msg) {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.github.pyzlwmn.filtdm.TeamCompetition.client.spec.TcpDeathCameraClient.accept(msg));
    }

    public static void handle(TcpDeathCameraS2CPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> receive(msg));
        context.setPacketHandled(true);
    }
}
