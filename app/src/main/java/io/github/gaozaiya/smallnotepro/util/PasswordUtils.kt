package io.github.gaozaiya.smallnotepro.util

import java.security.MessageDigest

object PasswordUtils {
    enum class Decision {
        Reveal,
        Decoy,
        Invalid,
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun decide(input: String, revealPasswordHash: String?, fakePasswordHash: String?): Decision {
        val hash = sha256Hex(input)

        if (!revealPasswordHash.isNullOrBlank() && hash == revealPasswordHash) {
            return Decision.Reveal
        }
        if (!fakePasswordHash.isNullOrBlank() && hash == fakePasswordHash) {
            return Decision.Decoy
        }
        return Decision.Invalid
    }
}
