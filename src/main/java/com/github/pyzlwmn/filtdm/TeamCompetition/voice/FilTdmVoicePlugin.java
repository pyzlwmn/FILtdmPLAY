package com.github.pyzlwmn.filtdm.TeamCompetition.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;

/**
 * ★ v1.1.4：SVC 插件入口（通过 {@code META-INF/services} 注册）。
 *
 * <p>SVC 启动时会加载本类并回调 {@link #initialize(VoicechatApi)}，
 * 我们在这里拿到服务端 API，之后就能强制给玩家分组。</p>
 */
public final class FilTdmVoicePlugin implements VoicechatPlugin {

    /** 服务端 API；客户端环境 / SVC 未就绪时保持 null。 */
    public static volatile VoicechatServerApi SERVER_API;

    @Override
    public String getPluginId() {
        return "filtdm";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi server) {
            SERVER_API = server;
        }
    }
}
