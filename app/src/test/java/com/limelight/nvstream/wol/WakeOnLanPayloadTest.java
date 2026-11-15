package com.limelight.nvstream.wol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.limelight.nvstream.http.ComputerDetails;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/** The Wake-on-LAN magic packet: six 0xFF bytes, then the PC's MAC address sixteen times. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WakeOnLanPayloadTest {
    private static byte[] payloadFor(String mac) {
        ComputerDetails pc = new ComputerDetails();
        pc.macAddress = mac;
        return WakeOnLanSender.createWolPayload(pc);
    }

    @Test
    public void magicPacketLayout() {
        byte[] payload = payloadFor("a0:b1:c2:d3:e4:f5");
        byte[] mac = {(byte) 0xA0, (byte) 0xB1, (byte) 0xC2, (byte) 0xD3, (byte) 0xE4, (byte) 0xF5};

        assertEquals(6 + 16 * 6, payload.length);
        for (int i = 0; i < 6; i++) {
            assertEquals((byte) 0xFF, payload[i]);
        }
        for (int repetition = 0; repetition < 16; repetition++) {
            int from = 6 + repetition * 6;
            assertArrayEquals("repetition " + repetition, mac, Arrays.copyOfRange(payload, from, from + 6));
        }
    }

    @Test
    public void upperCaseAndLeadingZerosAreTheSameAddress() {
        assertArrayEquals(payloadFor("0a:00:c2:d3:e4:0f"), payloadFor("0A:00:C2:D3:E4:0F"));
        assertEquals(0x0A, payloadFor("0a:00:c2:d3:e4:0f")[6]);
        assertEquals(0x00, payloadFor("0a:00:c2:d3:e4:0f")[7]);
    }

    @Test
    public void aMalformedMacStillYieldsAPacketOfTheRightSize() {
        for (String mac : new String[]{"", "not a mac", "a0:b1", "zz:b1:c2:d3:e4:f5", "a0:b1:c2:d3:e4:f5:07:08"}) {
            byte[] payload = payloadFor(mac);

            assertEquals(mac, 102, payload.length);
            assertEquals(mac, (byte) 0xFF, payload[0]);
        }
    }
}
