package com.koujunxin.billing.ledger;

public final class BalanceMath {

    private BalanceMath() {
    }

    public static Money available(Money ledgerNet, Money openHolds) {
        if (ledgerNet.micros() < openHolds.micros()) {
            return Money.ZERO;
        }
        return ledgerNet.minus(openHolds);
    }

    public static boolean covers(Money available, Money amount) {
        return available.micros() >= amount.micros();
    }
}
