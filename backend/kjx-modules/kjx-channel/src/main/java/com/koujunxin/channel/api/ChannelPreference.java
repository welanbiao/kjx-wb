package com.koujunxin.channel.api;

/**
 * 作品覆盖优先于会员，会员优先于平台默认。空表示未设置。
 */
public record ChannelPreference(ChannelMode workMode, ChannelMode memberMode, ChannelMode platformMode) {

    public ChannelPreference {
        if (platformMode == null) {
            platformMode = ChannelMode.PLATFORM;
        }
    }
}
