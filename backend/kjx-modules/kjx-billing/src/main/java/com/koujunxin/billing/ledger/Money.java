package com.koujunxin.billing.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(long micros) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (micros < 0) {
            throw new IllegalArgumentException("amount rejected");
        }
    }

    public static Money of(String decimal) {
        return of(new BigDecimal(decimal));
    }

    public static Money of(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("amount rejected");
        }
        try {
            return new Money(value.setScale(6, RoundingMode.UNNECESSARY).movePointRight(6).longValueExact());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("amount rejected");
        }
    }

    public BigDecimal decimal() {
        return BigDecimal.valueOf(micros, 6);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(micros, other.micros));
    }

    public Money minus(Money other) {
        if (micros < other.micros) {
            throw new IllegalArgumentException("amount rejected");
        }
        return new Money(micros - other.micros);
    }

    public int compareTo(Money other) {
        return Long.compare(micros, other.micros);
    }
}
