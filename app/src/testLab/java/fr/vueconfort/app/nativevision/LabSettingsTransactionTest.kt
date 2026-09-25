package fr.vueconfort.app.nativevision

import org.junit.Assert.*
import org.junit.Test

class LabSettingsTransactionTest {
    private val enabled = LabSecureKey.RELUMINO_ENABLED
    private val thickness = LabSecureKey.RELUMINO_THICKNESS
    private val capability = NativeVisionCapability.RELUMINO

    @Test fun absentKeysAreDeletedAndPresentStringsExactlyRestored() {
        val port = FakePort(mutableMapOf(thickness to "2.000"))
        val journal = FakeJournal()
        val engine = LabSettingsTransaction(port, journal)
        assertEquals(LabTransactionStatus.VERIFIED, engine.apply(linkedMapOf(thickness to "4.99", enabled to "1"), 1).status)
        assertEquals(LabTransactionStatus.VERIFIED, engine.restore(capability).status)
        assertFalse(port.values.containsKey(enabled))
        assertEquals("2.000", port.values[thickness])
        assertFalse(engine.hasRestoration())
    }

    @Test fun reopeningKeepsTheOriginalBaselineAcrossSeveralCommands() {
        val port = FakePort(mutableMapOf(enabled to "0"))
        val journal = FakeJournal()
        LabSettingsTransaction(port, journal).apply(linkedMapOf(thickness to "1.0", enabled to "1"), 1)
        val reopened = LabSettingsTransaction(port, journal)
        reopened.apply(linkedMapOf(thickness to "4.99", enabled to "1"), 2)
        assertEquals("0", journal.records[enabled]?.previous)
        assertEquals(LabTransactionStatus.VERIFIED, reopened.restore(capability).status)
        assertEquals(mapOf(enabled to "0"), port.values)
    }

    @Test fun externalChangeRejectsTheEntireCapabilityBeforeAnyRestoreWrite() {
        val port = FakePort()
        val engine = LabSettingsTransaction(port, FakeJournal())
        engine.apply(linkedMapOf(thickness to "4.99", enabled to "1"), 1)
        port.values[thickness] = "3.0"
        val count = port.writes
        assertEquals(LabTransactionStatus.CONFLICT, engine.restore(capability).status)
        assertEquals(count, port.writes)
        assertEquals("1", port.values[enabled])
        assertEquals("3.0", port.values[thickness])
        assertTrue(engine.hasRestoration())
    }

    @Test fun externalChangeRejectsNextApplyAndPreservesUserValue() {
        val port = FakePort()
        val engine = LabSettingsTransaction(port, FakeJournal())
        engine.apply(linkedMapOf(thickness to "2.0"), 1)
        port.values[thickness] = "1.0"
        assertEquals(LabTransactionStatus.CONFLICT, engine.apply(linkedMapOf(thickness to "4.0"), 2).status)
        assertEquals("1.0", port.values[thickness])
    }

    @Test fun missingOrRevokedPermissionNeverMutatesAndKeepsRestoration() {
        val port = FakePort().apply { allowed = false }
        val engine = LabSettingsTransaction(port, FakeJournal())
        assertEquals(LabTransactionStatus.PERMISSION_MISSING, engine.apply(linkedMapOf(enabled to "1"), 1).status)
        assertEquals(0, port.writes)
        assertFalse(engine.hasRestoration())
        port.allowed = true
        engine.apply(linkedMapOf(enabled to "1"), 2)
        port.allowed = false
        assertEquals(LabTransactionStatus.PERMISSION_MISSING, engine.restore(capability).status)
        assertEquals("1", port.values[enabled])
        assertTrue(engine.hasRestoration())
    }

    @Test fun revocationDuringJournalCommitIsCheckedImmediatelyBeforeWrite() {
        val port = FakePort()
        val journal = FakeJournal().apply { afterSave = { port.allowed = false } }
        val engine = LabSettingsTransaction(port, journal)
        assertEquals(LabTransactionStatus.PERMISSION_MISSING, engine.apply(linkedMapOf(enabled to "1"), 1).status)
        assertEquals(0, port.writes)
        port.allowed = true
        journal.afterSave = {}
        assertEquals(LabTransactionStatus.VERIFIED, engine.restore(capability).status)
        assertFalse(engine.hasRestoration())
    }

