package com.example.kpkn.domain.biomechanics.golden

import kotlin.math.abs

/** Original acceptance contract; pure calculations, no corpus, engine or Android access. */
internal object OriginalAcceptance {
    enum class Status { PASS, FAIL, NOT_VALIDATED }
    data class Count(val tier: String, val expectedFull: Int, val actualFull: Int?, val alta: Boolean)
    data class CountSummary(
        val eligible: Int, val outputs: Int, val abstentions: Int, val exactFull: Int,
        val alta: Int, val altaExact: Int, val overOne: Int,
    ) {
        val conditionalExact: Double? get() = ratio(exactFull, outputs)
        val eligibleExact: Double? get() = ratio(exactFull, eligible)
        val coverage: Double? get() = ratio(outputs, eligible)
        val altaPrecision: Double? get() = ratio(altaExact, alta)
        val altaCoverage: Double? get() = ratio(alta, eligible)
    }
    fun counts(rows: List<Count>): CountSummary {
        require(rows.all { it.expectedFull >= 0 && (it.actualFull == null || it.actualFull >= 0) })
        val outputs = rows.filter { it.actualFull != null }
        val alta = outputs.filter { it.alta }
        return CountSummary(rows.size, outputs.size, rows.size - outputs.size,
            outputs.count { it.actualFull == it.expectedFull }, alta.size,
            alta.count { it.actualFull == it.expectedFull },
            outputs.count { abs(it.actualFull!!.toLong() - it.expectedFull.toLong()) > 1 })
    }
    fun tierStatus(rows: List<Count>, tier: String): Status {
        require(tier == "A" || tier == "B")
        val c = counts(rows.filter { it.tier == tier })
        val exact = c.eligibleExact ?: return Status.NOT_VALIDATED
        return if (exact >= if (tier == "A") .95 else .90) {
            if (c.overOne == 0) Status.PASS else Status.FAIL
        } else Status.FAIL
    }
    fun altaStatus(rows: List<Count>): Status {
        val p = counts(rows).altaPrecision ?: return Status.NOT_VALIDATED
        return if (p >= .98) Status.PASS else Status.FAIL
    }
    private fun ratio(n: Int, d: Int): Double? = if (d == 0) null else n.toDouble() / d

