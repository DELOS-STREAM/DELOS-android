package com.limelight.nvstream.http;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

/**
 * Test helper: an RSA key with a self-signed certificate, the shape of both a GameStream host
 * identity and a client identity. Also usable as the client's {@link LimelightCryptoProvider}.
 */
public final class TestIdentity implements LimelightCryptoProvider {
    public final PrivateKey key;
    public final X509Certificate cert;

    private TestIdentity(PrivateKey key, X509Certificate cert) {
        this.key = key;
        this.cert = cert;
    }

    public static TestIdentity create(String commonName) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();

            X500Name name = new X500Name("CN=" + commonName);
            // Fixed validity window around every plausible test date
            Date from = new Date(1577836800000L);   // 2020-01-01
            Date to = new Date(4102444800000L);     // 2100-01-01
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name, BigInteger.valueOf(System.nanoTime()), from, to, name, pair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
            return new TestIdentity(pair.getPrivate(), cert);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** The certificate in OpenSSL PEM form with UNIX line endings, as hosts and clients exchange it. */
    public byte[] pem() {
        try {
            String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(cert.getEncoded());
            return ("-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n")
                    .getBytes(StandardCharsets.US_ASCII);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override public X509Certificate getClientCertificate() { return cert; }
    @Override public PrivateKey getClientPrivateKey() { return key; }
    @Override public byte[] getPemEncodedClientCertificate() { return pem(); }
    @Override public String encodeBase64String(byte[] data) { return Base64.getEncoder().encodeToString(data); }
}
