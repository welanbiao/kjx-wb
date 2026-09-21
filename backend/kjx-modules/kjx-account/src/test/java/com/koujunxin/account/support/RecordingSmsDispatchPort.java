package com.koujunxin.account.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.account.api.port.SmsDispatchPort;

public final class RecordingSmsDispatchPort implements SmsDispatchPort {

    private final Map<String, String> lastByScenePhone = new ConcurrentHashMap<>();

    @Override
    public void dispatch(String scene, String phone, String code) {
        lastByScenePhone.put(scene + ":" + phone, code);
    }

    public String last(String scene, String phone) {
        return lastByScenePhone.get(scene + ":" + phone);
    }
}
