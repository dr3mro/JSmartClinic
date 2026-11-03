package org.amrosman.jsmartclinic.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashing helper for document deduplication. */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
