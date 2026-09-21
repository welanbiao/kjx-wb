package com.koujunxin.channel.api;

/**
 * 通道优先级：作品 &gt; 会员 &gt; 平台。BYOK 缺少凭据时直接失败，不回退 PLATFORM。
 */
public final class ChannelResolver {

    public ResolvedChannel resolve(ChannelPreference preference, ByokCredential byokCredential) {
        if (preference == null) {
            throw new IllegalArgumentException("preference is required");
        }
        if (preference.workMode() != null) {
            return resolveOne(preference.workMode(), byokCredential, "work");
        }
        if (preference.memberMode() != null) {
            return resolveOne(preference.memberMode(), byokCredential, "member");
        }
        return resolveOne(preference.platformMode(), byokCredential, "platform");
    }

    private ResolvedChannel resolveOne(ChannelMode mode, ByokCredential credential, String source) {
        if (mode == ChannelMode.BYOK && credential == null) {
            throw new IllegalArgumentException("BYOK credential is required");
        }
        return new ResolvedChannel(mode, credential, source);
    }
}
