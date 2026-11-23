package com.moneyflow.whatsnew

import com.moneyflow.ui.whatsnew.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
  @Test fun versionsCompareNumerically() {
    assertTrue(ReleaseNotes.compare("1.10.0", "1.9.9") > 0)
    assertEquals(0, ReleaseNotes.compare("1.3", "1.3.0"))
  }

  @Test fun showsEverythingSinceTheLastSeenVersion() {
    assertEquals(listOf("1.3.1", "1.3.0", "1.2.0"), ReleaseNotes.since("1.1.0", "1.3.1").map { it.version })
    assertEquals(listOf("1.3.1"), ReleaseNotes.since(null, "1.3.1").map { it.version })
    assertTrue(ReleaseNotes.since("1.3.1", "1.3.1").isEmpty())
  }

  @Test fun notesAreNewestFirstAndUnique() {
    val versions = ReleaseNotes.all.map { it.version }
    assertEquals(versions.distinct(), versions)
    assertEquals(versions.sortedWith { a, b -> ReleaseNotes.compare(b, a) }, versions)
  }
}
