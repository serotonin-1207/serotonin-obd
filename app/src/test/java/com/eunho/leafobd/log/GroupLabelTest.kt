package com.eunho.leafobd.log

import org.junit.Assert.*
import org.junit.Test

class GroupLabelTest {
    @Test fun keepsSeparateCarsOfSameModel() {
        assertNotEquals(GroupLabel.normalize("리프 1"), GroupLabel.normalize("리프 2"))
        assertEquals("내 니로 EV", GroupLabel.normalize("  내 니로 EV  "))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsBlank() { GroupLabel.normalize("  ") }
    @Test(expected = IllegalArgumentException::class) fun rejectsControlText() { GroupLabel.normalize("리프\n니로") }
    @Test(expected = IllegalArgumentException::class) fun rejectsLongLabel() { GroupLabel.normalize("가".repeat(41)) }
}
