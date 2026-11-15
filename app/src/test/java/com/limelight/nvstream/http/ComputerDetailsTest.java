package com.limelight.nvstream.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.limelight.nvstream.http.ComputerDetails.AddressTuple;

import org.junit.Test;

/** Merging a poll result into what is stored about a PC must never lose what makes it reachable. */
public class ComputerDetailsTest {
    private static ComputerDetails stored() {
        ComputerDetails details = new ComputerDetails();
        details.uuid = "UUID-1";
        details.name = "PC";
        details.localAddress = new AddressTuple("192.168.1.20", 47989);
        details.remoteAddress = new AddressTuple("203.0.113.9", 47989);
        details.manualAddress = new AddressTuple("pc.example.org", 47989);
        details.ipv6Address = new AddressTuple("2001:db8::1", 47989);
        details.activeAddress = details.localAddress;
        details.macAddress = "a0:b1:c2:d3:e4:f5";
        details.serverCert = TestIdentity.create("host").cert;
        return details;
    }

    /** What a poll yields when the host tells nothing beyond its identity. */
    private static ComputerDetails barePoll() {
        ComputerDetails poll = new ComputerDetails();
        poll.uuid = "UUID-1";
        poll.name = "PC";
        poll.state = ComputerDetails.State.ONLINE;
        return poll;
    }

    @Test
    public void updateKeepsKnownAddressesAndPinnedCert() {
        ComputerDetails details = stored();
        ComputerDetails before = new ComputerDetails(details);

        details.update(barePoll());

        assertEquals(before.localAddress, details.localAddress);
        assertEquals(before.remoteAddress, details.remoteAddress);
        assertEquals(before.manualAddress, details.manualAddress);
        assertEquals(before.ipv6Address, details.ipv6Address);
        assertEquals(before.activeAddress, details.activeAddress);
        assertEquals(before.macAddress, details.macAddress);
        assertSame(before.serverCert, details.serverCert);
        assertEquals(ComputerDetails.State.ONLINE, details.state);
    }

    @Test
    public void updateTakesNewAddresses() {
        ComputerDetails details = stored();
        ComputerDetails poll = barePoll();
        poll.localAddress = new AddressTuple("192.168.1.99", 47989);
        poll.remoteAddress = new AddressTuple("198.51.100.4", 48989);
        poll.macAddress = "11:22:33:44:55:66";

        details.update(poll);

        assertEquals(new AddressTuple("192.168.1.99", 47989), details.localAddress);
        assertEquals(new AddressTuple("198.51.100.4", 48989), details.remoteAddress);
        assertEquals("11:22:33:44:55:66", details.macAddress);
    }

    @Test
    public void aLoopbackLocalAddressDoesNotReplaceTheRealOne() {
        ComputerDetails details = stored();
        ComputerDetails poll = barePoll();
        poll.localAddress = new AddressTuple("127.0.0.1", 47989);

        details.update(poll);

        assertEquals(new AddressTuple("192.168.1.20", 47989), details.localAddress);
    }

    @Test
    public void anAllZeroMacDoesNotReplaceTheRealOne() {
        ComputerDetails details = stored();
        ComputerDetails poll = barePoll();
        poll.macAddress = "00:00:00:00:00:00";

        details.update(poll);

        assertEquals("a0:b1:c2:d3:e4:f5", details.macAddress);
    }

    @Test
    public void anExternalPortWithoutAnAddressMovesTheKnownRemoteAddress() {
        ComputerDetails details = stored();
        ComputerDetails poll = barePoll();
        poll.externalPort = 48989;

        details.update(poll);

        assertEquals(new AddressTuple("203.0.113.9", 48989), details.remoteAddress);
    }

    @Test
    public void transientStateFollowsThePoll() {
        ComputerDetails details = stored();
        details.runningGameId = 5;
        details.pairState = PairingManager.PairState.PAIRED;
        ComputerDetails poll = barePoll();
        poll.pairState = PairingManager.PairState.NOT_PAIRED;
        poll.httpsPort = 48984;

        details.update(poll);

        assertEquals(0, details.runningGameId);
        assertEquals(PairingManager.PairState.NOT_PAIRED, details.pairState);
        assertEquals(48984, details.httpsPort);
    }

    @Test
    public void guessExternalPortPrefersTheMostSpecificSource() {
        ComputerDetails details = new ComputerDetails();
        assertEquals(NvHTTP.DEFAULT_HTTP_PORT, details.guessExternalPort());

        details.localAddress = new AddressTuple("192.168.1.20", 1001);
        assertEquals(1001, details.guessExternalPort());
        details.ipv6Address = new AddressTuple("2001:db8::1", 1002);
        assertEquals(1002, details.guessExternalPort());
        details.activeAddress = new AddressTuple("192.168.1.20", 1003);
        assertEquals(1003, details.guessExternalPort());
        details.remoteAddress = new AddressTuple("203.0.113.9", 1004);
        assertEquals(1004, details.guessExternalPort());
        details.externalPort = 1005;
        assertEquals(1005, details.guessExternalPort());
    }

    @Test
    public void addressTupleStripsIpv6BracketsAndPrintsThemBack() {
        AddressTuple tuple = new AddressTuple("[2001:db8::1]", 47989);

        assertEquals("2001:db8::1", tuple.address);
        assertEquals("[2001:db8::1]:47989", tuple.toString());
        assertEquals("192.168.1.20:47989", new AddressTuple("192.168.1.20", 47989).toString());
    }

    @Test
    public void addressTupleEqualityIsAddressAndPort() {
        AddressTuple tuple = new AddressTuple("192.168.1.20", 47989);

        assertEquals(tuple, new AddressTuple("192.168.1.20", 47989));
        assertEquals(tuple.hashCode(), new AddressTuple("192.168.1.20", 47989).hashCode());
        assertNotEquals(tuple, new AddressTuple("192.168.1.20", 47990));
        assertNotEquals(tuple, new AddressTuple("192.168.1.21", 47989));
        assertNotEquals(tuple, "192.168.1.20:47989");
    }

    @Test
    public void addressTupleRejectsWhatCannotBeDialled() {
        assertThrows(IllegalArgumentException.class, () -> new AddressTuple(null, 47989));
        assertThrows(IllegalArgumentException.class, () -> new AddressTuple("192.168.1.20", 0));
        assertThrows(IllegalArgumentException.class, () -> new AddressTuple("192.168.1.20", -1));
    }

    @Test
    public void aFreshPcIsInAnUnknownState() {
        ComputerDetails details = new ComputerDetails();

        assertEquals(ComputerDetails.State.UNKNOWN, details.state);
        assertNull(details.serverCert);
    }
}
