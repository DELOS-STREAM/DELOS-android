package com.limelight.nvstream.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParserException;

import java.io.Closeable;
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
import javax.net.ssl.X509TrustManager;

/**
 * Who a paired PC is (S-06, S-07): the pinned certificate alone decides, a reply that could not be
 * authenticated changes nothing that is kept about the PC, and first contact still works over HTTP.
 * The hosts are real local HTTP and TLS servers.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PairedHostTrustTest {
    private static final String REAL = serverInfo("Workstation", "1", "AA:BB:CC:DD:EE:FF", "192.168.1.10");
    private static final String FORGED = serverInfo("Forged", "1", "11:22:33:44:55:66", "10.9.9.9");
    private static final String UNAUTHORIZED = "<root status_code=\"401\" status_message=\"Unauthorized\"/>";

    private static Identity host;
    private static Identity other;
    private static Identity client;

    private final List<Closeable> servers = new ArrayList<>();

    @BeforeClass
    public static void identities() throws Exception {
        host = new Identity("host");
        other = new Identity("other");
        client = new Identity("client");
    }

    @After
    public void stopServers() throws IOException {
        for (Closeable server : servers) {
            server.close();
        }
    }

    // ---- S-07: the pin is exclusive ----

    @Test
    public void aPinnedCertificateIsAcceptedWithoutAskingTheTrustStore() throws Exception {
        NvHTTP.checkServerCertificate(new X509Certificate[] {host.cert}, "RSA", host.cert, rejectingAll());
    }

    @Test
    public void aDifferentCertificateIsAMismatchEvenWhenACaVouchesForIt() {
        // The trust store accepts everything here, as it would for a certificate issued by a public CA
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[] {other.cert}, "RSA", host.cert, acceptingAll()));
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[] {other.cert, host.cert}, "RSA", host.cert, acceptingAll()));
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[0], "RSA", host.cert, acceptingAll()));
    }

    @Test
    public void withoutAPinTheTrustStoreDecides() throws Exception {
        NvHTTP.checkServerCertificate(new X509Certificate[] {other.cert}, "RSA", null, acceptingAll());
        assertThrows(CertificateException.class, () ->
                NvHTTP.checkServerCertificate(new X509Certificate[] {other.cert}, "RSA", null, rejectingAll()));
    }

    // ---- S-06: what an HTTP reply may do ----

    @Test
    public void firstContactReadsServerInfoOverHttpAsBefore() throws Exception {
        int http = serve(null, FORGED.replace("Forged", "New PC"));
        NvHTTP nv = new NvHTTP(address(http), 0, "client", null, client);

        NvHTTP.ServerInfo info = nv.fetchServerInfo(true);
        assertFalse(info.unverified);
        ComputerDetails details = nv.getComputerDetails(true);
        assertEquals("New PC", details.name);
        assertFalse(details.identityUnverified);
    }

    @Test
    public void aPairedHostIsReadOverHttpsWithItsPin() throws Exception {
        int https = serve(host, REAL);
        int http = serve(null, FORGED);
        NvHTTP nv = new NvHTTP(address(http), https, "client", host.cert, client);

        ComputerDetails details = nv.getComputerDetails(true);
        assertEquals("Workstation", details.name);
        assertFalse(details.identityUnverified);
        assertEquals(PairingManager.PairState.PAIRED, details.pairState);
    }

    @Test
    public void aCertificateMismatchStillReachesTheHostOverHttpButMarksTheReply() throws Exception {
        int https = serve(other, FORGED);
        int http = serve(null, FORGED);
        NvHTTP nv = new NvHTTP(address(http), https, "client", host.cert, client);

        NvHTTP.ServerInfo info = nv.fetchServerInfo(true);
        assertTrue(info.unverified);
        assertEquals(FORGED, info.xml);
        // The pairing flow reads the same reply through the old entry point
        assertEquals(FORGED, nv.getServerInfo(true));
        assertTrue(nv.getComputerDetails(true).identityUnverified);
    }

    @Test
    public void aHostThatForgotThisClientIsReadOverHttpAndMarked() throws Exception {
        int https = serve(host, UNAUTHORIZED);
        int http = serve(null, REAL.replace("<PairStatus>1", "<PairStatus>0"));
        NvHTTP nv = new NvHTTP(address(http), https, "client", host.cert, client);

        ComputerDetails details = nv.getComputerDetails(true);
        assertTrue(details.identityUnverified);
        assertEquals(PairingManager.PairState.NOT_PAIRED, details.pairState);
    }

    @Test
    public void anUnverifiedReplyOnlyTellsAPairedPcToPairAgain() throws Exception {
        ComputerDetails stored = new ComputerDetails();
        stored.uuid = "ABC-1";
        stored.name = "Workstation";
        stored.serverCert = host.cert;
        stored.localAddress = new ComputerDetails.AddressTuple("192.168.1.10", 47989);
        stored.remoteAddress = new ComputerDetails.AddressTuple("203.0.113.5", 47989);
        stored.macAddress = "AA:BB:CC:DD:EE:FF";
        stored.pairState = PairingManager.PairState.PAIRED;
        stored.runningGameId = 7;
        stored.httpsPort = 47984;

        int http = serve(null, FORGED.replace("</root>", "<PairStatus>1</PairStatus></root>"));
        NvHTTP nv = new NvHTTP(address(http), serve(other, FORGED), "client", host.cert, client);
        ComputerDetails reply = nv.getComputerDetails(true);
        reply.activeAddress = address(http);
        stored.update(reply);

        assertEquals("Workstation", stored.name);
        assertEquals("192.168.1.10", stored.localAddress.address);
        assertEquals("203.0.113.5", stored.remoteAddress.address);
        assertEquals("AA:BB:CC:DD:EE:FF", stored.macAddress);
        assertSame(host.cert, stored.serverCert);
        assertTrue(stored.identityUnverified);
        assertEquals(ComputerDetails.State.ONLINE, stored.state);
        assertEquals(PairingManager.PairState.NOT_PAIRED, stored.pairState);
        assertEquals(0, stored.runningGameId);
        // Pairing again needs the place that answered
        assertEquals(address(http), stored.activeAddress);
    }

    @Test
    public void aVerifiedReplyUpdatesAPairedPcAndClearsTheMark() throws Exception {
        ComputerDetails stored = new ComputerDetails();
        stored.uuid = "ABC-1";
        stored.name = "Old name";
        stored.serverCert = host.cert;
        stored.identityUnverified = true;

        NvHTTP nv = new NvHTTP(address(serve(null, FORGED)), serve(host, REAL), "client", host.cert, client);
        stored.update(nv.getComputerDetails(true));

        assertFalse(stored.identityUnverified);
        assertEquals("Workstation", stored.name);
        assertEquals("192.168.1.10", stored.localAddress.address);
        assertEquals("AA:BB:CC:DD:EE:FF", stored.macAddress);
        assertEquals(PairingManager.PairState.PAIRED, stored.pairState);
    }

    @Test
    public void aPcWithoutAPinTakesAnHttpReplyWhole() throws Exception {
        ComputerDetails fresh = new ComputerDetails();
        NvHTTP nv = new NvHTTP(address(serve(null, REAL)), 0, "client", null, client);
        fresh.update(nv.getComputerDetails(true));

        assertEquals("Workstation", fresh.name);
        assertEquals("192.168.1.10", fresh.localAddress.address);
        assertFalse(fresh.identityUnverified);
        assertNull(fresh.serverCert);
    }

    // ---- S-02: the host's id ----

    @Test
    public void aUniqueIdThatCouldLeaveADirectoryIsRejected() throws Exception {
        NvHTTP nv = new NvHTTP(address(1), 1, "client", null, client);
        for (String bad : new String[] {"../x", "a/b", "..", "a b", "a\\b"}) {
            assertThrows(bad, XmlPullParserException.class,
                    () -> nv.getComputerDetails(REAL.replace("ABC-1", bad)));
        }
        assertThrows(XmlPullParserException.class,
                () -> nv.getComputerDetails(REAL.replace("<uniqueid>ABC-1</uniqueid>", "<uniqueid></uniqueid>")));
        assertEquals("ABC-1", nv.getComputerDetails(REAL).uuid);
    }

    // ---- helpers ----

    private static String serverInfo(String name, String paired, String mac, String localIp) {
        return "<root status_code=\"200\"><hostname>" + name + "</hostname><appversion>7.1.431.-1</appversion>"
                + "<uniqueid>ABC-1</uniqueid><HttpsPort>47984</HttpsPort><ExternalPort>47989</ExternalPort>"
                + "<mac>" + mac + "</mac><LocalIP>" + localIp + "</LocalIP>"
                + "<PairStatus>" + paired + "</PairStatus><currentgame>0</currentgame>"
                + "<state>SUNSHINE_SERVER_FREE</state></root>";
    }

    private static ComputerDetails.AddressTuple address(int port) {
        return new ComputerDetails.AddressTuple("127.0.0.1", port);
    }

    /** Answers every request with {@code body}; over TLS with {@code identity}'s certificate when given. */
    private int serve(Identity identity, String body) throws Exception {
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

    private static X509TrustManager acceptingAll() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
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

    /** A self-signed certificate with its key, as hosts and this client have. */
    private static final class Identity implements LimelightCryptoProvider {
        final X509Certificate cert;
        final PrivateKey key;

        Identity(String name) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            X500Name subject = new X500Name("CN=" + name);
            long now = System.currentTimeMillis();
            cert = new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(
                    subject, BigInteger.valueOf(now), new Date(now - 60_000), new Date(now + 3_600_000), subject, pair.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
            key = pair.getPrivate();
        }

        SSLContext tls() throws Exception {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            store.setKeyEntry("host", key, new char[0], new X509Certificate[] {cert});
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, new char[0]);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            return context;
        }

        @Override public X509Certificate getClientCertificate() { return cert; }
        @Override public PrivateKey getClientPrivateKey() { return key; }
        @Override public byte[] getPemEncodedClientCertificate() { return new byte[0]; }
        @Override public String encodeBase64String(byte[] data) { return java.util.Base64.getEncoder().encodeToString(data); }
    }
}
