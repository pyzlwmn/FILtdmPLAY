package com.github.pyzlwmn.filtdm.TeamCompetition.loadout;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 枪械数值读取（v74，配件页数值面板用）。
 *
 * <p>全部走反射读 TaCZ 的 {@code GunData} / {@code AttachmentData}，TaCZ 没装就返回空列表，
 * 绝不抛异常。数值按「枪本体 → 再叠加各配件 modifier」的顺序算，和游戏里的实际手感一致。
 *
 * <p>对外只吐 {@link Stat}（key / 中文名 / 显示值 / 原始值 / 是否百分比），
 * 序列化由 {@code TcpAttachmentS2CPacket} 负责。
 */
public final class GunStats {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-Stats");

    /** 一条数值 */
    public record Stat(String key, String label, String value, float raw, boolean percent) {
        public Stat {
            if (label == null) label = key;
            if (value == null) value = "-";
        }
    }

    /**
     * 读写改前 / 改后对比用的小容器。
     */
    public record Snapshot(Map<String, Float> raw, Map<String, String> text) {
        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }
    }

    private GunStats() {
    }

    // ==================== 对外入口 ====================

    /**
     * 读一把枪的完整数值面板。
     *
     * @param gunId       枪械 id（tacz:xxx）
     * @param attachments 槽位类型 → 配件 id（当前的/悬停预览的）
     * @return 按显示顺序排好的数值，读不到时返回空列表
     */
    public static List<Stat> read(String gunId, Map<String, String> attachments) {
        List<Stat> out = new ArrayList<>();
        if (gunId == null || gunId.isBlank()) return out;
        if (!GunItemFactory.taczLoaded()) return out;

        try {
            Object gunData = commonGunData(gunId);
            if (gunData == null) return out;

            // ---- 基础值 ----
            double damage = num(invoke(gunData, "getBulletData"), "getDamageAmount", 0.0);
            int rpm = (int) num(gunData, "getRoundsPerMinute", 0.0);
            int mag = (int) num(gunData, "getAmmoAmount", 0.0);
            double aimTime = num(gunData, "getAimTime", 0.0);
            double weight = num(gunData, "getWeight", 0.0);
            double recoilPitch = firstRecoilValue(invoke(gunData, "getRecoil"), "getPitch");
            double recoilYaw = firstRecoilValue(invoke(gunData, "getRecoil"), "getYaw");
            double inaccuracy = inaccuracyOf(gunData, "STAND");
            double inaccuracyAim = inaccuracyOf(gunData, "AIM");

            // ---- 叠加配件 modifier ----
            double dmgMul = 1.0, rpmMul = 1.0, magMul = 1.0, aimMul = 1.0;
            double recoilMul = 1.0, inaccMul = 1.0;
            if (attachments != null && !attachments.isEmpty()) {
                for (Map.Entry<String, String> e : new LinkedHashMap<>(attachments).entrySet()) {
                    if (e.getValue() == null || e.getValue().isBlank()) continue;
                    Object attData = commonAttachmentData(e.getValue());
                    if (attData == null) continue;
                    Map<String, Double> mods = modifiersOf(attData);
                    dmgMul *= mods.getOrDefault("damage", 1.0);
                    rpmMul *= mods.getOrDefault("rpm", 1.0);
                    magMul *= mods.getOrDefault("ammo", 1.0);
                    aimMul *= mods.getOrDefault("aim", 1.0);
                    recoilMul *= mods.getOrDefault("recoil", 1.0);
                    inaccMul *= mods.getOrDefault("inaccuracy", 1.0);
                    weight += num(attData, "getWeight", 0.0);
                }
            }

            damage *= dmgMul;
            rpm = (int) Math.round(rpm * rpmMul);
            mag = (int) Math.round(mag * magMul);
            aimTime *= aimMul;
            recoilPitch *= recoilMul;
            recoilYaw *= recoilMul;
            inaccuracy *= inaccMul;
            inaccuracyAim *= inaccMul;

            if (damage > 0) out.add(new Stat("damage", "伤害", fmt1(damage), (float) damage, false));
            if (rpm > 0) out.add(new Stat("rpm", "射速", rpm + " RPM", rpm, false));
            if (mag > 0) out.add(new Stat("ammo", "弹匣", mag + " 发", mag, false));
            if (recoilPitch > 0 || recoilYaw > 0) {
                out.add(new Stat("recoil", "后坐",
                        fmt1(recoilPitch) + " / " + fmt1(recoilYaw), (float) recoilPitch, false));
            }
            if (inaccuracy > 0) {
                // 散布数值越小越准 → 面板上换算成「精度 = 100 - 散布×系数」，越高越好
                double acc = Math.max(0.0, 100.0 - inaccuracy * 100.0);
                out.add(new Stat("accuracy", "精度", fmt1(acc), (float) acc, false));
            }
            if (inaccuracyAim > 0) {
                double accAim = Math.max(0.0, 100.0 - inaccuracyAim * 100.0);
                out.add(new Stat("accuracy_aim", "瞄准精度", fmt1(accAim), (float) accAim, false));
            }
            if (aimTime > 0) out.add(new Stat("aim", "开镜", fmt2(aimTime) + " 秒", (float) aimTime, false));
            if (weight > 0) out.add(new Stat("weight", "重量", fmt2(weight), (float) weight, false));

        } catch (Throwable t) {
            LOG.debug("[TDM·数值] 读 {} 数值失败：{}", gunId, t.toString());
        }
        return out;
    }

    /** 便捷：只给 gunId，按 LoadoutManager 当前生效配件读 */
    public static List<Stat> read(String gunId) {
        return read(gunId, Map.of());
    }

    // ==================== TaCZ 反射 ====================

    /** 拿 CommonGunIndex 里的 GunData（服务端/客户端都能用） */
    private static Object commonGunData(String gunId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(gunId);
            if (rl == null) return null;
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, rl);
            Object idx = unwrap(opt);
            if (idx == null) return null;
            return invoke(idx, "getGunData");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 拿配件的 AttachmentData */
    private static Object commonAttachmentData(String attachmentId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(attachmentId);
            if (rl == null) return null;
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonAttachmentIndex", ResourceLocation.class).invoke(null, rl);
            Object idx = unwrap(opt);
            if (idx == null) return null;
            return invoke(idx, "getData");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object unwrap(Object maybeOptional) {
        if (maybeOptional == null) return null;
        if (maybeOptional instanceof java.util.Optional<?> o) return o.orElse(null);
        return maybeOptional;
    }

    /**
     * 把附件的 modifier 换算成一组乘数。
     *
     * <p>TaCZ 的 modifier 是 {@code Map<String, JsonProperty>}，JsonProperty.getValue() 一般是
     * {@code Modifier{addend, percent, multiplier}}。不同配件用不同 key 名，这里做常见别名归一
     * （见 {@link #canonicalKey(String)}），未知 key 忽略。
     */
    private static Map<String, Double> modifiersOf(Object attachmentData) {
        Map<String, Double> out = new LinkedHashMap<>();
        try {
            Object mapObj = invoke(attachmentData, "getModifier");
            if (!(mapObj instanceof Map<?, ?> map)) return out;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = canonicalKey(String.valueOf(e.getKey()));
                if (key == null) continue;
                Object prop = e.getValue();
                Object value = invoke(prop, "getValue");
                if (value == null) continue;
                double addend = num(value, "getAddend", 0.0);
                double percent = num(value, "getPercent", 0.0);
                double multiplier = num(value, "getMultiplier", 0.0);
                double factor = 1.0;
                if (percent != 0.0) factor += percent / 100.0;
                if (multiplier > 0.0) factor *= multiplier;
                if (addend != 0.0) factor += addend;
                if (factor <= 0.0) continue;
                out.merge(key, factor, (a, b) -> a * b);
            }
        } catch (Throwable t) {
            LOG.debug("[TDM·数值] 读配件 modifier 失败：{}", t.toString());
        }
        return out;
    }

    /** 常见字段别名 → 我们面板用的 6 个维度；不认识返回 null */
    private static String canonicalKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String k = raw.toLowerCase(Locale.ROOT).trim();
        // 后坐
        if (k.contains("recoil")) return "recoil";
        // 精度 / 散布
        if (k.contains("inaccuracy") || k.contains("accuracy") || k.contains("spread")) return "inaccuracy";
        // 伤害
        if (k.contains("damage") || k.contains("armor_ignore")) return "damage";
        // 射速
        if (k.contains("rpm") || k.contains("fire_rate") || k.contains("rounds_per")) return "rpm";
        // 弹匣
        if (k.contains("ammo_amount") || k.contains("ammo") || k.contains("magazine")) return "ammo";
        // 开镜
        if (k.contains("aim_time") || k.contains("aim_speed") || k.contains("ads")) return "aim";
        return null;
    }

    /** 后坐曲线取第一个关键帧的第一个分量（够做面板对比） */
    private static double firstRecoilValue(Object recoil, String getter) {
        try {
            Object frames = invoke(recoil, getter);
            if (frames instanceof Object[] arr && arr.length > 0) {
                Object first = arr[0];
                Object value = invoke(first, "getValue");
                if (value instanceof float[] f && f.length > 0) return f[0];
                if (value instanceof int[] i && i.length > 0) return i[0];
            }
        } catch (Throwable ignored) {
        }
        return 0.0;
    }

    /** 散布：getInaccuracy(InaccuracyType) —— 用枚举名反射取 */
    private static double inaccuracyOf(Object gunData, String typeName) {
        try {
            Class<?> typeCls = Class.forName("com.tacz.guns.resource.pojo.data.gun.InaccuracyType");
            Object type = null;
            for (Object o : typeCls.getEnumConstants()) {
                if (o.toString().equalsIgnoreCase(typeName)) {
                    type = o;
                    break;
                }
            }
            if (type == null) return 0.0;
            for (Method m : gunData.getClass().getMethods()) {
                if (!m.getName().equals("getInaccuracy") || m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isInstance(type)) continue;
                Object r = m.invoke(gunData, type);
                if (r instanceof Number n) return n.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 0.0;
    }

    // ==================== 反射小工具 ====================

    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static double num(Object target, String method, double def) {
        Object r = invoke(target, method);
        return r instanceof Number n ? n.doubleValue() : def;
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
