package com.limelight.nvstream.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The pin against certificates that a certificate authority issued: a trust store that really validates
 * chains, and hosts that present a chain of two certificates. The pin is compared with the leaf only.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PinnedCertificateChainTest {
    private static final String SERVER_INFO =
            "<root status_code=\"200\"><hostname>Workstation</hostname><appversion>7.1.431.-1</appversion>"
            + "<uniqueid>ABC-1</uniqueid><HttpsPort>47984</HttpsPort><ExternalPort>47989</ExternalPort>"
            + "<mac>AA:BB:CC:DD:EE:FF</mac><LocalIP>192.168.1.10</LocalIP>"
            + "<PairStatus>1</PairStatus><currentgame>0</currentgame>"
            + "<state>SUNSHINE_SERVER_FREE</state></root>";

    private static Issued ca;
    private static Issued paired;
    private static Issued impostor;
    private static TestIdentity client;
    private static X509TrustManager trustsTheCa;

    private final List<ServerSocket> servers = new ArrayList<>();

    @BeforeClass
    public static void identities() throws Exception {
        ca = new Issued("Test CA", null);
        // Both hosts hold a certificate of the same authority; only one of them was paired
        paired = new Issued("pc.example.org", ca);
        impostor = new Issued("pc.example.org", ca);
        client = TestIdentity.create("client");

        KeyStore roots = KeyStore.getInstance(KeyStore.getDefaultType());
        roots.load(null, null);
        roots.setCertificateEntry("ca", ca.cert);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(roots);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                trustsTheCa = (X509TrustManager) manager;
            }
        }
    }

    @After
    public void stopServers() throws IOException {
        for (ServerSocket server : servers) {
            server.close();
        }
    }

    @Test
    public void theTrustStoreOfThisTestAcceptsWhatTheCaIssuedAndNothingElse() throws Exception {
        // Without a pin the trust store decides: this proves the cases below are about the pin
        NvHTTP.checkServerCertificate(impostor.chain(), "RSA", null, trustsTheCa);
        X509Certificate selfSigned = TestIdentity.create("pc.example.org").cert;
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[] {selfSigned}, "RSA", null, trustsTheCa));
    }

    @Test
    public void aCaTrustedCertificateThatDiffersFromThePinIsRejected() {
        // Same authority, same name, valid chain: still not the host that was paired
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(impostor.chain(), "RSA", paired.cert, trustsTheCa));
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[] {impostor.cert}, "RSA", paired.cert, trustsTheCa));
        // The authority's own certificate is not the host either
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[] {ca.cert}, "RSA", paired.cert, trustsTheCa));
    }

    @Test
    public void aChainOfTwoCertificatesWithThePinnedLeafIsAccepted() throws Exception {
        NvHTTP.checkServerCertificate(paired.chain(), "RSA", paired.cert, trustsTheCa);
        // The pin alone decides: the trust store is not asked
        NvHTTP.checkServerCertificate(paired.chain(), "RSA", paired.cert, rejectingAll());
    }

    @Test
    public void thePinIsTheLeafNotAnotherCertificateOfTheChain() {
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(paired.chain(), "RSA", ca.cert, trustsTheCa));
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[] {ca.cert, paired.cert}, "RSA", paired.cert, trustsTheCa));
    }

    @Test
    public void aHostThatPresentsATwoCertificateChainIsReadOverHttpsWithItsPinnedLeaf() throws Exception {
        int https = serve(paired, SERVER_INFO);
        int http = serve(null, SERVER_INFO.replace("Workstation", "Forged"));
        NvHTTP nv = new NvHTTP(address(http), https, "client", paired.cert, client);

        ComputerDetails details = nv.getComputerDetails(true);

        assertEquals("Workstation", details.name);
        assertFalse(details.identityUnverified);
        assertEquals(PairingManager.PairState.PAIRED, details.pairState);
    }

    @Test
    public void aHostWithAnotherCertificateOfTheSameCaIsNotThePairedHost() throws Exception {
        int https = serve(impostor, SERVER_INFO);
        int http = serve(null, SERVER_INFO);
        NvHTTP nv = new NvHTTP(address(http), https, "client", paired.cert, client);

        // Nothing is read over HTTPS; what plain HTTP says is marked as not authenticated
        assertTrue(nv.fetchServerInfo(true).unverified);
        assertTrue(nv.getComputerDetails(true).identityUnverified);
    }

    // ---- helpers ----

    private static ComputerDetails.AddressTuple address(int port) {
        return new ComputerDetails.AddressTuple("127.0.0.1", port);
    }

    /** Answers every request with {@code body}; over TLS with {@code identity}'s chain when given. */
    private int serve(Issued identity, String body) throws Exception {
        ServerSocket socket = identity == null
                ? new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
                : identity.tls().getServerSocketFactory().createServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        servers.add(socket);
        Thread thread = new Thread(() -> {
            while (!socket.isClosed()) {
                try (Socket peer = socket.accept()) {
                    InputStream in = peer.getInputStream();
                    StringBuilder request = new StringBuilder();
                    int c;
                    while (request.indexOf("\r\n\r\n") < 0 && (c = in.read()) != -1) {
                        request.append((char) c);
                    }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    OutputStream out = peer.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: " + bytes.length
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes);
                    out.flush();
                } catch (IOException ignored) {
                    // A refused handshake or the closed server socket
                }
            }
        }, "test host");
        thread.setDaemon(true);
        thread.start();
        return socket.getLocalPort();
    }

    private static X509TrustManager rejectingAll() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("untrusted");
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("untrusted");
            }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }

    /** A key with a certificate: self-signed and able to issue when {@code issuer} is null, else issued by it. */
    private static final class Issued {
        final X509Certificate cert;
        final PrivateKey key;
        private final Issued issuer;

        Issued(String name, Issued issuer) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            X500Name subject = new X500Name("CN=" + name);
            // Fixed validity window around every plausible test date
            Date from = new Date(1577836800000L);   // 2020-01-01
            Date to = new Date(4102444800000L);     // 2100-01-01
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer == null ? subject : new X500Name(issuer.cert.getSubjectX500Principal().getName()),
                    new BigInteger(64, new java.security.SecureRandom()), from, to, subject, pair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(issuer == null));
            PrivateKey signingKey = issuer == null ? pair.getPrivate() : issuer.key;
            cert = new JcaX509CertificateConverter().getCertificate(
                    builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signingKey)));
            key = pair.getPrivate();
            this.issuer = issuer;
        }

        /** What a TLS server sends: the leaf first, then its issuer. */
        X509Certificate[] chain() {
            return issuer == null ? new X509Certificate[] {cert} : new X509Certificate[] {cert, issuer.cert};
        }

        SSLContext tls() throws Exception {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            store.setKeyEntry("host", key, new char[0], chain());
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, new char[0]);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            return context;
        }
    }
}
