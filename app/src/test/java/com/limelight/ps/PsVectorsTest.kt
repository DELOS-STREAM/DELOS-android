package com.limelight.ps

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

/** The test vectors of docs/research/ps-remote-play-test-vectors.json (spec section 10). */
class PsVectorsTest {
    private val v: JsonObject = JsonParser.parseString(javaClass.classLoader!!.getResource("ps-remote-play-test-vectors.json")!!.readText()).asJsonObject
    private fun o(name: String) = v.getAsJsonObject(name)
    private fun hex(o: JsonObject, k: String) = PsHex.decode(o.get(k).asString)
    private fun s(name: String) = PsHex.decode(v.get(name).asString)

    private fun checkSession(name: String, target: PsTarget) {
        val t = o(name)
        val c = RpCrypt.session(target, hex(t, "nonce"), hex(t, "morning"))
        assertArrayEquals("$name bright", hex(t, "bright"), c.bright)
        assertArrayEquals("$name ambassador", hex(t, "ambassador"), c.ambassador)
        assertArrayEquals("$name iv0", hex(t, "iv_counter0"), c.iv(0))
        assertArrayEquals("$name iv1", hex(t, "iv_counter1"), c.iv(1))
        assertArrayEquals("$name ct0", hex(t, "ciphertext_counter0"), c.encrypt(0, hex(t, "plaintext")))
        assertArrayEquals("$name ct5", hex(t, "ciphertext_counter5"), c.encrypt(5, hex(t, "plaintext")))
        assertArrayEquals("$name decrypt", hex(t, "plaintext"), c.decrypt(5, hex(t, "ciphertext_counter5")))
    }

    @Test fun sessionKeys() {
        checkSession("session_ps4_9", PsTarget.PS4_9)
        checkSession("session_ps4_10", PsTarget.PS4_10)
        checkSession("session_ps5_1", PsTarget.PS5_1)
    }

    private fun checkRegist(name: String, target: PsTarget) {
        val t = o(name)
        val req = PsRegistrationRequest(target, PsHex.decode("0102030405060708"), t.get("pin").asInt, hex(t, "ambassador"))
        assertEquals(t.get("key0_off").asInt, req.key0Off)
        assertEquals(t.get("key1_off").asInt, req.key1Off)
        assertArrayEquals("$name bright", hex(t, "bright"), req.crypt.bright)
        assertArrayEquals("$name aeropause", hex(t, "aeropause"), req.aeropause())
        assertArrayEquals("$name iv0", hex(t, "iv_counter0"), req.crypt.iv(0))
        assertArrayEquals("$name inner", hex(t, "inner_ciphertext"), req.crypt.encrypt(0, hex(t, "inner_plaintext")))
    }

    @Test fun registration() {
        checkRegist("regist_ps4_9", PsTarget.PS4_9)
        checkRegist("regist_ps4_10", PsTarget.PS4_10)
        checkRegist("regist_ps5_1", PsTarget.PS5_1)
        // The inner header DELOS writes for a PS5 is the vector's plaintext
        val t = o("regist_ps5_1")
        val req = PsRegistrationRequest(PsTarget.PS5_1, PsHex.decode("0102030405060708"), 12345678, hex(t, "ambassador"))
        assertEquals(String(hex(t, "inner_plaintext"), Charsets.US_ASCII), req.innerHeader())
        val payload = req.payload()
        assertEquals(0x1E0 + req.innerHeader().length, payload.size)
        assertArrayEquals(req.aeropause().copyOfRange(8, 16), payload.copyOfRange(0xC7, 0xCF))
        assertArrayEquals(req.aeropause().copyOfRange(0, 8), payload.copyOfRange(0x191, 0x199))
    }

    @Test fun ctrlHeadersPs5() {
        val t = o("ctrl_headers_ps5")
        val s = o("session_ps5_1")
        val crypt = RpCrypt.session(PsTarget.PS5_1, hex(s, "nonce"), hex(s, "morning"))
        val (headers, next) = PsSession.ctrlHeaders(crypt, t.get("regist_key_ascii").asString.toByteArray(), hex(t, "did"), PsCodec.HEVC)
        val map = headers.toMap()
        assertEquals(t.get("rp_auth").asString, map["RP-Auth"])
        assertEquals(t.get("rp_did").asString, map["RP-Did"])
        assertEquals(t.get("rp_ostype").asString, map["RP-OSType"])
        assertEquals(t.get("rp_startbitrate").asString, map["RP-StartBitrate"])
        assertEquals(t.get("rp_streamingtype_hevc").asString, map["RP-StreamingType"])
        assertEquals(5L, next)
        assertArrayEquals(hex(t, "did").copyOf(10), PsSession.newDid().copyOf(10))
    }

