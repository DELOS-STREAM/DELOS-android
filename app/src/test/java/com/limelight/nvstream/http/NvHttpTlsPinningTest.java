package com.limelight.nvstream.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;

/**
 * Certificate pinning of {@link NvHTTP} against a real TLS server on the loopback interface.
 * The host presents a self-signed certificate, exactly like Sunshine/Apollo/Vibepollo do; the
 * only thing that may make the client talk to it is the certificate pinned at pairing time.
 *
 * These tests state what stays true whatever the HTTP fallback policy is: a request over HTTPS
 * reaches a host only when its certificate is the pinned one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 36})
public class NvHttpTlsPinningTest {
    private static final String SERVER_INFO =
            "<root status_code=\"200\"><hostname>HOST</hostname><appversion>7.1.431.-1</appversion>"
                    + "<uniqueid>ABC</uniqueid><PairStatus>1</PairStatus><state>SUNSHINE_SERVER_FREE</state></root>";

    private static TestIdentity client;
    private static TestIdentity hostIdentity;
    private static TestIdentity other;

    private FakeTlsHost host;
    private int closedHttpPort;

    @BeforeClass
    public static void identities() {
        client = TestIdentity.create("NVIDIA GameStream Client");
        hostIdentity = TestIdentity.create("Sunshine Gamestream Host");
        other = TestIdentity.create("Sunshine Gamestream Host");
    }

    @Before
    public void startHost() throws Exception {
        host = new FakeTlsHost(hostIdentity, SERVER_INFO);
        // A port nothing listens on, for the plain-HTTP side of the host.
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedHttpPort = socket.getLocalPort();
        }
    }

    @After
    public void stopHost() throws IOException {
        host.close();
    }

    private NvHTTP clientPinnedTo(X509Certificate pinned) throws IOException {
        return new NvHTTP(new ComputerDetails.AddressTuple("127.0.0.1", closedHttpPort),
                host.port(), "0123456789ABCDEF", pinned, client);
    }

    @Test
    public void pinnedCertIsAccepted() throws Exception {
        String response = clientPinnedTo(hostIdentity.cert).executePairingChallenge();

        assertEquals(SERVER_INFO, response);
        assertEquals(1, host.requests.size());
    }

    @Test
    public void serverInfoOfAPinnedHostIsReadOverTls() throws Exception {
        // Plain HTTP is closed in this test, so an answer can only have come through TLS.
        assertEquals(SERVER_INFO, clientPinnedTo(hostIdentity.cert).getServerInfo(true));
        assertEquals(1, host.requests.size());
        assertTrue(host.requests.get(0).target.startsWith("/serverinfo?"));
    }

    @Test
    public void aDifferentSelfSignedCertIsRejected() throws Exception {
        NvHTTP http = clientPinnedTo(other.cert);

        assertThrows(SSLException.class, http::executePairingChallenge);
        assertEquals("a request reached a host that is not the pinned one", 0, host.requests.size());
    }

    @Test
    public void serverInfoIsNeverReadFromAHostWithAnotherCert() throws Exception {
        NvHTTP http = clientPinnedTo(other.cert);

        assertThrows(IOException.class, () -> http.getServerInfo(true));
        assertEquals("a request reached a host that is not the pinned one", 0, host.requests.size());
    }

    @Test
    public void noPinnedCertRejectsSelfSigned() throws Exception {
        NvHTTP http = clientPinnedTo(null);

        assertThrows(SSLException.class, http::executePairingChallenge);
        assertEquals(0, host.requests.size());
    }

    @Test
    public void theCertLearnedWhilePairingIsPinnedAtOnce() throws Exception {
        NvHTTP http = clientPinnedTo(null);

        http.setServerCert(hostIdentity.cert);
        assertEquals(SERVER_INFO, http.executePairingChallenge());

        http.setServerCert(other.cert);
        assertThrows(SSLException.class, http::executePairingChallenge);
        assertEquals(1, host.requests.size());
    }

    @Test
    public void theClientPresentsItsOwnCertificate() throws Exception {
        clientPinnedTo(hostIdentity.cert).executePairingChallenge();

        // This is how a host recognises a paired client.
        Certificate[] seen = host.requests.get(0).clientCertificates;
        assertNotNull("no client certificate was presented", seen);
        assertArrayEquals(client.cert.getEncoded(), seen[0].getEncoded());
    }

    @Test
    public void requestsCarryTheClientIdAndAFreshRequestId() throws Exception {
        NvHTTP http = clientPinnedTo(hostIdentity.cert);

        http.executePairingChallenge();
        http.executePairingChallenge();

        String first = host.requests.get(0).target;
        String second = host.requests.get(1).target;
        assertTrue(first, first.startsWith("/pair?"));
        assertEquals("0123456789ABCDEF", query(first, "uniqueid"));
        assertNotNull(query(first, "uuid"));
        assertNotEquals(query(first, "uuid"), query(second, "uuid"));
    }

    private static String query(String target, String name) {
        return FakePairingHost.parse(target.substring(target.indexOf('?') + 1)).get(name);
    }
}
