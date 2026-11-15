package com.limelight.binding.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

/** The client identity every paired host knows the app by: losing it unpairs all hosts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AndroidCryptoProviderTest {
    private Context context;
    private File certFile;
    private File keyFile;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        certFile = new File(context.getFilesDir(), "client.crt");
        keyFile = new File(context.getFilesDir(), "client.key");
        certFile.delete();
        keyFile.delete();
    }

    private static void assertKeyMatchesCertificate(PrivateKey key, X509Certificate cert) throws Exception {
        byte[] message = "delos".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(message);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(cert.getPublicKey());
        verifier.update(message);
        assertTrue("the private key does not belong to the certificate", verifier.verify(signer.sign()));
    }

    @Test
    public void aFreshInstallGeneratesAUsableIdentity() throws Exception {
        AndroidCryptoProvider provider = new AndroidCryptoProvider(context);

        X509Certificate cert = provider.getClientCertificate();
        PrivateKey key = provider.getClientPrivateKey();

        assertNotNull(cert);
        assertNotNull(key);
        assertKeyMatchesCertificate(key, cert);
        assertEquals(2048, ((RSAPublicKey) cert.getPublicKey()).getModulus().bitLength());
        // Self-signed and valid today and in ten years (hosts keep the pairing that long).
        cert.verify(cert.getPublicKey());
        cert.checkValidity(new Date());
        cert.checkValidity(new Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000));
        assertTrue(certFile.length() > 0);
        assertTrue(keyFile.length() > 0);
    }

    @Test
    public void generatesOnceAndReloadsTheSameIdentity() throws Exception {
        AndroidCryptoProvider first = new AndroidCryptoProvider(context);
        X509Certificate cert = first.getClientCertificate();
        PrivateKey key = first.getClientPrivateKey();
        byte[] pem = first.getPemEncodedClientCertificate();

        // A new process: nothing cached in memory, only the two files.
        AndroidCryptoProvider second = new AndroidCryptoProvider(context);

        assertArrayEquals(cert.getEncoded(), second.getClientCertificate().getEncoded());
        assertArrayEquals(key.getEncoded(), second.getClientPrivateKey().getEncoded());
        assertArrayEquals(pem, second.getPemEncodedClientCertificate());
    }

    @Test
    public void askingForTheKeyFirstGivesTheSameIdentity() throws Exception {
        AndroidCryptoProvider provider = new AndroidCryptoProvider(context);

        PrivateKey key = provider.getClientPrivateKey();

        assertKeyMatchesCertificate(key, provider.getClientCertificate());
    }

    @Test
    public void pemIsParseableX509WithUnixLineEndings() throws Exception {
        AndroidCryptoProvider provider = new AndroidCryptoProvider(context);

        byte[] pem = provider.getPemEncodedClientCertificate();

        String text = new String(pem, StandardCharsets.US_ASCII);
        assertTrue(text.startsWith("-----BEGIN CERTIFICATE-----\n"));
        assertFalse("hosts reject CRLF certificates", text.contains("\r"));
        X509Certificate parsed = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem));
        assertArrayEquals(provider.getClientCertificate().getEncoded(), parsed.getEncoded());
        // What is sent to the host is exactly what is stored.
        assertArrayEquals(Files.readAllBytes(certFile.toPath()), pem);
    }

    @Test
    public void aCorruptCertificateFileRegenerates() throws Exception {
        X509Certificate original = new AndroidCryptoProvider(context).getClientCertificate();
        Files.write(certFile.toPath(), "not a certificate".getBytes(StandardCharsets.US_ASCII));

        AndroidCryptoProvider provider = new AndroidCryptoProvider(context);
        X509Certificate cert = provider.getClientCertificate();

        assertNotNull(cert);
        assertNotEquals(original, cert);
        assertKeyMatchesCertificate(provider.getClientPrivateKey(), cert);
    }

    @Test
    public void aCorruptKeyFileRegenerates() throws Exception {
        X509Certificate original = new AndroidCryptoProvider(context).getClientCertificate();
        Files.write(keyFile.toPath(), new byte[]{1, 2, 3, 4});

        AndroidCryptoProvider provider = new AndroidCryptoProvider(context);
        PrivateKey key = provider.getClientPrivateKey();

        assertNotNull(key);
        // A certificate without its key is useless: both are replaced together.
        assertNotEquals(original, provider.getClientCertificate());
        assertKeyMatchesCertificate(key, provider.getClientCertificate());
    }

    @Test
    public void aMissingKeyFileRegeneratesBoth() throws Exception {
        X509Certificate original = new AndroidCryptoProvider(context).getClientCertificate();
        assertTrue(keyFile.delete());

        AndroidCryptoProvider provider = new AndroidCryptoProvider(context);

        assertNotEquals(original, provider.getClientCertificate());
        assertKeyMatchesCertificate(provider.getClientPrivateKey(), provider.getClientCertificate());
    }

    @Test
    public void theRegeneratedIdentityIsStoredAgain() throws Exception {
        new AndroidCryptoProvider(context).getClientCertificate();
        Files.write(keyFile.toPath(), new byte[]{1, 2, 3, 4});
        X509Certificate regenerated = new AndroidCryptoProvider(context).getClientCertificate();

        X509Certificate reloaded = new AndroidCryptoProvider(context).getClientCertificate();

        assertArrayEquals(regenerated.getEncoded(), reloaded.getEncoded());
    }

    @Test
    public void base64IsUnwrapped() {
        byte[] data = new byte[200];
        String encoded = new AndroidCryptoProvider(context).encodeBase64String(data);

        assertFalse(encoded.contains("\n"));
        assertArrayEquals(data, java.util.Base64.getDecoder().decode(encoded));
    }
}