    @Test fun allReluminoStopsAcceptedAndInvalidRangesRejectedBeforeWrites() {
        listOf("1.0", "2.0", "3.0", "4.0", "4.99").forEach { value ->
            assertTrue(thickness.accepts(value))
        }
        listOf("0", "5.0", "NaN", "Infinity", "-1.0").forEach { value ->
            val port = FakePort()
            val engine = LabSettingsTransaction(port, FakeJournal())
            assertEquals(LabTransactionStatus.INVALID, engine.apply(linkedMapOf(thickness to value, enabled to "1"), 1).status)
            assertEquals(0, port.writes)
        }
        assertFalse(LabSecureKey.RELUMINO_TYPE.accepts("4"))
        assertFalse(LabSecureKey.COLOR_FILTER_OPACITY.accepts("9"))
        assertFalse(LabSecureKey.EXTRA_DIM_STRENGTH.accepts("101"))
        assertFalse(LabSecureKey.COLOR_CORRECTION_MODE.accepts("14"))
    }

    @Test fun crashAfterWriteBeforeFinalCommitRecoversUsingWriteAheadValue() {
        val port = FakePort()
        val journal = FakeJournal().apply { rejectSaveNumber = 2 }
        val engine = LabSettingsTransaction(port, journal)
        assertEquals(LabTransactionStatus.ERROR, engine.apply(linkedMapOf(enabled to "1"), 1).status)
        assertEquals("1", port.values[enabled])
        assertTrue(journal.records[enabled]!!.hasPendingWrite)
        journal.rejectSaveNumber = -1
        assertEquals(LabTransactionStatus.VERIFIED, LabSettingsTransaction(port, journal).restore(capability).status)
        assertFalse(port.values.containsKey(enabled))
    }

    @Test fun refusingPrewritePersistencePreventsSystemMutation() {
        val port = FakePort()
        val engine = LabSettingsTransaction(port, FakeJournal().apply { rejectSaveNumber = 1 })
        assertEquals(LabTransactionStatus.ERROR, engine.apply(linkedMapOf(enabled to "1"), 1).status)
        assertEquals(0, port.writes)
    }

    @Test fun providerRefusalIsNotReportedAsAppliedAndJournalCanBeClearedByRestore() {
        val port = FakePort().apply { rejectWrites = true }
        val engine = LabSettingsTransaction(port, FakeJournal())
        assertEquals(LabTransactionStatus.ERROR, engine.apply(linkedMapOf(enabled to "1"), 1).status)
        assertFalse(port.values.containsKey(enabled))
        assertTrue(engine.hasRestoration())
        assertEquals(LabTransactionStatus.VERIFIED, engine.restore(capability).status)
        assertFalse(engine.hasRestoration())
    }

    @Test fun cancellationAfterPersistenceStopsOldRequestAndKeepsRollback() {
        val port = FakePort()
        var current = true
        val journal = FakeJournal().apply { afterSave = { current = false } }
        val engine = LabSettingsTransaction(port, journal)
        assertEquals(LabTransactionStatus.INVALID, engine.apply(linkedMapOf(thickness to "4.99", enabled to "1"), 1) { current }.status)
        assertEquals(0, port.writes)
        journal.afterSave = {}
        assertEquals(LabTransactionStatus.VERIFIED, engine.restore(capability).status)
    }

    @Test fun explicitlyKeepingCurrentClearsOwnershipWithoutWriting() {
        val port = FakePort()
        val engine = LabSettingsTransaction(port, FakeJournal())
        engine.apply(linkedMapOf(enabled to "1"), 1)
        val count = port.writes
        assertTrue(engine.keepCurrent())
        assertFalse(engine.hasRestoration())
        assertEquals("1", port.values[enabled])
        assertEquals(count, port.writes)
    }

    @Test fun emptyStringIsRestoredAsAStoredValueNotAbsence() {
        val port = FakePort(mutableMapOf(thickness to ""))
        val engine = LabSettingsTransaction(port, FakeJournal())
        engine.apply(linkedMapOf(thickness to "1.0"), 1)
        engine.restore(capability)
        assertTrue(port.values.containsKey(thickness))
        assertEquals("", port.values[thickness])
    }

    private class FakePort(val values: MutableMap<LabSecureKey, String> = mutableMapOf()) : LabSettingsPort {
        var allowed = true
        var rejectWrites = false
        var writes = 0
        override fun canWrite() = allowed
        override fun read(key: LabSecureKey) = values[key]
        override fun put(key: LabSecureKey, value: String): Boolean { check(allowed); writes++; if (rejectWrites) return false; values[key] = value; return true }
        override fun delete(key: LabSecureKey): Boolean { check(allowed); writes++; values.remove(key); return true }
    }
    private class FakeJournal : LabJournal {
        var records: Map<LabSecureKey, LabRestoreRecord> = emptyMap()
        var afterSave: () -> Unit = {}
        var rejectSaveNumber = -1
        private var saveNumber = 0
        override fun load() = records.toMap()
        override fun save(records: Map<LabSecureKey, LabRestoreRecord>): Boolean {
            if (++saveNumber == rejectSaveNumber) return false
            this.records = records.toMap()
            afterSave()
            return true
        }
    }
}
