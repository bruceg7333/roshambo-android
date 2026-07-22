package co.realmate.roshambo.solana

import java.io.ByteArrayOutputStream

/**
 * Builds an unsigned legacy Solana transaction carrying a single SPL Memo
 * instruction. The wallet (via MWA) fills in the fee-payer signature and
 * submits it, so no custom on-chain program is required.
 */
object MemoTransaction {

    private const val MEMO_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"

    /** ShortVec (compact-u16) length prefix. */
    private fun shortVec(n: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var v = n
        while (true) {
            val b = v and 0x7f
            v = v ushr 7
            if (v == 0) { out.write(b); break } else { out.write(b or 0x80) }
        }
        return out.toByteArray()
    }

    /** Serialized transaction: [1 empty signature placeholder] + message. */
    fun build(feePayer: ByteArray, recentBlockhash: ByteArray, memo: ByteArray): ByteArray {
        val memoProgram = Base58.decode(MEMO_PROGRAM)

        val message = ByteArrayOutputStream().apply {
            // Header: 1 required signature, 0 readonly-signed, 1 readonly-unsigned.
            write(1); write(0); write(1)
            // Account keys: [feePayer (writable signer), memo program (readonly)].
            write(shortVec(2)); write(feePayer); write(memoProgram)
            // Recent blockhash.
            write(recentBlockhash)
            // One instruction: memo program, no accounts, memo bytes as data.
            write(shortVec(1))
            write(1)               // programIdIndex → memo program
            write(shortVec(0))     // no account indices
            write(shortVec(memo.size)); write(memo)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(shortVec(1))     // one signature
            write(ByteArray(64))   // zero placeholder; wallet fills it in
            write(message)
        }.toByteArray()
    }
}
