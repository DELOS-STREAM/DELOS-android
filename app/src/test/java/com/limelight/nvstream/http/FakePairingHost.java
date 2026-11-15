package com.limelight.nvstream.http;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Test helper: the host side of the GameStream pairing handshake, written independently of
 * {@link PairingManager} (JCA AES and digests instead of the client's BouncyCastle engine), so
 * that the client is checked against a second implementation of the protocol and not against
 * itself. Answers the query strings the client hands to {@code NvHTTP.executePairingCommand}.
 */
final class FakePairingHost {
    final TestIdentity identity;
    /** The PIN the host's user typed. */
    String pin;
    /** "SHA-256" for generation 7+ hosts, "SHA-1" before. */
    String hashAlgorithm = "SHA-256";
    /** Signs the pairing secret with this key instead of the host's own (an impostor). */
    PrivateKey signingKeyOverride;
    /** Another client is already pairing: the first answer carries no certificate. */
    boolean omitCertificate;
    /** 1-based index of the pairing command that is answered with paired=0; 0 = never. */
    int refuseAtStep;
    /** Answers the final pair challenge with paired=0. */
    boolean refusePairChallenge;

    final List<String> requests = new ArrayList<>();
    /** True once the client proved it knows the PIN and owns its certificate's key. */
    boolean clientProved;
    boolean pairChallengeSeen;

    private byte[] aesKey;
    private X509Certificate clientCert;
    private final byte[] serverSecret = filled(16, 0x51);
    private final byte[] serverChallenge = filled(16, 0x33);
    private byte[] clientChallengeHash;
    private int step;

    FakePairingHost(TestIdentity identity, String pin) {
        this.identity = identity;
        this.pin = pin;
    }

    String onPairingCommand(String query) throws Exception {
        requests.add(query);
        step++;
        if (step == refuseAtStep) {
            return "<root status_code=\"200\"><paired>0</paired></root>";
        }
        Map<String, String> args = parse(query);
        if ("getservercert".equals(args.get("phrase"))) {
            byte[] salt = unhex(args.get("salt"));
            clientCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(unhex(args.get("clientcert"))));
            aesKey = Arrays.copyOf(hash(concat(salt, pin.getBytes(StandardCharsets.UTF_8))), 16);
            return ok(omitCertificate ? "" : "<plaincert>" + hex(identity.pem()) + "</plaincert>");
        }
        if (args.containsKey("clientchallenge")) {
            byte[] clientChallenge = Arrays.copyOf(aes(Cipher.DECRYPT_MODE, unhex(args.get("clientchallenge"))), 16);
            byte[] response = hash(concat(concat(clientChallenge, identity.cert.getSignature()), serverSecret));
            return ok("<challengeresponse>"
                    + hex(aes(Cipher.ENCRYPT_MODE, concat(response, serverChallenge)))
                    + "</challengeresponse>");
        }
        if (args.containsKey("serverchallengeresp")) {
            clientChallengeHash = Arrays.copyOf(
                    aes(Cipher.DECRYPT_MODE, unhex(args.get("serverchallengeresp"))), hashLength());
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(signingKeyOverride != null ? signingKeyOverride : identity.key);
            signature.update(serverSecret);
            return ok("<pairingsecret>" + hex(concat(serverSecret, signature.sign())) + "</pairingsecret>");
        }
        if (args.containsKey("clientpairingsecret")) {
            byte[] data = unhex(args.get("clientpairingsecret"));
            byte[] clientSecret = Arrays.copyOfRange(data, 0, 16);
            byte[] clientSignature = Arrays.copyOfRange(data, 16, data.length);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(clientCert.getPublicKey());
            signature.update(clientSecret);
            boolean signed = signature.verify(clientSignature);
            boolean knowsPin = Arrays.equals(clientChallengeHash,
                    hash(concat(concat(serverChallenge, clientCert.getSignature()), clientSecret)));
            clientProved = signed && knowsPin;
            return clientProved ? ok("") : "<root status_code=\"200\"><paired>0</paired></root>";
        }
        throw new AssertionError("unexpected pairing command: " + query);
    }

    String onPairChallenge() {
        pairChallengeSeen = true;
        return "<root status_code=\"200\"><paired>" + (refusePairChallenge ? "0" : "1") + "</paired></root>";
    }

    private static String ok(String body) {
        return "<root status_code=\"200\"><paired>1</paired>" + body + "</root>";
    }

    private int hashLength() {
        return "SHA-1".equals(hashAlgorithm) ? 20 : 32;
    }

    private byte[] hash(byte[] data) throws Exception {
        return MessageDigest.getInstance(hashAlgorithm).digest(data);
    }

    /** AES-128 ECB without padding; the input is zero-padded to a whole block like the real host does. */
    private byte[] aes(int mode, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(mode, new SecretKeySpec(aesKey, "AES"));
        return cipher.doFinal(Arrays.copyOf(data, (data.length + 15) / 16 * 16));
    }

    static Map<String, String> parse(String query) {
        Map<String, String> args = new HashMap<>();
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            args.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return args;
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02X", b));
        }
        return builder.toString();
    }

    static byte[] unhex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
