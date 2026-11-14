package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpAttachmentData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class TcpAttachmentS2CPacket {

    public static final int FLAG_OPEN = 1;
    public static final int FLAG_CAN_EDIT = 2;

    public record TypeEntry(String type, String current, List<String> options) {
    }

    /**
     * 数值面板一行（v74）。
     *
     * @param key     内部字段名（damage / rpm / ammo / recoil / accuracy / aim / weight）
     * @param label   中文名（服务端算好，客户端直接画）
     * @param value   显示值（已格式化的字符串，避免两端格式化不一致）
     * @param raw     原始数值（客户端做「改前 vs 改后」对比条用）
     * @param percent 是否百分比显示
     */
    public record StatEntry(String key, String label, String value, float raw, boolean percent) {
    }

    public record GunEntry(String slotName, String label, String gunId, List<TypeEntry> types,
                           List<StatEntry> stats) {

        /** v74 兼容：老代码三参构造 → 无数值 */
        public GunEntry(String slotName, String label, String gunId, List<TypeEntry> types) {
            this(slotName, label, gunId, types, List.of());
        }
    }

    private final int flags;
    private final List<GunEntry> guns;

    public TcpAttachmentS2CPacket(int flags, List<GunEntry> guns) {
        this.flags = flags;
        this.guns = guns == null ? List.of() : guns;
    }

    public static void encode(TcpAttachmentS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.flags);
        buf.writeVarInt(msg.guns.size());
        for (GunEntry g : msg.guns) {
            buf.writeUtf(g.slotName(), 32);
            buf.writeUtf(g.label(), 64);
            buf.writeUtf(g.gunId() == null ? "" : g.gunId(), 128);
            buf.writeVarInt(g.types().size());
            for (TypeEntry t : g.types()) {
                buf.writeUtf(t.type(), 32);
                buf.writeUtf(t.current() == null ? "" : t.current(), 128);
                buf.writeVarInt(t.options().size());
                for (String o : t.options()) buf.writeUtf(o, 128);
            }
            // ★ v74：数值面板
            buf.writeVarInt(g.stats().size());
            for (StatEntry s : g.stats()) {
                buf.writeUtf(s.key(), 24);
                buf.writeUtf(s.label(), 32);
                buf.writeUtf(s.value() == null ? "" : s.value(), 32);
                buf.writeFloat(s.raw());
                buf.writeBoolean(s.percent());
            }
        }
    }

    public static TcpAttachmentS2CPacket decode(FriendlyByteBuf buf) {
        int flags = buf.readVarInt();
        int gn = buf.readVarInt();
        List<GunEntry> guns = new ArrayList<>(gn);
        for (int i = 0; i < gn; i++) {
            String slotName = buf.readUtf(32);
            String label = buf.readUtf(64);
            String gunId = buf.readUtf(128);
            int tn = buf.readVarInt();
            List<TypeEntry> types = new ArrayList<>(tn);
            for (int j = 0; j < tn; j++) {
                String type = buf.readUtf(32);
                String current = buf.readUtf(128);
                int on = buf.readVarInt();
                List<String> options = new ArrayList<>(on);
                for (int k = 0; k < on; k++) options.add(buf.readUtf(128));
                types.add(new TypeEntry(type, current.isEmpty() ? null : current, options));
            }
            int sn = buf.readVarInt();
            List<StatEntry> stats = new ArrayList<>(sn);
            for (int s = 0; s < sn; s++) {
                stats.add(new StatEntry(buf.readUtf(24), buf.readUtf(32), buf.readUtf(32),
                        buf.readFloat(), buf.readBoolean()));
            }
            guns.add(new GunEntry(slotName, label, gunId.isEmpty() ? null : gunId, types, stats));
        }
        return new TcpAttachmentS2CPacket(flags, guns);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpAttachmentData.update(flags, guns);
            if ((flags & FLAG_OPEN) != 0) {
                TcpAttachmentData.openEditor();
            }
        }
    }
}
