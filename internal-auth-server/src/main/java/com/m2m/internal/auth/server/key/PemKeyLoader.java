package com.m2m.internal.auth.server.key;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PemKeyLoader {
    private PemKeyLoader() {}

    public static KeyPair loadRsaKeyPair(String publicPem, String privatePem) {
        PublicKey pub = loadPublicKey(publicPem);
        PrivateKey priv = loadPrivateKey(privatePem);
        return new KeyPair(pub, priv);
    }

    public static KeyPair generateRsaKeyPair(int bits) {
        try {
            KeyPairGenerator kgp = KeyPairGenerator.getInstance("RSA");
            kgp.initialize(bits);
            return kgp.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    public static PrivateKey loadPrivateKey(String pem) {
        try {
            String privateKeyContent = stripPemHeaders(pem, "PRIVATE KEY");
            byte[] der = Base64.getDecoder().decode(privateKeyContent.getBytes(StandardCharsets.US_ASCII));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse private key from PEM", e);
        }
    }

    public static PublicKey loadPublicKey(String pem) {
        try {
            String publicKeyContent = stripPemHeaders(pem, "PUBLIC KEY");
            byte[] der = Base64.getDecoder().decode(publicKeyContent.getBytes(StandardCharsets.US_ASCII));
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to parse public key from PEM", e);
        }
    }

    private static String stripPemHeaders(String pem, String type) {
        return pem
            .replace("-----BEGIN " + type + "-----", "")
            .replace("-----END " + type + "-----", "")
            .replaceAll("\\s+", "");
    }
}
