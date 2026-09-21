package com.koujunxin.billing.charge;

import com.koujunxin.billing.api.BillingActor;
import com.koujunxin.billing.api.BillingException;
import com.koujunxin.billing.api.FreezeCommand;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.api.ReleaseCommand;
import com.koujunxin.billing.api.SettleCommand;
import com.koujunxin.billing.ledger.CanonicalHash;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.channel.api.ChannelMode;
import com.koujunxin.channel.api.ChannelPreference;
import com.koujunxin.channel.api.ChannelResolver;
import com.koujunxin.channel.api.DnsLookup;
import com.koujunxin.channel.api.ResolvedChannel;
import com.koujunxin.channel.api.SsrfGuard;

/**
 * BYOK 失败只抛错，不调用平台冻结。平台通道先冻结，上游失败则释放。
 */
public final class ChargeCoordinator {

    private final ChannelResolver resolver;
    private final SsrfGuard ssrfGuard;
    private final PlatformBilling billing;
    private final UpstreamCall upstream;

    public ChargeCoordinator(ChannelResolver resolver, DnsLookup lookup, PlatformBilling billing, UpstreamCall upstream) {
        this.resolver = resolver;
        this.ssrfGuard = new SsrfGuard(lookup);
        this.billing = billing;
        this.upstream = upstream;
    }

    public ChargeResult charge(ChargeRequest request) {
        ResolvedChannel channel = resolver.resolve(request.preference(), request.credential());
        if (channel.mode() == ChannelMode.BYOK) {
            ssrfGuard.check(channel.credential().endpoint());
            try {
                upstream.invoke(channel);
            } catch (RuntimeException ex) {
                throw BillingException.byokFailed();
            }
            return ChargeResult.byok();
        }
        String hash = CanonicalHash.sha256(request.idempotencyKey() + "|freeze|" + request.estimate().decimal().toPlainString());
        long holdId = billing.freeze(new FreezeCommand(
                request.memberId(),
                request.estimate(),
                request.idempotencyKey(),
                hash,
                request.reason(),
                request.workId(),
                request.actor()));
        try {
            UpstreamCall.Usage usage = upstream.invoke(channel);
            String settleKey = "settle:" + request.idempotencyKey();
            String settleHash = CanonicalHash.sha256(settleKey + "|" + usage.cost().decimal().toPlainString());
            long ledgerId = billing.settle(new SettleCommand(
                    holdId,
                    usage.cost(),
                    settleKey,
                    settleHash,
                    usage.inputTokens(),
                    usage.outputTokens(),
                    usage.model(),
                    request.actor()));
            return ChargeResult.platform(ledgerId);
        } catch (RuntimeException ex) {
            billing.release(new ReleaseCommand(holdId, "upstream-failed", request.actor()));
            if (ex instanceof BillingException billingException) {
                throw billingException;
            }
            throw BillingException.rejected("平台调用失败，冻结已释放");
        }
    }

    public record ChargeRequest(
            long memberId,
            Long workId,
            Money estimate,
            String idempotencyKey,
            String reason,
            ChannelPreference preference,
            com.koujunxin.channel.api.ByokCredential credential,
            BillingActor actor) {
    }

    public record ChargeResult(ChannelMode mode, Long ledgerId) {
        static ChargeResult byok() {
            return new ChargeResult(ChannelMode.BYOK, null);
        }

        static ChargeResult platform(long ledgerId) {
            return new ChargeResult(ChannelMode.PLATFORM, ledgerId);
        }
    }
}
