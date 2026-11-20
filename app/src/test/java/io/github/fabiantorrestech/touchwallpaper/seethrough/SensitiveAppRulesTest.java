package io.github.fabiantorrestech.touchwallpaper.seethrough;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.github.fabiantorrestech.touchwallpaper.seethrough.SensitiveAppRules.Reason;
import org.junit.Test;

public class SensitiveAppRulesTest {
    private static Reason reason(String packageName, String label) {
        return SensitiveAppRules.reasonFor(packageName, label, null, false);
    }

    @Test
    public void banksAndPayments() {
        assertEquals(Reason.MONEY, reason("com.chase.sig.android", "Chase"));
        assertEquals(Reason.MONEY, reason("com.citi.citimobile", "Citi Mobile"));
        assertEquals(Reason.MONEY, reason("com.paypal.android.p2pmobile", "PayPal"));
        assertEquals(Reason.MONEY, reason("com.google.android.apps.walletnfcrel", "Google Wallet"));
        assertEquals(Reason.MONEY, reason("com.example.mybank", "My Bank"));
        assertEquals(Reason.MONEY, reason("com.squareup.cash", "Cash App"));
    }

    @Test
    public void messagesAndEmail() {
        assertEquals(Reason.MESSAGES, reason("com.whatsapp", "WhatsApp"));
        assertEquals(Reason.MESSAGES, reason("org.thoughtcrime.securesms", "Signal"));
        assertEquals(Reason.MESSAGES, reason("com.google.android.apps.messaging", "Messages"));
        assertEquals(Reason.MESSAGES, reason("com.facebook.orca", "Messenger"));
        assertEquals(Reason.EMAIL, reason("com.google.android.gm", "Gmail"));
        assertEquals(Reason.EMAIL, reason("com.fsck.k9", "K-9 Mail"));
    }

    @Test
    public void passwordsAndCodes() {
        assertEquals(Reason.PASSWORDS, reason("com.x8bit.bitwarden", "Bitwarden"));
        assertEquals(Reason.PASSWORDS, reason("com.agilebits.onepassword", "1Password"));
        assertEquals(Reason.PASSWORDS, reason("com.google.android.apps.authenticator2", "Authenticator"));
    }

    @Test
    public void ordinaryAppsAreNotSuggested() {
        assertNull(reason("com.android.chrome", "Chrome"));
        assertNull(reason("org.koreader.launcher", "KOReader"));
        assertNull(reason("com.example.purchase", "Purchase Tracker"));
        assertNull(reason("com.example.cities", "Cities"));
        assertNull(reason("com.android.vending", "Play Store"));
        assertNull(reason("com.example.display", "Display Tester"));
    }

    @Test
    public void systemRolesAndSocialCategoryCount() {
        assertEquals(Reason.MESSAGES, SensitiveAppRules.reasonFor("com.example.dialer", "Phone", Reason.MESSAGES, false));
        assertEquals(Reason.SOCIAL, SensitiveAppRules.reasonFor("com.example.friends", "Friends", null, true));
    }
}
