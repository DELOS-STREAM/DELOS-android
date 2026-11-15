package com.limelight.nvstream.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limelight.nvstream.http.PairingManager.PairState;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * The pairing handshake against an independent in-test host ({@link FakePairingHost}): the only
 * moment the client decides which host certificate it will trust from then on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PairingManagerTest {
    private static final String PIN = "4821";

    private static TestIdentity client;
    private static TestIdentity hostIdentity;
    private static TestIdentity impostor;

    private NvHTTP http;
    private FakePairingHost host;
    private PairingManager manager;

    @BeforeClass
    public static void identities() {
        client = TestIdentity.create("NVIDIA GameStream Client");
        hostIdentity = TestIdentity.create("Sunshine Gamestream Host");
        impostor = TestIdentity.create("Sunshine Gamestream Host");
    }

    @Before
    public void setUp() throws Exception {
        host = new FakePairingHost(hostIdentity, PIN);
        http = mock(NvHTTP.class);
        when(http.getServerMajorVersion(any())).thenReturn(7);
        when(http.executePairingCommand(anyString(), anyBoolean()))
                .thenAnswer(call -> host.onPairingCommand(call.getArgument(0)));
        when(http.executePairingChallenge()).thenAnswer(call -> host.onPairChallenge());
        manager = new PairingManager(http, client);
    }

    @Test
    public void pairsWithAHostThatKnowsThePin() throws Exception {
        assertEquals(PairState.PAIRED, manager.pair("serverinfo", PIN, null));

        // The host accepted the client's proof (PIN knowledge and key ownership) as well.
        assertTrue(host.clientProved);
        assertTrue(host.pairChallengeSeen);
        // The certificate the host presented is the one pinned from now on.
        assertEquals(hostIdentity.cert, manager.getPairedCert());
        verify(http).setServerCert(hostIdentity.cert);
        verify(http, never()).unpair();
    }

    @Test
    public void theFirstRequestWaitsForThePinWithoutAReadTimeout() throws Exception {
        manager.pair("serverinfo", PIN, null);

        // Only the first command may block while the user types the PIN on the host.
        verify(http, times(1)).executePairingCommand(anyString(), org.mockito.ArgumentMatchers.eq(false));
        verify(http, times(3)).executePairingCommand(anyString(), org.mockito.ArgumentMatchers.eq(true));
        assertEquals("getservercert", FakePairingHost.parse(host.requests.get(0)).get("phrase"));
    }

    @Test
    public void thePinNeverLeavesTheDevice() throws Exception {
        manager.pair("serverinfo", PIN, null);

        for (String request : host.requests) {
            for (String value : FakePairingHost.parse(request).values()) {
                assertFalse("PIN in a request", value.equals(PIN));
            }
        }
    }

    @Test
    public void everyAttemptUsesAFreshSalt() throws Exception {
        manager.pair("serverinfo", PIN, null);
        String firstSalt = FakePairingHost.parse(host.requests.get(0)).get("salt");

        host = new FakePairingHost(hostIdentity, PIN);
        manager.pair("serverinfo", PIN, null);
        String secondSalt = FakePairingHost.parse(host.requests.get(0)).get("salt");

        assertEquals(32, firstSalt.length());
        assertFalse(firstSalt.equals(secondSalt));
    }

    @Test
    public void wrongPinIsPinWrongAndUnpairs() throws Exception {
        host.pin = "0000";

        assertEquals(PairState.PIN_WRONG, manager.pair("serverinfo", PIN, null));

        verify(http).unpair();
        // The client must not hand its signed secret to a host that did not prove the PIN.
        assertEquals(3, host.requests.size());
        assertFalse(host.clientProved);
        assertFalse(host.pairChallengeSeen);
    }

    @Test
    public void aForgedServerSecretSignatureFails() throws Exception {
        // A man in the middle relays the real host's certificate but cannot sign with its key.
        host.signingKeyOverride = impostor.key;

        assertEquals(PairState.FAILED, manager.pair("serverinfo", PIN, null));

        verify(http).unpair();
        assertEquals(3, host.requests.size());
        assertFalse(host.pairChallengeSeen);
    }

    @Test
    public void aForgedSignatureIsReportedEvenWhenThePinIsAlsoWrong() throws Exception {
        host.signingKeyOverride = impostor.key;
        host.pin = "0000";

        assertEquals(PairState.FAILED, manager.pair("serverinfo", PIN, null));
        verify(http).unpair();
    }

    @Test
    public void emptyCertMeansAlreadyInProgress() throws Exception {
        host.omitCertificate = true;

        assertEquals(PairState.ALREADY_IN_PROGRESS, manager.pair("serverinfo", PIN, null));

        verify(http).unpair();
        verify(http, never()).setServerCert(any());
        assertNull(manager.getPairedCert());
        assertEquals(1, host.requests.size());
    }

    @Test
    public void aRefusedFirstRequestFailsWithoutPinningAnything() throws Exception {
        host.refuseAtStep = 1;

        assertEquals(PairState.FAILED, manager.pair("serverinfo", PIN, null));

        verify(http, never()).setServerCert(any());
        assertNull(manager.getPairedCert());
        assertEquals(1, host.requests.size());
    }

    @Test
    public void paired0AtAnyLaterStepFailsAndUnpairs() throws Exception {
        for (int step = 2; step <= 4; step++) {
            setUp();
            host.refuseAtStep = step;

            assertEquals("step " + step, PairState.FAILED, manager.pair("serverinfo", PIN, null));

            verify(http).unpair();
            assertEquals("the handshake stops at the refused step", step, host.requests.size());
            assertFalse(host.pairChallengeSeen);
        }
    }

    @Test
    public void aRefusedPairChallengeFailsAndUnpairs() throws Exception {
        host.refusePairChallenge = true;

        assertEquals(PairState.FAILED, manager.pair("serverinfo", PIN, null));
        verify(http).unpair();
    }

    @Test
    public void generation6UsesSha1() throws Exception {
        when(http.getServerMajorVersion(any())).thenReturn(6);
        host.hashAlgorithm = "SHA-1";

        assertEquals(PairState.PAIRED, manager.pair("serverinfo", PIN, null));
        assertTrue(host.clientProved);
        verify(http, never()).unpair();
    }

    @Test
    public void aHashMismatchBetweenGenerationsIsNotAccepted() throws Exception {
        // The client believes generation 6 (SHA-1) while the host hashes with SHA-256.
        when(http.getServerMajorVersion(any())).thenReturn(6);

        PairState state = manager.pair("serverinfo", PIN, null);

        assertFalse(state == PairState.PAIRED);
        verify(http).unpair();
    }

    @Test
    public void aPassphraseIsSentAsASaltedHashOnly() throws Exception {
        assertEquals(PairState.PAIRED, manager.pair("serverinfo", PIN, "correct horse"));

        Map<String, String> first = FakePairingHost.parse(host.requests.get(0));
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest((PIN + first.get("salt") + "correct horse").getBytes(StandardCharsets.UTF_8));
        assertEquals(FakePairingHost.hex(expected), first.get("otpauth"));
        for (String request : host.requests) {
            assertFalse(request.contains("correct horse"));
        }
    }

    @Test
    public void withoutAPassphraseNoOtpFieldIsSent() throws Exception {
        manager.pair("serverinfo", PIN, null);

        assertFalse(FakePairingHost.parse(host.requests.get(0)).containsKey("otpauth"));
    }

    @Test
    public void theClientSendsItsOwnCertificate() throws Exception {
        manager.pair("serverinfo", PIN, null);

        String sent = FakePairingHost.parse(host.requests.get(0)).get("clientcert");
        assertEquals(FakePairingHost.hex(client.pem()), sent);
    }

    @Test
    public void generatedPinsAreFourDigits() {
        for (int i = 0; i < 200; i++) {
            assertTrue(PairingManager.generatePinString().matches("[0-9]{4}"));
        }
    }
}