    @Test fun launchSpecKeystream() {
        val s = o("session_ps5_1")
        val crypt = RpCrypt.session(PsTarget.PS5_1, hex(s, "nonce"), hex(s, "morning"))
        assertArrayEquals(hex(o("launchspec_keystream_ps5"), "keystream"), crypt.encrypt(0, ByteArray(48)))
        // Encrypted launch spec XOR the same keystream gives back the JSON and its NUL
        val json = PsSession.launchSpecJson(PsTarget.PS5_1, PsStreamRequest(), ByteArray(16))
        val enc = Base64.getDecoder().decode(PsSession.launchSpecEncrypted(crypt, json))
        val ks = crypt.encrypt(0, ByteArray(enc.size))
        val plain = ByteArray(enc.size) { (enc[it].toInt() xor ks[it].toInt()).toByte() }
        assertEquals(json + "\u0000", String(plain, Charsets.UTF_8))
        assertTrue(json.contains("\"videoCodec\":\"hevc\",\"dynamicRange\":\"SDR\",\"handshakeKey\""))
    }

    @Test fun ecdh() {
        val t = o("ecdh")
        val client = PsEcdh(BigInteger(1, hex(t, "client_private")))
        assertArrayEquals(hex(t, "client_public"), client.publicKey)
        assertArrayEquals(hex(t, "shared_secret"), client.sharedSecret(hex(t, "console_public")))
        assertArrayEquals(hex(t, "client_sig"), client.signature(hex(t, "handshake_key")))
        val console = PsEcdh(BigInteger(1, hex(t, "console_private")))
        assertArrayEquals(hex(t, "shared_secret"), console.sharedSecret(client.publicKey))
    }

    private fun checkGk(name: String, index: Int) {
        val t = o(name)
        val gk = PsGkCrypt(index, hex(t, "handshake_key"), hex(t, "ecdh_secret"))
        assertArrayEquals("$name key", hex(t, "key"), gk.key)
        assertArrayEquals("$name iv", hex(t, "iv"), gk.iv)
        assertArrayEquals("$name g0", hex(t, "gmac_key_index0"), gk.gmacKey(0))
        assertArrayEquals("$name g1", hex(t, "gmac_key_index1"), gk.gmacKey(1))
        assertArrayEquals("$name ks0", hex(t, "keystream_pos0_32"), gk.keystream(0, 32))
        assertArrayEquals("$name ks18", hex(t, "keystream_pos0x18_20"), gk.keystream(0x18, 20))
        val data = hex(t, "data")
        assertArrayEquals("$name mac0", hex(t, "gmac_pos0"), gk.gmac(data, 0))
        assertArrayEquals("$name mac30", hex(t, "gmac_pos0x30"), gk.gmac(data, 0x30))
        assertArrayEquals("$name mac45001", hex(t, "gmac_pos45001"), gk.gmac(data, 45001))
    }

    @Test fun gkCrypt() { checkGk("gk_index2", 2); checkGk("gk_index3", 3) }

    @Test fun fec() {
        val t = o("fec_k3_m2")
        val m = PsFec.matrix(3, 2)
        val expected = t.getAsJsonArray("matrix").map { row -> row.asJsonArray.map { it.asInt }.toIntArray() }
        for (i in 0 until 2) assertArrayEquals(expected[i], m[i])
        val sources = t.getAsJsonArray("sources").map { PsHex.decode(it.asString) }
        val parity = t.getAsJsonArray("parity").map { PsHex.decode(it.asString) }
        val made = PsFec.encode(sources, 2)
        for (i in 0 until 2) assertArrayEquals(parity[i], made[i])
        // Any two missing units come back
        for (a in 0 until 5) for (b in a + 1 until 5) {
            val units: Array<ByteArray?> = arrayOf(sources[0], sources[1], sources[2], parity[0], parity[1])
            units[a] = null; units[b] = null
            assertTrue(PsFec.decode(units, 3, 2))
            for (j in 0 until 3) assertArrayEquals("lost $a,$b unit $j", sources[j], units[j])
        }
        val tooMany: Array<ByteArray?> = arrayOf(null, null, null, parity[0], parity[1])
        assertEquals(false, PsFec.decode(tooMany, 3, 2))
    }

    @Test fun feedback() {
        assertArrayEquals(s("feedback_state_idle_v12"), PsFeedback.state(PsControllerState(), v12 = true))
        assertArrayEquals(s("history_cross_down"), PsButton.CROSS.event(true))
        assertArrayEquals(s("history_touch_down"), PsFeedback.touchEvent(true, 3, 1000, 500))
    }

    @Test fun smallThings() {
        val a = o("account_id")
        val id = PsAccountId.fromUserId(a.get("user_id").asString)
        assertArrayEquals(hex(a, "account_id_bytes"), id)
        assertEquals(a.get("account_id_base64").asString, PsAccountId.toBase64(id))
        val w = o("wakeup_credential")
        assertEquals(w.get("user_credential_decimal").asString, PsDiscovery.credential(w.get("regist_key_ascii").asString.toByteArray()))
        assertArrayEquals(s("takion_init_packet"), PsTakionPackets.init(0x11223344))
    }
}
