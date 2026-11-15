package com.limelight.nvstream.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.limelight.nvstream.http.ComputerDetails.AddressTuple;
import com.limelight.nvstream.http.PairingManager.PairState;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParserException;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

/**
 * What the app reads out of a host's {@code /serverinfo} and {@code /applist} answers. The
 * documents below are written by hand in the field layout of each host family (they are not
 * captures of a real machine).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ServerInfoParsingTest {
    private static final String SUNSHINE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<root status_code=\"200\">"
            + "<hostname>LIVINGROOM</hostname>"
            + "<appversion>7.1.431.-1</appversion>"
            + "<GfeVersion>3.23.0.74</GfeVersion>"
            + "<uniqueid>2A6F3E0B-11C4-4E7B-9E2D-5B6A7C8D9E0F</uniqueid>"
            + "<HttpsPort>48984</HttpsPort>"
            + "<ExternalPort>48989</ExternalPort>"
            + "<MaxLumaPixelsHEVC>1869449984</MaxLumaPixelsHEVC>"
            + "<mac>a0:b1:c2:d3:e4:f5</mac>"
            + "<LocalIP>192.168.1.20</LocalIP>"
            + "<ServerCodecModeSupport>3843</ServerCodecModeSupport>"
            + "<PairStatus>1</PairStatus>"
            + "<currentgame>0</currentgame>"
            + "<state>SUNSHINE_SERVER_FREE</state>"
            + "</root>";

    private static final String APOLLO_BUSY = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<root status_code=\"200\">"
            + "<hostname>DESK</hostname>"
            + "<appversion>7.1.431.-1</appversion>"
            + "<GfeVersion>3.23.0.74</GfeVersion>"
            + "<uniqueid>7C1D0A55-0000-4000-8000-00000000BEEF</uniqueid>"
            + "<HttpsPort>47984</HttpsPort>"
            + "<ExternalPort>47989</ExternalPort>"
            + "<mac>00:00:00:00:00:00</mac>"
            + "<LocalIP>10.0.0.7</LocalIP>"
            + "<ExternalIP>203.0.113.9</ExternalIP>"
            + "<Permission>117506049</Permission>"
            + "<VirtualDisplayCapable>true</VirtualDisplayCapable>"
            + "<VirtualDisplayDriverReady>true</VirtualDisplayDriverReady>"
            + "<ServerCommand>Sleep</ServerCommand>"
            + "<ServerCommand>Restart Steam</ServerCommand>"
            + "<PairStatus>1</PairStatus>"
            + "<currentgame>881448767</currentgame>"
            + "<currentgameuuid>8CB5C136-DA67-4F99-B4A1-F9CD35005CF4</currentgameuuid>"
            + "<state>SUNSHINE_SERVER_BUSY</state>"
            + "</root>";

    private static final String GFE = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n"
            + "<root protocol_version=\"0.1\" query=\"serverinfo\" status_code=\"200\" status_message=\"OK\">"
            + "<hostname>OLDPC</hostname>"
            + "<appversion>7.1.415.0</appversion>"
            + "<GfeVersion>3.20.4.14</GfeVersion>"
            + "<uniqueid>0000111122223333</uniqueid>"
            + "<HttpsPort>47984</HttpsPort>"
            + "<mac>11:22:33:44:55:66</mac>"
            + "<LocalIP>192.168.0.2</LocalIP>"
            + "<ExternalIP>198.51.100.4</ExternalIP>"
            + "<PairStatus>0</PairStatus>"
            + "<currentgame>12345</currentgame>"
            + "<state>MJOLNIR_STATE_SERVER_AVAILABLE</state>"
            + "</root>";

    private static TestIdentity client;
    private NvHTTP http;

    @BeforeClass
    public static void identity() {
        client = TestIdentity.create("NVIDIA GameStream Client");
    }

    @Before
    public void setUp() throws Exception {
        // The address the document was fetched from; nothing is contacted in these tests.
        http = new NvHTTP(new AddressTuple("192.168.1.20", 47989), 0, "uid", null, client);
    }

    @Test
    public void sunshineResponse() throws Exception {
        ComputerDetails details = http.getComputerDetails(SUNSHINE);

        assertEquals("LIVINGROOM", details.name);
        assertEquals("2A6F3E0B-11C4-4E7B-9E2D-5B6A7C8D9E0F", details.uuid);
        assertEquals(48984, details.httpsPort);
        assertEquals(48989, details.externalPort);
        assertEquals("a0:b1:c2:d3:e4:f5", details.macAddress);
        // The local address keeps the port the client actually used.
        assertEquals(new AddressTuple("192.168.1.20", 47989), details.localAddress);
        assertNull("no ExternalIP in the document", details.remoteAddress);
        assertEquals(PairState.PAIRED, details.pairState);
        assertEquals(0, details.runningGameId);
        assertNull(details.runningGameUUID);
        assertFalse(details.nvidiaServer);
        assertFalse(details.vDisplaySupported);
        assertFalse(details.vDisplayDriverReady);
        assertEquals(Collections.emptyList(), details.serverCommands);
        assertEquals(-1, details.permission);
        assertFalse(details.pyroWave.supportsLinkTest());
        assertEquals(ComputerDetails.State.ONLINE, details.state);
    }

    @Test
    public void apolloResponseWithARunningApp() throws Exception {
        ComputerDetails details = http.getComputerDetails(APOLLO_BUSY);

        assertEquals("DESK", details.name);
        assertEquals(881448767, details.runningGameId);
        assertEquals("8CB5C136-DA67-4F99-B4A1-F9CD35005CF4", details.runningGameUUID);
        assertEquals(117506049, details.permission);
        assertTrue(details.vDisplaySupported);
        assertTrue(details.vDisplayDriverReady);
        assertEquals(Arrays.asList("Sleep", "Restart Steam"), details.serverCommands);
        assertEquals(new AddressTuple("203.0.113.9", 47989), details.remoteAddress);
        assertEquals(new AddressTuple("10.0.0.7", 47989), details.localAddress);
        assertEquals(PairState.PAIRED, details.pairState);
    }

    @Test
    public void vibepolloLinkTestFieldsAreRead() throws Exception {
        String vibepollo = SUNSHINE.replace("</root>",
                "<PyroWaveHostLinkMbps>2500</PyroWaveHostLinkMbps>"
                        + "<PyroWaveBandwidthProbeBytes>33554432</PyroWaveBandwidthProbeBytes>"
                        + "<PyroWaveWireBudgetVersion>1</PyroWaveWireBudgetVersion>"
                        + "<PyroWaveUdpProbeVersion>1</PyroWaveUdpProbeVersion>"
                        + "<PyroWaveUdpHandshakeVersion>1</PyroWaveUdpHandshakeVersion>"
                        + "<PyroWaveCriticalFecPercentage>50</PyroWaveCriticalFecPercentage>"
                        + "<PyroWaveMinParityShards>2</PyroWaveMinParityShards></root>");

        ComputerDetails details = http.getComputerDetails(vibepollo);

        assertEquals(2500, details.pyroWave.hostLinkMbps);
        assertTrue(details.pyroWave.supportsLinkTest());
    }

    @Test
    public void gfeResponse() throws Exception {
        ComputerDetails details = http.getComputerDetails(GFE);

        assertTrue(details.nvidiaServer);
        assertEquals(PairState.NOT_PAIRED, details.pairState);
        // GFE keeps the last game in currentgame; it only counts while the server is busy.
        assertEquals(0, details.runningGameId);
        // Without ExternalPort the port the client used stands in.
        assertEquals(47989, details.externalPort);
        assertEquals(new AddressTuple("198.51.100.4", 47989), details.remoteAddress);
        assertEquals(47984, details.httpsPort);
    }

    @Test
    public void aVirtualDisplayWithoutADriverIsNotReady() throws Exception {
        ComputerDetails details = http.getComputerDetails(
                APOLLO_BUSY.replace("<VirtualDisplayDriverReady>true", "<VirtualDisplayDriverReady>false"));

        assertTrue(details.vDisplaySupported);
        assertFalse(details.vDisplayDriverReady);
    }

    @Test
    public void aHostWithoutANameStillHasOne() throws Exception {
        ComputerDetails details = http.getComputerDetails(
                SUNSHINE.replace("<hostname>LIVINGROOM</hostname>", "<hostname></hostname>"));

        assertFalse(details.name == null || details.name.isEmpty());
    }

    @Test
    public void aNonNumericPermissionIsNoPermission() throws Exception {
        ComputerDetails details = http.getComputerDetails(
                APOLLO_BUSY.replace("<Permission>117506049", "<Permission>all"));

        assertEquals(-1, details.permission);
    }

    @Test
    public void missingUniqueIdIsRejected() {
        String noId = SUNSHINE.replace("<uniqueid>2A6F3E0B-11C4-4E7B-9E2D-5B6A7C8D9E0F</uniqueid>", "");

        assertThrows(XmlPullParserException.class, () -> http.getComputerDetails(noId));
    }

    @Test
    public void missingPairStatusOrStateIsRejected() {
        assertThrows(XmlPullParserException.class,
                () -> http.getComputerDetails(SUNSHINE.replace("<PairStatus>1</PairStatus>", "")));
        assertThrows(XmlPullParserException.class,
                () -> http.getComputerDetails(SUNSHINE.replace("<state>SUNSHINE_SERVER_FREE</state>", "")));
    }

    @Test
    public void anErrorStatusIsAnHttpErrorNotAHost() {
        String denied = "<root status_code=\"401\" status_message=\"Unauthorized\">"
                + "<hostname>X</hostname><uniqueid>1</uniqueid></root>";

        HostHttpResponseException error =
                assertThrows(HostHttpResponseException.class, () -> http.getComputerDetails(denied));
        assertEquals(401, error.getErrorCode());
    }

    @Test
    public void truncatedDocumentIsRejected() {
        String cut = SUNSHINE.substring(0, SUNSHINE.indexOf("<uniqueid>") + 15);

        assertThrows(Exception.class, () -> http.getComputerDetails(cut));
    }

    @Test
    public void missingHttpsPortFallsBackToTheDefault() {
        assertEquals(47984, http.getHttpsPort(SUNSHINE.replace("<HttpsPort>48984</HttpsPort>", "")));
    }

    @Test
    public void serverGenerationComesFromTheAppVersion() throws Exception {
        assertEquals(7, http.getServerMajorVersion(SUNSHINE));
        assertEquals(4, http.getServerAppVersionQuad(SUNSHINE).length);
        assertEquals(-1, http.getServerAppVersionQuad(SUNSHINE)[3]);
        assertThrows(IllegalArgumentException.class, () -> http.getServerMajorVersion(
                SUNSHINE.replace("7.1.431.-1", "7.1")));
    }

    @Test
    public void codecAndLumaFieldsDefaultToZeroWhenAbsent() throws Exception {
        assertEquals(3843, http.getServerCodecModeSupport(SUNSHINE));
        assertEquals(1869449984L, http.getMaxLumaPixelsHEVC(SUNSHINE));
        assertEquals(0, http.getServerCodecModeSupport(GFE));
        assertEquals(0, http.getMaxLumaPixelsHEVC(GFE));
        assertEquals(0, http.getMaxLumaPixelsH264(GFE));
    }

    // ---- /applist ----

    private static LinkedList<NvApp> apps(String xml) throws Exception {
        return NvHTTP.getAppListByReader(new StringReader(xml));
    }

    @Test
    public void appListWithHdrFlagsAndUuids() throws Exception {
        LinkedList<NvApp> list = apps("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root status_code=\"200\">"
                + "<App><IsHdrSupported>1</IsHdrSupported><AppTitle>Desktop</AppTitle>"
                + "<UUID>11111111-2222-3333-4444-555555555555</UUID><IDX>0</IDX><ID>881448767</ID></App>"
                + "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Steam Big Picture</AppTitle>"
                + "<ID>1093255277</ID></App>"
                + "</root>");

        assertEquals(2, list.size());
        assertEquals("Desktop", list.get(0).getAppName());
        assertEquals(881448767, list.get(0).getAppId());
        assertEquals("11111111-2222-3333-4444-555555555555", list.get(0).getAppUUID());
        assertTrue(list.get(0).isHdrSupported());
        assertEquals("Steam Big Picture", list.get(1).getAppName());
        assertEquals(1093255277, list.get(1).getAppId());
        assertFalse(list.get(1).isHdrSupported());
    }

    @Test
    public void anAppWithoutAUsableIdIsDropped() throws Exception {
        LinkedList<NvApp> list = apps("<root status_code=\"200\">"
                + "<App><AppTitle>Broken</AppTitle><ID>not-a-number</ID></App>"
                + "<App><AppTitle>Nameless id</AppTitle></App>"
                + "<App><AppTitle>Good</AppTitle><ID>7</ID></App>"
                + "</root>");

        assertEquals(1, list.size());
        assertEquals("Good", list.get(0).getAppName());
    }

    @Test
    public void anEmptyAppListIsEmpty() throws Exception {
        assertTrue(apps("<root status_code=\"200\"></root>").isEmpty());
    }

    @Test
    public void aTruncatedAppListIsRejectedInsteadOfShownHalf() {
        assertThrows(XmlPullParserException.class, () -> apps("<root status_code=\"200\">"
                + "<App><AppTitle>Desktop</AppTitle><ID>1</ID></App>"
                + "<App><AppTitle>Stea"));
    }

    @Test
    public void anAppListErrorStatusIsAnHttpError() {
        HostHttpResponseException error = assertThrows(HostHttpResponseException.class,
                () -> apps("<root status_code=\"503\" status_message=\"Busy\"></root>"));
        assertEquals(503, error.getErrorCode());
    }
}
