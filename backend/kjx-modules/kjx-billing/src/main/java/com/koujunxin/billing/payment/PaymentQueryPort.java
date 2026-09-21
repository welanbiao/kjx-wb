package com.koujunxin.billing.payment;

import com.koujunxin.billing.ledger.Money;

public interface PaymentQueryPort {

    Money queryPaidAmount(String provider, String outTradeNo);
}
