package io.github.fabiantorrestech.touchwallpaper.seethrough;

import java.util.Locale;

/**
 * Suggests apps to hide from the see-through lock screen. Only a suggestion: every app is shown
 * unless the user hides it. Pure Java, so it's unit tested without a device.
 */
public final class SensitiveAppRules {
    public enum Reason {
        MONEY,
        MESSAGES,
        EMAIL,
        PASSWORDS,
        SOCIAL
    }

    private enum Match {
        /** The whole word, like "citi", which mustn't catch "cities". */
        WHOLE,
        /** The start of a word, like "messag" in "messaging". */
        START,
        /** The start or end of a word, like "pay" in "gpay" or "sms" in "securesms". */
        START_OR_END
    }

    private static final class Rule {
        final String keyword;
        final Reason reason;
        final Match match;

        Rule(String keyword, Reason reason, Match match) {
            this.keyword = keyword;
            this.reason = reason;
            this.match = match;
        }

        boolean matches(String word) {
            switch (match) {
                case WHOLE:
                    return word.equals(keyword);
                case START:
                    return word.startsWith(keyword);
                default:
                    return word.startsWith(keyword) || word.endsWith(keyword);
            }
        }
    }

    // Matched against words, not substrings, so "chase" doesn't catch "purchase".
    private static final Rule[] RULES = {
            new Rule("bank", Reason.MONEY, Match.START_OR_END),
            new Rule("pay", Reason.MONEY, Match.START_OR_END),
            new Rule("wallet", Reason.MONEY, Match.START_OR_END),
            new Rule("cash", Reason.MONEY, Match.START),
            new Rule("money", Reason.MONEY, Match.START_OR_END),
            new Rule("venmo", Reason.MONEY, Match.START),
            new Rule("zelle", Reason.MONEY, Match.START),
            new Rule("chase", Reason.MONEY, Match.START),
            new Rule("wellsfargo", Reason.MONEY, Match.START),
            new Rule("citi", Reason.MONEY, Match.WHOLE),
            new Rule("capitalone", Reason.MONEY, Match.START),
            new Rule("amex", Reason.MONEY, Match.WHOLE),
            new Rule("schwab", Reason.MONEY, Match.START),
            new Rule("fidelity", Reason.MONEY, Match.START),
            new Rule("vanguard", Reason.MONEY, Match.START),
            new Rule("robinhood", Reason.MONEY, Match.START),
            new Rule("coinbase", Reason.MONEY, Match.START),
            new Rule("crypto", Reason.MONEY, Match.START),
            new Rule("financ", Reason.MONEY, Match.START),
            new Rule("credit", Reason.MONEY, Match.START),
            new Rule("invest", Reason.MONEY, Match.START),
            new Rule("revolut", Reason.MONEY, Match.START),
            new Rule("sms", Reason.MESSAGES, Match.START_OR_END),
            new Rule("mms", Reason.MESSAGES, Match.WHOLE),
            new Rule("messag", Reason.MESSAGES, Match.START),
            new Rule("messenger", Reason.MESSAGES, Match.START_OR_END),
            new Rule("chat", Reason.MESSAGES, Match.START_OR_END),
            new Rule("whatsapp", Reason.MESSAGES, Match.START),
            new Rule("telegram", Reason.MESSAGES, Match.START),
            new Rule("signal", Reason.MESSAGES, Match.WHOLE),
            new Rule("viber", Reason.MESSAGES, Match.START),
            new Rule("discord", Reason.MESSAGES, Match.START),
            new Rule("slack", Reason.MESSAGES, Match.START),
            new Rule("teams", Reason.MESSAGES, Match.WHOLE),
            new Rule("mail", Reason.EMAIL, Match.START_OR_END),
            new Rule("outlook", Reason.EMAIL, Match.START),
            new Rule("k9", Reason.EMAIL, Match.WHOLE),
            new Rule("password", Reason.PASSWORDS, Match.START_OR_END),
            new Rule("bitwarden", Reason.PASSWORDS, Match.START),
            new Rule("lastpass", Reason.PASSWORDS, Match.START),
            new Rule("keepass", Reason.PASSWORDS, Match.START),
            new Rule("dashlane", Reason.PASSWORDS, Match.START),
            new Rule("authenticator", Reason.PASSWORDS, Match.START),
            new Rule("authy", Reason.PASSWORDS, Match.WHOLE),
            new Rule("aegis", Reason.PASSWORDS, Match.WHOLE),
            new Rule("vault", Reason.PASSWORDS, Match.START_OR_END),
            new Rule("otp", Reason.PASSWORDS, Match.WHOLE),
            new Rule("2fa", Reason.PASSWORDS, Match.START),
    };

    private SensitiveAppRules() {
    }

    /**
     * Why an app might show private things, or null if nothing suggests it.
     *
     * @param role a reason known from the system, such as being the default SMS app, or null
     * @param social whether the app declares itself a social app
     */
    public static Reason reasonFor(String packageName, String label, Reason role, boolean social) {
        if (role != null) {
            return role;
        }
        Reason fromWords = matchWords(packageName);
        if (fromWords == null && label != null) {
            fromWords = matchWords(label);
        }
        if (fromWords != null) {
            return fromWords;
        }
        return social ? Reason.SOCIAL : null;
    }

    private static Reason matchWords(String text) {
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.isEmpty()) {
                continue;
            }
            for (Rule rule : RULES) {
                if (rule.matches(word)) {
                    return rule.reason;
                }
            }
        }
        return null;
    }
}
