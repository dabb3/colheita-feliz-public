package br.com.colheitafeliz.game;

import java.math.BigDecimal;

/** A protected attempt spends the online cooldown even when no gold is lost. */
public record TheftAttempt(ActionResult result, Kind kind, BigDecimal goldDebited) {
    public enum Kind { STOLEN, PROTECTED, BITTEN, BLOCKED }
    public static TheftAttempt blocked(String message) {
        return new TheftAttempt(ActionResult.fail(message),Kind.BLOCKED,BigDecimal.ZERO);
    }
    public static TheftAttempt from(ActionResult result) {
        return new TheftAttempt(result,result.success()?Kind.STOLEN:Kind.BLOCKED,BigDecimal.ZERO);
    }
    public boolean consumesCooldown() { return kind!=Kind.BLOCKED; }
}
