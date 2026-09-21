package com.koujunxin.billing.charge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.koujunxin.billing.api.BillingActor;
import com.koujunxin.billing.api.BillingException;
import com.koujunxin.billing.api.FreezeCommand;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.api.ReleaseCommand;
import com.koujunxin.billing.api.SettleCommand;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.channel.api.ByokCredential;
import com.koujunxin.channel.api.ChannelMode;
import com.koujunxin.channel.api.ChannelPreference;
import com.koujunxin.channel.api.ChannelResolver;
import com.koujunxin.channel.api.SecretRef;

class ChargeCoordinatorTest {

    private final AtomicInteger freezes = new AtomicInteger();
    private final AtomicInteger releases = new AtomicInteger();

    @Test
    void byokFailureDoesNotFreezePlatformBalance() {
        ChargeCoordinator coordinator = coordinator("203.0.113.10", channel -> {
            throw new IllegalStateException("upstream down");
        });
        BillingException error = assertThrows(BillingException.class, () -> coordinator.charge(byokRequest()));
        assertEquals("BYOK_FAILED", error.name());
        assertEquals(0, freezes.get());
    }

    @Test
    void privateEndpointIsRejectedBeforeAnyFreeze() {
        ChargeCoordinator coordinator = coordinator("10.0.0.8", channel -> {
            throw new IllegalStateException("must not call");
        });
        assertThrows(IllegalArgumentException.class, () -> coordinator.charge(byokRequest()));
        assertEquals(0, freezes.get());
    }

    @Test
    void platformUpstreamFailureReleasesHold() {
        ChargeCoordinator coordinator = coordinator("203.0.113.10", channel -> {
            throw new IllegalStateException("platform down");
        });
        assertThrows(BillingException.class, () -> coordinator.charge(platformRequest()));
        assertEquals(1, freezes.get());
        assertEquals(1, releases.get());
    }

    private ChargeCoordinator coordinator(String address, UpstreamCall upstream) {
        PlatformBilling billing = new PlatformBilling() {
            @Override
            public long freeze(FreezeCommand command) {
                freezes.incrementAndGet();
                return 7L;
            }

            @Override
            public long settle(SettleCommand command) {
                return 8L;
            }

            @Override
            public void release(ReleaseCommand command) {
                releases.incrementAndGet();
            }
        };
        return new ChargeCoordinator(new ChannelResolver(), host -> List.of(InetAddress.getByName(address)), billing, upstream);
    }

    private ChargeCoordinator.ChargeRequest byokRequest() {
        return new ChargeCoordinator.ChargeRequest(
                3L,
                9L,
                Money.of("1.000000"),
                "idem-byok",
                "byok",
                new ChannelPreference(ChannelMode.BYOK, null, ChannelMode.PLATFORM),
                new ByokCredential(new SecretRef("ref:member.3"), "https://api.example.com/v1"),
                new BillingActor(11L, false, "trace", "request"));
    }

    private ChargeCoordinator.ChargeRequest platformRequest() {
        return new ChargeCoordinator.ChargeRequest(
                3L,
                9L,
                Money.of("1.000000"),
                "idem-platform",
                "platform",
                new ChannelPreference(null, null, ChannelMode.PLATFORM),
                null,
                new BillingActor(11L, false, "trace", "request"));
    }
}
