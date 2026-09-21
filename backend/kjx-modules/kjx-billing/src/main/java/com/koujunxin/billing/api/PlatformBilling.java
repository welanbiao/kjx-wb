package com.koujunxin.billing.api;

public interface PlatformBilling {

    long freeze(FreezeCommand command);

    long settle(SettleCommand command);

    void release(ReleaseCommand command);
}
