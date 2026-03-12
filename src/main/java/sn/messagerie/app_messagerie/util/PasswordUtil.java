package sn.messagerie.app_messagerie.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordUtil {

    private static final int COST = 12;

    public static String hash(String plainPassword) {
        return BCrypt.withDefaults().hashToString(COST, plainPassword.toCharArray());
    }

    public static boolean verify(String plainPassword, String hashedPassword) {
        BCrypt.Result result = BCrypt.verifyer().verify(
                plainPassword.toCharArray(),
                hashedPassword
        );
        return result.verified;
    }
}
