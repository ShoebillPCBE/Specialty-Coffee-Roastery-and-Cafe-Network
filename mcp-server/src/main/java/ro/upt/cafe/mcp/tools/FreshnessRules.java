package ro.upt.cafe.mcp.tools;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import ro.upt.cafe.mcp.config.CafeProperties;
import ro.upt.cafe.mcp.model.AtRiskBatch;
import ro.upt.cafe.mcp.model.AtRiskBatch.RiskType;
import ro.upt.cafe.mcp.model.AtRiskBatch.Severity;
import ro.upt.cafe.mcp.model.BatchView;

/**
 * Domain rules for "at-risk" stock:
 * coffee beans older than 21 days since roast, matcha tins open longer than 25 days,
 * milk expiring within the requested window (default 48h). Items that will cross a
 * threshold inside the window are reported too, so the agent can act before it happens.
 */
@Component
public class FreshnessRules {

    static final Set<String> COFFEE = Set.of("ESPRESSO_BEAN", "FILTER_BEAN", "DECAF_BEAN");
    static final Set<String> MILK = Set.of("DAIRY_MILK", "OAT_MILK", "SOY_MILK");
    static final String MATCHA = "MATCHA_TIN";

    private final CafeProperties.Freshness cfg;

    public FreshnessRules(CafeProperties props) {
        this.cfg = props.freshness();
    }

    public List<AtRiskBatch> evaluate(List<BatchView> batches, int windowHours, LocalDateTime now) {
        return batches.stream()
                .filter(b -> b.remainingQuantity() > 0)
                .map(b -> assess(b, windowHours, now))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(AtRiskBatch::severity)
                        .thenComparing(a -> a.hoursUntilStaleOrExpired() == null ? Long.MAX_VALUE : a.hoursUntilStaleOrExpired()))
                .toList();
    }

    Optional<AtRiskBatch> assess(BatchView b, int windowHours, LocalDateTime now) {
        String category = b.category() == null ? "" : b.category().toUpperCase(Locale.ROOT);
        if (COFFEE.contains(category)) {
            return ageRule(b, now, windowHours, cfg.coffeeStaleAfterDays(),
                    RiskType.STALE_COFFEE, RiskType.AGING_COFFEE, "roasted");
        }
        if (MATCHA.equals(category)) {
            return ageRule(b, now, windowHours, cfg.matchaOxidizedAfterDays(),
                    RiskType.OXIDIZING_MATCHA, RiskType.MATCHA_NEAR_OXIDATION, "opened");
        }
        return expiryRule(b, now, windowHours, MILK.contains(category));
    }

    private Optional<AtRiskBatch> ageRule(BatchView b, LocalDateTime now, int windowHours, int limitDays,
                                          RiskType pastType, RiskType soonType, String verb) {
        if (b.roastOrOpenDate() == null) {
            return Optional.empty();
        }
        long ageDays = ChronoUnit.DAYS.between(b.roastOrOpenDate(), now.toLocalDate());
        long hoursLeft = Duration.between(now, b.roastOrOpenDate().plusDays(limitDays).atStartOfDay()).toHours();
        if (ageDays > limitDays) {
            return Optional.of(risk(b, ageDays, hoursLeft, pastType, Severity.HIGH,
                    "%s %d days ago, past the %d-day freshness limit".formatted(verb, ageDays, limitDays)));
        }
        if (hoursLeft <= windowHours) {
            return Optional.of(risk(b, ageDays, hoursLeft, soonType, Severity.MEDIUM,
                    "%s %d days ago, crosses the %d-day limit in ~%dh".formatted(verb, ageDays, limitDays, hoursLeft)));
        }
        return Optional.empty();
    }

    private Optional<AtRiskBatch> expiryRule(BatchView b, LocalDateTime now, int windowHours, boolean milk) {
        LocalDate expiry = b.expiryDate();
        if (expiry == null) {
            return Optional.empty();
        }
        long hoursLeft = Duration.between(now, expiry.plusDays(1).atStartOfDay()).toHours();
        Long ageDays = b.roastOrOpenDate() == null ? null : ChronoUnit.DAYS.between(b.roastOrOpenDate(), now.toLocalDate());
        if (hoursLeft <= 0) {
            return Optional.of(risk(b, ageDays, hoursLeft, RiskType.EXPIRED, Severity.CRITICAL,
                    "expired on " + expiry + " - remove from service"));
        }
        if (hoursLeft <= windowHours) {
            Severity severity = hoursLeft <= 24 ? Severity.HIGH : Severity.MEDIUM;
            return Optional.of(risk(b, ageDays, hoursLeft, milk ? RiskType.MILK_EXPIRING : RiskType.EXPIRING, severity,
                    "expires %s (~%dh left)".formatted(expiry, hoursLeft)));
        }
        return Optional.empty();
    }

    private static AtRiskBatch risk(BatchView b, Long ageDays, long hoursLeft, RiskType type, Severity severity, String note) {
        return new AtRiskBatch(b.batchId(), b.name(), b.category(), b.remainingQuantity(), b.unit(),
                ageDays, hoursLeft, type, severity, note);
    }
}
