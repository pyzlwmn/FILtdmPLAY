package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.ClientConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * v88：小地图地形层（客户端本地采样，服务端零负担）。
 *
 * <p>做法：</p>
 * <ul>
 *   <li>以玩家为中心，按 <b>2 格 = 1 像素</b> 采样最高方块的地图色</li>
 *   <li>颜色取 {@code BlockState#getMapColor}（草绿/石灰/水蓝/沙黄…），
 *       再按高度差做明暗，透视出起伏</li>
 *   <li>结果写进一张 {@link DynamicTexture}，**外圈 alpha=0 直接烘进纹理**，
 *       所以旋转绘制后仍是正圆，不需要运行时裁剪</li>
 *   <li>每 {@value #REFRESH_TICKS} tick 才重建一次，重建量为 48×48 = 2304 次方块查询</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class RadarTerrain {

    /** 网格边长（格数）：range 48 / 2 格一像素 → 直径 96 格 = 48 格网。 */
    public static final int GRID = 48;
    /** 每格代表的方块数。 */
    public static final int BLOCK_PER_CELL = 2;
    /** 重建间隔（tick）。 */
    public static final int REFRESH_TICKS = 8;

    private static final ResourceLocation TEX_ID =
            ResourceLocation.tryBuild("filtdm", "radar_terrain");

    private static final int[] COLORS = new int[GRID * GRID];

    private static DynamicTexture texture;
    private static NativeImage image;
    private static boolean dirty = true;
    private static boolean broken = false;
    private static int tickCounter = 0;
    private static Level lastLevel = null;

    private RadarTerrain() {
    }

    /** 每 client tick 调一次（由 HUD 侧调用即可）。 */
    public static void tick(Level level) {
        if (level == null) return;
        if (level != lastLevel) {
            lastLevel = level;
            dirty = true;
        }
        if (!ClientConfig.radarTerrain) return;
        if (++tickCounter < REFRESH_TICKS) return;
        tickCounter = 0;
        try {
            rebuild(level);
        } catch (Throwable t) {
            // 地形层属于装饰功能，任何异常都只放弃本帧刷新，绝不崩客户端
            texture = null;
            broken = true;
            tickCounter = 0;
        }
    }

    private static void rebuild(Level level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int originX = mc.player.blockPosition().getX() - (GRID * BLOCK_PER_CELL) / 2;
        int originZ = mc.player.blockPosition().getZ() - (GRID * BLOCK_PER_CELL) / 2;
        int py = mc.player.blockPosition().getY();

        float half = GRID / 2.0f;
        float r2 = half * half;

        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int idx = gz * GRID + gx;

                // 圆形遮罩（中心到格子中心）
                float cx = gx + 0.5f - half;
                float cz = gz + 0.5f - half;
                if (cx * cx + cz * cz > r2) {
                    COLORS[idx] = 0;
                    continue;
                }

                int wx = originX + gx * BLOCK_PER_CELL;
                int wz = originZ + gz * BLOCK_PER_CELL;
                int wy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);

                BlockState state = level.getBlockState(
                        new net.minecraft.core.BlockPos(wx, Math.max(wy - 1, level.getMinBuildHeight()), wz));
                MapColor mapColor = state.getMapColor(level, new net.minecraft.core.BlockPos(wx, wy, wz));
                int rgb = mapColor == null ? 0x808080 : mapColor.col;

                // 高度明暗：越高于玩家越亮，低处更暗
                int dy = wy - py;
                float shade = 1.0f + Math.max(-0.35f, Math.min(0.35f, dy * 0.03f));
                rgb = shade(rgb, shade);

                COLORS[idx] = 0xFF000000 | rgb;
            }
        }

        upload();
        dirty = false;
    }

    private static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static void upload() {
        // ★ 关键：DynamicTexture 会接管并关闭旧的 NativeImage，
        // 所以每次重建都必须 new 一张新图，绝对不能复用同一张
        NativeImage img = new NativeImage(GRID, GRID, false);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                int c = COLORS[y * GRID + x];
                int a = (c >>> 24) & 0xFF;
                int rr = (c >> 16) & 0xFF;
                int gg = (c >> 8) & 0xFF;
                int bb = c & 0xFF;
                img.setPixelRGBA(x, y, (a << 24) | (bb << 16) | (gg << 8) | rr);
            }
        }

        try {
            if (texture == null) {
                texture = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(TEX_ID, texture);
            } else {
                texture.setPixels(img);
                texture.upload();
            }
            broken = false;
        } catch (Throwable t) {
            // 纹理被资源重载/世界切换关掉了 → 丢掉重建
            try {
                img.close();
            } catch (Throwable ignored) {
            }
            texture = null;
            broken = true;
        }
    }

    public static boolean ready() {
        return texture != null && !dirty && !broken;
    }

    public static ResourceLocation textureId() {
        return TEX_ID;
    }
}
