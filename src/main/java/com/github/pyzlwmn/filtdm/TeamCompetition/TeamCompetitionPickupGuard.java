package com.github.pyzlwmn.filtdm.TeamCompetition;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;

import java.util.Optional;

/**
 * v81：对局中禁止捡东西进物品栏。
 *
 * <p>原因：右下角物品栏只显示 1~5 号槽（配装固定），玩家捡起物品时
 * 原版会塞进 6~9 号空格 → 出现「3/4 号被塞东西」「5 号跑到 9 号」这种错位。
 * 直接在对局期间取消拾取，物品留在地上。</p>
 */
@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamCompetitionPickupGuard {

    private TeamCompetitionPickupGuard() {
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        TeamCompetitionMap map = mapOf(player);
        if (map == null) return;
        if (!map.isStart()) return;

        event.setCanceled(true);
    }

    private static TeamCompetitionMap mapOf(ServerPlayer player) {
        Optional<BaseMap> cached = FPSMCore.getInstance().getMapByPlayer(player);
        if (cached.isPresent() && cached.get() instanceof TeamCompetitionMap tcp) {
            return tcp;
        }
        for (TeamCompetitionMap map : FPSMCore.getInstance().getMapByClass(TeamCompetitionMap.class)) {
            if (map.checkGameHasPlayer(player)) return map;
        }
        return null;
    }
}
