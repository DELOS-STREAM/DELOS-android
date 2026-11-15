package com.limelight.computers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.ComputerDetails.AddressTuple;
import com.limelight.nvstream.http.TestIdentity;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.security.cert.X509Certificate;
import java.util.List;

/** The table of saved PCs: every paired host and the certificate the app pinned for it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ComputerDatabaseManagerTest {
    private static X509Certificate certA;
    private static X509Certificate certB;

    private Context context;
    private ComputerDatabaseManager db;

    @BeforeClass
    public static void certificates() {
        certA = TestIdentity.create("host A").cert;
        certB = TestIdentity.create("host B").cert;
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        for (String name : context.databaseList()) {
            context.deleteDatabase(name);
        }
        db = new ComputerDatabaseManager(context);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private static ComputerDetails pc(String uuid, String name) {
        ComputerDetails details = new ComputerDetails();
        details.uuid = uuid;
        details.name = name;
        return details;
    }

    private static ComputerDetails fullPc() {
        ComputerDetails details = pc("UUID-FULL", "Living room");
        details.localAddress = new AddressTuple("192.168.1.20", 47989);
        details.remoteAddress = new AddressTuple("203.0.113.9", 48989);
        details.manualAddress = new AddressTuple("pc.example.org", 50000);
        details.ipv6Address = new AddressTuple("2001:db8::1", 47989);
        details.macAddress = "a0:b1:c2:d3:e4:f5";
        details.serverCert = certA;
        return details;
    }

    private void reopen() {
        db.close();
        db = new ComputerDatabaseManager(context);
    }

    @Test
    public void roundTripKeepsCertAndAllAddresses() throws Exception {
        assertTrue(db.updateComputer(fullPc()));
        reopen();

        ComputerDetails read = db.getComputerByUUID("UUID-FULL");

        assertEquals("Living room", read.name);
        assertEquals(new AddressTuple("192.168.1.20", 47989), read.localAddress);
        assertEquals(new AddressTuple("203.0.113.9", 48989), read.remoteAddress);
        assertEquals(new AddressTuple("pc.example.org", 50000), read.manualAddress);
        assertEquals(new AddressTuple("2001:db8::1", 47989), read.ipv6Address);
        assertEquals("a0:b1:c2:d3:e4:f5", read.macAddress);
        assertArrayEquals(certA.getEncoded(), read.serverCert.getEncoded());
        assertEquals(certA, read.serverCert);
        // The external port travels in the remote address.
        assertEquals(48989, read.externalPort);
        // Nothing is known about a stored PC until it is polled.
        assertEquals(ComputerDetails.State.UNKNOWN, read.state);
    }

    @Test
    public void aPcWithOnlyAManualAddressAndNoCertRoundTrips() {
        ComputerDetails details = pc("UUID-MANUAL", "Typed");
        details.manualAddress = new AddressTuple("10.0.0.7", 47989);
        db.updateComputer(details);
        reopen();

        ComputerDetails read = db.getComputerByUUID("UUID-MANUAL");

        assertEquals(new AddressTuple("10.0.0.7", 47989), read.manualAddress);
        assertNull(read.localAddress);
        assertNull(read.remoteAddress);
        assertNull(read.ipv6Address);
        assertNull(read.macAddress);
        assertNull(read.serverCert);
        assertEquals(47989, read.externalPort);
    }

    @Test
    public void updateDoesNotDuplicate() {
        db.updateComputer(fullPc());
        ComputerDetails renamed = fullPc();
        renamed.name = "Renamed";
        renamed.serverCert = certB;
        db.updateComputer(renamed);

        List<ComputerDetails> all = db.getAllComputers();

        assertEquals(1, all.size());
        assertEquals("Renamed", all.get(0).name);
        assertEquals(certB, all.get(0).serverCert);
    }

    @Test
    public void eachPcKeepsItsOwnCertificate() {
        ComputerDetails a = pc("UUID-A", "A");
        a.serverCert = certA;
        ComputerDetails b = pc("UUID-B", "B");
        b.serverCert = certB;
        db.updateComputer(a);
        db.updateComputer(b);

        assertEquals(certA, db.getComputerByUUID("UUID-A").serverCert);
        assertEquals(certB, db.getComputerByUUID("UUID-B").serverCert);
        assertEquals(2, db.getAllComputers().size());
    }

    @Test
    public void clearingTheCertIsStored() {
        db.updateComputer(fullPc());
        ComputerDetails unpaired = fullPc();
        unpaired.serverCert = null;
        db.updateComputer(unpaired);

        assertNull(db.getComputerByUUID("UUID-FULL").serverCert);
    }

    @Test
    public void deleteRemovesOnlyThatPc() {
        db.updateComputer(pc("UUID-A", "A"));
        db.updateComputer(pc("UUID-B", "B"));

        db.deleteComputer(pc("UUID-A", "whatever the name is now"));

        assertNull(db.getComputerByUUID("UUID-A"));
        assertNotNull(db.getComputerByUUID("UUID-B"));
        assertEquals(1, db.getAllComputers().size());
    }

    @Test
    public void byNameAndByUuid() {
        db.updateComputer(pc("UUID-A", "Desk"));
        db.updateComputer(pc("UUID-B", "Laptop"));

        assertEquals("UUID-B", db.getComputerByName("Laptop").uuid);
        assertEquals("Desk", db.getComputerByUUID("UUID-A").name);
        assertNull(db.getComputerByName("Nobody"));
        assertNull(db.getComputerByUUID("UUID-Z"));
    }

    @Test
    public void aNameWithQuotesIsStoredVerbatim() {
        db.updateComputer(pc("UUID-Q", "Bob's \"PC\"; DROP TABLE Computers"));

        assertEquals("UUID-Q", db.getComputerByName("Bob's \"PC\"; DROP TABLE Computers").uuid);
        assertEquals(1, db.getAllComputers().size());
    }

    @Test
    public void aRowWhoseIdCouldLeaveADirectoryIsNotAPc() {
        // The id names cache and artwork directories. Such a row can only come from an earlier version
        // or a tampered file, so it is written here past updateComputer().
        String[] bad = {"../../shared_prefs", "a/b", "..", "a b", "a\\b", "", "PC.1",
                new String(new char[com.limelight.utils.HostIds.MAX_UUID_LENGTH + 1]).replace('\0', 'A')};
        db.updateComputer(fullPc());
        db.close();
        SQLiteDatabase raw = SQLiteDatabase.openDatabase(
                context.getDatabasePath("computers4.db").getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        for (int i = 0; i < bad.length; i++) {
            ContentValues row = new ContentValues();
            row.put("UUID", bad[i]);
            row.put("ComputerName", "Bad " + i);
            row.put("Addresses", "{}");
            raw.insertOrThrow("Computers", null, row);
        }
        raw.close();
        db = new ComputerDatabaseManager(context);

        List<ComputerDetails> all = db.getAllComputers();
        assertEquals(1, all.size());
        assertEquals("UUID-FULL", all.get(0).uuid);
        for (int i = 0; i < bad.length; i++) {
            assertNull(bad[i], db.getComputerByUUID(bad[i]));
            assertNull(bad[i], db.getComputerByName("Bad " + i));
        }
    }

    @Test
    public void anUnknownIdOrNameIsNoPc() {
        db.updateComputer(fullPc());

        assertNull(db.getComputerByUUID("UUID-OTHER"));
        assertNull(db.getComputerByName("Kitchen"));
        assertNotNull(db.getComputerByName("Living room"));
    }

    @Test
    public void aRowWithoutAnIdInAVersion3DatabaseIsNotMigrated() {
        db.close();
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("computers3.db"), null);
        legacy.execSQL("CREATE TABLE Computers(UUID TEXT PRIMARY KEY, ComputerName TEXT NOT NULL,"
                + " Addresses TEXT NOT NULL, MacAddress TEXT, ServerCert TEXT)");
        ContentValues broken = new ContentValues();
        broken.putNull("UUID");
        broken.put("ComputerName", "No id");
        broken.put("Addresses", "192.168.1.6;;;");
        legacy.insertOrThrow("Computers", null, broken);
        ContentValues good = new ContentValues();
        good.put("UUID", "UUID-V3-GOOD");
        good.put("ComputerName", "Good PC");
        good.put("Addresses", "192.168.1.5;;;");
        legacy.insertOrThrow("Computers", null, good);
        legacy.close();

        db = new ComputerDatabaseManager(context);

        List<ComputerDetails> all = db.getAllComputers();
        assertEquals(1, all.size());
        assertEquals("UUID-V3-GOOD", all.get(0).uuid);
        assertEquals(new AddressTuple("192.168.1.5", 47989), all.get(0).localAddress);
        assertNull(db.getComputerByName("No id"));
    }

    @Test
    public void anEmptyDatabaseListsNothing() {
        assertTrue(db.getAllComputers().isEmpty());
    }

    // ---- databases of earlier app versions are taken over on first start ----

    @Test
    public void version3DatabaseMigratesWithCertAndPorts() throws Exception {
        db.close();
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("computers3.db"), null);
        legacy.execSQL("CREATE TABLE Computers(UUID TEXT PRIMARY KEY, ComputerName TEXT NOT NULL,"
                + " Addresses TEXT NOT NULL, MacAddress TEXT, ServerCert TEXT)");
        ContentValues row = new ContentValues();
        row.put("UUID", "UUID-V3");
        row.put("ComputerName", "Old PC");
        row.put("Addresses", "192.168.1.5_47989;203.0.113.9_48989;;2001:db8::5");
        row.put("MacAddress", "AA:BB:CC:DD:EE:FF");
        row.put("ServerCert", certA.getEncoded());
        legacy.insertOrThrow("Computers", null, row);
        legacy.close();

        db = new ComputerDatabaseManager(context);
        ComputerDetails read = db.getComputerByUUID("UUID-V3");

        assertEquals("Old PC", read.name);
        assertEquals(new AddressTuple("192.168.1.5", 47989), read.localAddress);
        assertEquals(new AddressTuple("203.0.113.9", 48989), read.remoteAddress);
        assertNull(read.manualAddress);
        // An address stored without a port gets the default one.
        assertEquals(new AddressTuple("2001:db8::5", 47989), read.ipv6Address);
        assertEquals("AA:BB:CC:DD:EE:FF", read.macAddress);
        assertEquals(certA, read.serverCert);
        assertFalse("the old file must not be migrated twice", context.getDatabasePath("computers3.db").exists());

        // And it is still there after the next start.
        reopen();
        assertEquals(certA, db.getComputerByUUID("UUID-V3").serverCert);
    }

    @Test
    public void version1DatabaseMigratesBlobAndPrefixedAddresses() {
        db.close();
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("computers.db"), null);
        legacy.execSQL("CREATE TABLE Computers(ComputerName TEXT PRIMARY KEY, UUID TEXT NOT NULL,"
                + " LocalAddress, RemoteAddress, MacAddress TEXT)");
        ContentValues blobRow = new ContentValues();
        blobRow.put("ComputerName", "Blob PC");
        blobRow.put("UUID", "UUID-V1-BLOB");
        blobRow.put("LocalAddress", new byte[]{(byte) 192, (byte) 168, 1, 5});
        blobRow.put("RemoteAddress", new byte[]{(byte) 203, 0, 113, 9});
        blobRow.put("MacAddress", "AA:BB:CC:DD:EE:FF");
        legacy.insertOrThrow("Computers", null, blobRow);
        ContentValues nameRow = new ContentValues();
        nameRow.put("ComputerName", "Named PC");
        nameRow.put("UUID", "UUID-V1-NAME");
        nameRow.put("LocalAddress", "ADDRESS_PREFIX__192.168.1.6");
        nameRow.put("RemoteAddress", "ADDRESS_PREFIX__pc.example.org");
        legacy.insertOrThrow("Computers", null, nameRow);
        legacy.close();

        db = new ComputerDatabaseManager(context);

        ComputerDetails blob = db.getComputerByUUID("UUID-V1-BLOB");
        assertEquals("Blob PC", blob.name);
        assertEquals(new AddressTuple("192.168.1.5", 47989), blob.localAddress);
        assertEquals(new AddressTuple("203.0.113.9", 47989), blob.remoteAddress);
        // Old versions kept typed-in addresses in the remote field.
        assertEquals(blob.remoteAddress, blob.manualAddress);
        assertEquals("AA:BB:CC:DD:EE:FF", blob.macAddress);
        assertNull("version 1 knew no pinned certificate", blob.serverCert);

        ComputerDetails named = db.getComputerByUUID("UUID-V1-NAME");
        assertEquals(new AddressTuple("192.168.1.6", 47989), named.localAddress);
        assertEquals(new AddressTuple("pc.example.org", 47989), named.remoteAddress);
        assertFalse(context.getDatabasePath("computers.db").exists());
    }

    @Test
    public void aCurrentEntryIsNotOverwrittenByNothing() {
        // Start with no legacy files at all: what is stored stays as it is.
        db.updateComputer(fullPc());
        reopen();
        reopen();

        assertEquals(1, db.getAllComputers().size());
        assertEquals(certA, db.getComputerByUUID("UUID-FULL").serverCert);
    }

    @Test
    public void addressJsonHelpersRoundTrip() throws Exception {
        org.json.JSONObject holder = new org.json.JSONObject();
        holder.put("local", ComputerDatabaseManager.tupleToJson(new AddressTuple("192.168.1.20", 47989)));

        assertEquals(new AddressTuple("192.168.1.20", 47989), ComputerDatabaseManager.tupleFromJson(holder, "local"));
        assertNull(ComputerDatabaseManager.tupleFromJson(holder, "remote"));
        assertNull(ComputerDatabaseManager.tupleToJson(null));
    }
}
