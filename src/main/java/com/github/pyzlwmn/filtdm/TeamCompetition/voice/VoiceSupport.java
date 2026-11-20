package com.github.pyzlwmn.filtdm.TeamCompetition.voice;

import com.github.pyzlwmn.filtdm.TeamCompetition.TeamCompetitionMap;
import net.minecraftforge.fml.ModList;

/**
 * ★ v1.1.4：SVC（Simple Voice Chat）适配的安全入口。
 *
 * <p>所有和 SVC API 相关的类都放在 {@link SvcVoice} / {@link FilTdmVoicePlugin} 里，
 * 这里<b>不引用任何 SVC 类型</b>，只做「装没装」的判断，所以没装 SVC 时整个 mod 照常跑。</p>
 */
public final class VoiceSupport {

    private VoiceSupport() {
    }

    /** SVC 是否已加载（modid = voicechat）。 */
    public static boolean available() {
        try {
            return ModList.get().isLoaded("voicechat");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 每秒同步一次队伍语音分组；没装 SVC 时直接返回。 */
    public static void tick(TeamCompetitionMap map) {
        if (!available()) return;
        try {
            SvcVoice.tick(map);
        } catch (Throwable ignored) {
            // SVC 版本不匹配等异常一律吞掉，绝不能影响对局
        }
    }
}
