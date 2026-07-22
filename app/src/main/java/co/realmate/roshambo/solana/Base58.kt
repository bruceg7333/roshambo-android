package co.realmate.roshambo.solana

import java.math.BigInteger

/** Minimal Base58 (Bitcoin/Solana alphabet) encoder for displaying public keys. */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        var value = BigInteger.ZERO
        for (c in input) {
            val digit = ALPHABET.indexOf(c)
            require(digit >= 0) { "Invalid Base58 char: $c" }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        var bytes = value.toByteArray()
        // Strip a leading sign byte BigInteger may add.
        if (bytes.size > 1 && bytes[0].toInt() == 0) bytes = bytes.copyOfRange(1, bytes.size)
        // Restore leading zero bytes (encoded as '1').
        val leadingZeros = input.takeWhile { it == ALPHABET[0] }.length
        return ByteArray(leadingZeros) + bytes
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var value = BigInteger(1, input)
        val sb = StringBuilder()
        while (value > BigInteger.ZERO) {
            val (q, r) = value.divideAndRemainder(BASE)
            sb.append(ALPHABET[r.toInt()])
            value = q
        }
        // Preserve leading zero bytes as leading '1's.
        for (b in input) {
            if (b.toInt() == 0) sb.append(ALPHABET[0]) else break
        }
        return sb.reverse().toString()
    }
}