    /** Values are ms (event timestamps or independently annotated phase durations).
     * key identifies event kind or phase; clip and exact normalized side prevent cross matching.
     * Lists must already follow chronological REP order within each clip. Global clip order may differ.
     * A null side only matches null. Memory/time of DP: sum(nClip*mClip), not corpus-wide n*m.
     */
    data class Sample(val clip: String, val key: String, val side: String?, val valueMs: Double)
    data class Alignment(val pairs: List<Pair<Int, Int>>, val expected: Int, val detected: Int) {
        val missing: Int get() = expected - pairs.size
        val extras: Int get() = detected - pairs.size
    }
    fun match(expected: List<Sample>, detected: List<Sample>): Alignment {
        require((expected + detected).all { it.valueMs.isFinite() && it.side in setOf(null, "left", "right") })
        val expectedByClip = expected.withIndex().groupBy { it.value.clip }
        val detectedByClip = detected.withIndex().groupBy { it.value.clip }
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (clip in expectedByClip.keys + detectedByClip.keys) {
            val x = expectedByClip[clip].orEmpty()
            val y = detectedByClip[clip].orEmpty()
            val local = matchOneClip(x.map { it.value }, y.map { it.value })
            pairs += local.pairs.map { (i, j) -> x[i].index to y[j].index }
        }
        return Alignment(pairs.sortedBy { it.first }, expected.size, detected.size)
    }
    /** DP is monotonic within one clip; retain all key/side mismatches as missing/extras. */
    private fun matchOneClip(expected: List<Sample>, detected: List<Sample>): Alignment {
        require((expected + detected).all { it.valueMs.isFinite() && it.side in setOf(null, "left", "right") })
        val n = expected.size; val m = detected.size
        val matches = Array(n + 1) { IntArray(m + 1) }
        val costs = Array(n + 1) { DoubleArray(m + 1) }
        val choice = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) choice[i][0] = 1
        for (j in 1..m) choice[0][j] = 2
        for (i in 1..n) for (j in 1..m) {
            var count = matches[i - 1][j]; var cost = costs[i - 1][j]; var selected = 1
            fun prefer(c: Int, d: Double, how: Int) {
                if (c > count || (c == count && d < cost)) { count = c; cost = d; selected = how }
            }
            prefer(matches[i][j - 1], costs[i][j - 1], 2)
            val a = expected[i - 1]; val b = detected[j - 1]
            if (a.clip == b.clip && a.key == b.key && a.side == b.side) {
                prefer(matches[i - 1][j - 1] + 1, costs[i - 1][j - 1] + abs(a.valueMs - b.valueMs), 3)
            }
            matches[i][j] = count; costs[i][j] = cost; choice[i][j] = selected
        }
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = n; var j = m
        while (i > 0 || j > 0) when (choice[i][j]) {
            1 -> i--
            2 -> j--
            3 -> { pairs += (i - 1) to (j - 1); i--; j-- }
            else -> error("Invalid alignment")
        }
        return Alignment(pairs.asReversed(), n, m)
    }
    data class Temporal(
        val status: Status, val expected: Int, val detected: Int, val paired: Int,
        val missing: Int, val extras: Int, val maeMs: Double?, val icc: Double?, val reason: String,
    )
    /** expected=null means no independent, sufficiently precise ground truth. Never infer phases from turns. */
    fun temporal(expected: List<Sample>?, detected: List<Sample>, phaseIcc: Boolean): Temporal {
        if (expected == null) return Temporal(Status.NOT_VALIDATED, 0, detected.size, 0, 0, detected.size,
            null, null, "No independent ground truth; expected count unknown")
        if ((expected + detected).any { !it.valueMs.isFinite() || (phaseIcc && it.valueMs <= 0) }) {
            return Temporal(Status.NOT_VALIDATED, expected.size, detected.size, 0, expected.size, detected.size,
                null, null, "Invalid/non-finite values")
        }
        val a = match(expected, detected)
        val x = a.pairs.map { expected[it.first].valueMs }
        val y = a.pairs.map { detected[it.second].valueMs }
        val mae = if (x.isEmpty()) null else x.indices.map { abs(x[it] - y[it]) }.average()
        val icc = if (phaseIcc) iccA1(x, y) else null
        val state = when {
            a.missing > 0 || a.extras > 0 -> Status.FAIL
            mae == null || (phaseIcc && icc == null) -> Status.NOT_VALIDATED
            phaseIcc -> if (icc!! >= .90) Status.PASS else Status.FAIL
            else -> if (mae <= 50.0) Status.PASS else Status.FAIL
        }
        return Temporal(state, a.expected, a.detected, a.pairs.size, a.missing, a.extras, mae, icc,
            "MAE uses matched pairs only; any missing/extra blocks approval. ICC(A,1), two raters, absolute agreement.")
    }
    /** Two-way random, single measurement, absolute agreement: ICC(2,1) = ICC(A,1).
     * MSR rows, MSC raters, MSE residual; k=2. Constant/non-finite or n<3 is not estimable.
     */
    fun iccA1(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size < 3 || (x + y).any { !it.isFinite() }) return null
        if (x.distinct().size < 2 || y.distinct().size < 2) return null
        val n = x.size.toDouble(); val mx = x.average(); val my = y.average(); val grand = (mx + my) / 2
        val rowMeans = x.indices.map { (x[it] + y[it]) / 2 }
        val msr = 2 * rowMeans.sumOf { (it - grand) * (it - grand) } / (n - 1)
        val msc = n * ((mx - grand) * (mx - grand) + (my - grand) * (my - grand))
        val mse = x.indices.sumOf {
            val ex = x[it] - rowMeans[it] - mx + grand
            val ey = y[it] - rowMeans[it] - my + grand
            ex * ex + ey * ey
        } / (n - 1)
        val denominator = msr + mse + 2 * (msc - mse) / n
        if (!denominator.isFinite() || denominator <= 0) return null
        return ((msr - mse) / denominator).takeIf { it.isFinite() }
    }
    fun complete(vararg statuses: Status): Status = when {
        statuses.any { it == Status.FAIL } -> Status.FAIL
        statuses.isEmpty() || statuses.any { it == Status.NOT_VALIDATED } -> Status.NOT_VALIDATED
        else -> Status.PASS
    }
}
