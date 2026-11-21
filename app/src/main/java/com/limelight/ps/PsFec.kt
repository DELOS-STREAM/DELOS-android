package com.limelight.ps

/**
 * Reed-Solomon erasure code of the video units (7.3): GF(2^8) with the polynomial 0x11D, a Cauchy matrix C[i][j] = 1 / (i XOR (F + j))
 * for F parity rows and S source columns. [encode] makes the parities, [decode] fills missing source units from any S of the S + F.
 */
object PsFec {
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]
    fun inv(a: Int): Int { require(a != 0); return exp[255 - log[a]] }

    fun matrix(sources: Int, parities: Int): Array<IntArray> = Array(parities) { i -> IntArray(sources) { j -> inv(i xor (parities + j)) } }

    fun encode(sources: List<ByteArray>, parities: Int): List<ByteArray> {
        val m = matrix(sources.size, parities)
        val size = sources.maxOf { it.size }
        return List(parities) { i ->
            ByteArray(size).also { out ->
                for (j in sources.indices) {
                    val c = m[i][j]
                    val src = sources[j]
                    for (b in src.indices) out[b] = (out[b].toInt() xor mul(c, u(src[b]))).toByte()
                }
            }
        }
    }

    /**
     * Restores the missing source units in place. [units] has S + F slots of equal length (null = missing); returns false when
     * more than F units are missing.
     */
    fun decode(units: Array<ByteArray?>, sources: Int, parities: Int): Boolean {
        val missing = (0 until sources).filter { units[it] == null }
        if (missing.isEmpty()) return true
        val present = (0 until sources + parities).filter { units[it] != null }
        if (present.size < sources) return false
        val size = units[present.first()]!!.size
        // Rows of the full generator (identity for sources, Cauchy for parities) for the first S present units
        val c = matrix(sources, parities)
        val rows = present.take(sources)
        val a = Array(sources) { r -> val idx = rows[r]; if (idx < sources) IntArray(sources) { if (it == idx) 1 else 0 } else c[idx - sources].copyOf() }
        val invA = invert(a) ?: return false
        for (m in missing) {
            val out = ByteArray(size)
            for (k in 0 until sources) {
                val coef = invA[m][k]
                if (coef == 0) continue
                val unit = units[rows[k]]!!
                for (b in 0 until size) out[b] = (out[b].toInt() xor mul(coef, u(unit[b]))).toByte()
            }
            units[m] = out
        }
        return true
    }

    private fun invert(m: Array<IntArray>): Array<IntArray>? {
        val n = m.size
        val a = Array(n) { m[it].copyOf() }
        val r = Array(n) { i -> IntArray(n) { if (it == i) 1 else 0 } }
        for (col in 0 until n) {
            val pivot = (col until n).firstOrNull { a[it][col] != 0 } ?: return null
            if (pivot != col) { val t = a[pivot]; a[pivot] = a[col]; a[col] = t; val u2 = r[pivot]; r[pivot] = r[col]; r[col] = u2 }
            val pinv = inv(a[col][col])
            for (k in 0 until n) { a[col][k] = mul(a[col][k], pinv); r[col][k] = mul(r[col][k], pinv) }
            for (row in 0 until n) {
                if (row == col || a[row][col] == 0) continue
                val f = a[row][col]
                for (k in 0 until n) { a[row][k] = a[row][k] xor mul(f, a[col][k]); r[row][k] = r[row][k] xor mul(f, r[col][k]) }
            }
        }
        return r
    }
}
