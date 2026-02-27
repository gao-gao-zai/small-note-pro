package io.github.gaozaiya.smallnotepro.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PasswordUtilsTest {
    @Test
    fun sha256Hex_sameInput_sameOutput() {
        val a = PasswordUtils.sha256Hex("abc")
        val b = PasswordUtils.sha256Hex("abc")
        assertEquals(a, b)
    }

    @Test
    fun sha256Hex_differentInput_differentOutput() {
        val a = PasswordUtils.sha256Hex("abc")
        val b = PasswordUtils.sha256Hex("abcd")
        assertNotEquals(a, b)
    }

    @Test
    fun decide_revealPassword_match() {
        val reveal = PasswordUtils.sha256Hex("real")
        val fake = PasswordUtils.sha256Hex("fake")
        assertEquals(PasswordUtils.Decision.Reveal, PasswordUtils.decide("real", reveal, fake))
    }

    @Test
    fun decide_fakePassword_match() {
        val reveal = PasswordUtils.sha256Hex("real")
        val fake = PasswordUtils.sha256Hex("fake")
        assertEquals(PasswordUtils.Decision.Decoy, PasswordUtils.decide("fake", reveal, fake))
    }

    @Test
    fun decide_invalidPassword() {
        val reveal = PasswordUtils.sha256Hex("real")
        val fake = PasswordUtils.sha256Hex("fake")
        assertEquals(PasswordUtils.Decision.Invalid, PasswordUtils.decide("nope", reveal, fake))
    }

    @Test
    fun decide_nullHashes_invalid() {
        assertEquals(PasswordUtils.Decision.Invalid, PasswordUtils.decide("anything", null, null))
    }
}
