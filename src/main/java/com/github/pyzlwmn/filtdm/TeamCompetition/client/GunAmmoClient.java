package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * v80：客户端弹药读取（TaCZ 反射，不硬依赖 —— 没装 TaCZ 时全部安全降级）。
 *
 * <p>数据来源：</p>
 * <ul>
 *   <li>弹匣内：物品 NBT {@code GunCurrentAmmoCount}（TaCZ 自己写的）</li>
 *   <li>弹匣容量：{@code TimelessAPI.getClientGunIndex} → {@code ClientGunIndex.getGunData} →
 *       {@code AttachmentDataUtils.getAmmoCountWithAttachment}（含弹匣配件扩容）</li>
 *   <li>备弹：扫背包里 {@code IAmmo} / {@code IAmmoBox}，调 {@code isAmmoOfGun}</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class GunAmmoClient {

    public record Ammo(boolean gun, int mag, int magSize, int reserve) {
        public static final Ammo NONE = new Ammo(false, 0, 0, 0);
    }

    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static Boolean taczPresent = null;

    private GunAmmoClient() {
    }

    public static boolean taczPresent() {
        if (taczPresent == null) {
            try {
                Class.forName("com.tacz.guns.api.item.IGun");
                taczPresent = Boolean.TRUE;
            } catch (Throwable t) {
                taczPresent = Boolean.FALSE;
            }
        }
        return taczPresent;
    }

    public static boolean isGun(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !taczPresent()) return false;
        try {
            Class<?> igun = Class.forName("com.tacz.guns.api.item.IGun");
            return igun.isInstance(stack.getItem());
        } catch (Throwable t) {
            return false;
        }
    }

    public static Ammo of(ItemStack stack) {
        if (!isGun(stack)) return Ammo.NONE;
        int mag = magCount(stack);
        int size = magSize(stack);
        int reserve = reserveCount(stack);
        return new Ammo(true, mag, size, reserve);
    }

    public static int magCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tag = stack.getTag();
        if (tag == null) return 0;
        return tag.contains("GunCurrentAmmoCount") ? tag.getInt("GunCurrentAmmoCount") : 0;
    }

    public static int magSize(ItemStack stack) {
        if (!isGun(stack)) return 0;
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Method getIndex = api.getMethod("getClientGunIndex", net.minecraft.resources.ResourceLocation.class);
            Object optional = getIndex.invoke(null, gunId(stack));
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) return 0;
            Object clientGunIndex = opt.get();
            Method getGunData = clientGunIndex.getClass().getMethod("getGunData");
            Object gunData = getGunData.invoke(clientGunIndex);
            if (gunData == null) return 0;
            Class<?> utils = Class.forName("com.tacz.guns.util.AttachmentDataUtils");
            Method m = utils.getMethod("getAmmoCountWithAttachment", ItemStack.class, gunData.getClass());
            Object v = m.invoke(null, stack, gunData);
            return v instanceof Integer i ? i : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static net.minecraft.resources.ResourceLocation gunId(ItemStack stack) {
        try {
            Class<?> igun = Class.forName("com.tacz.guns.api.item.IGun");
            Method getId = igun.getMethod("getGunId", ItemStack.class);
            Object rl = getId.invoke(stack.getItem(), stack);
            return rl instanceof net.minecraft.resources.ResourceLocation r ? r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 背包里这把枪能用的备弹总数；取不到时返回 -1（UI 显示「--」）。 */
    public static int reserveCount(ItemStack gun) {
        if (!isGun(gun)) return -1;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return -1;
        int total = 0;
        boolean ok = false;
        List<ItemStack> items = mc.player.getInventory().items;
        for (ItemStack it : items) {
            if (it == null || it.isEmpty()) continue;
            int n = ammoInItem(gun, it);
            if (n < 0) continue;
            ok = true;
            if (n >= 9999) return 9999;
            total += n;
        }
        return ok ? total : -1;
    }

    /** 单个物品给这把枪贡献多少发；-1 = 不是这把枪的弹药。 */
    private static int ammoInItem(ItemStack gun, ItemStack item) {
        try {
            Class<?> iammo = Class.forName("com.tacz.guns.api.item.IAmmo");
            if (iammo.isInstance(item.getItem())) {
                Method isAmmoOfGun = find(iammo, "isAmmoOfGun", 2);
                if (isAmmoOfGun == null) return -1;
                Object ok = isAmmoOfGun.invoke(item.getItem(), gun, item);
                if (Boolean.TRUE.equals(ok)) return item.getCount();
                return -1;
            }
            Class<?> iammoBox = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            if (iammoBox.isInstance(item.getItem())) {
                Method isBoxOfGun = find(iammoBox, "isAmmoBoxOfGun", 2);
                if (isBoxOfGun == null) return -1;
                Object ok = isBoxOfGun.invoke(item.getItem(), gun, item);
                if (!Boolean.TRUE.equals(ok)) return -1;
                Method creative = find(iammoBox, "isAllTypeCreative", 1);
                if (creative != null && Boolean.TRUE.equals(creative.invoke(item.getItem(), item))) return 9999;
                Method isCreative = find(iammoBox, "isCreative", 1);
                if (isCreative != null && Boolean.TRUE.equals(isCreative.invoke(item.getItem(), item))) return 9999;
                Method getCount = find(iammoBox, "getAmmoCount", 1);
                if (getCount == null) return 0;
                Object v = getCount.invoke(item.getItem(), item);
                return v instanceof Integer i ? i : 0;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static Method find(Class<?> type, String name, int argc) {
        String key = type.getName() + "#" + name + "/" + argc;
        Method cached = METHODS.get(key);
        if (cached != null) return cached;
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argc) {
                METHODS.put(key, m);
                return m;
            }
        }
        return null;
    }
}
