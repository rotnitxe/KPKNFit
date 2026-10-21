package com.example.kpkn.data.media.video

/**
 * Container access units under the explicit MP4 composition-order index policy.
 * No Android dependency. Never deduplicates, estimates fps or rebases the source PTS.
 */
internal class IndexedPresentationTimes private constructor(
    private val presentationUs: LongArray,
    val reorderedForMp4Presentation: Boolean,
) {
    val size: Int get() = presentationUs.size
    fun ptsUs(index: Int): Long = presentationUs[index]

    fun firstIndexAtOrAfter(fromUs: Long): Int {
        var lo = 0
        var hi = presentationUs.size
        while (lo < hi) {
            val mid = lo + (hi - lo) / 2
            if (presentationUs[mid] < fromUs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        // 8 MiB raw PTS budget; validation copies once, bounding transient PTS storage to 16 MiB.
        const val MAX_ENTRIES = 1_048_576

        fun verify(samplePtsUs: LongArray, reportedFrameCount: Int?, videoTracks: Int, mp4Compatible: Boolean): IndexedPtsValidation {
            if (!mp4Compatible) return IndexedPtsValidation.Rejected("unsupported_container_or_codec")
            if (videoTracks != 1) return IndexedPtsValidation.Rejected("ambiguous_video_tracks")
            if (reportedFrameCount == null || reportedFrameCount <= 0) return IndexedPtsValidation.Rejected("no_frame_count")
            if (reportedFrameCount > MAX_ENTRIES) return IndexedPtsValidation.Rejected("pts_memory_budget")
            if (samplePtsUs.size != reportedFrameCount) return IndexedPtsValidation.Rejected("sample_frame_count_mismatch")
            if (samplePtsUs.isEmpty() || samplePtsUs.any { it < 0L }) return IndexedPtsValidation.Rejected("invalid_pts")
            var reordered = false
            for (i in 1 until samplePtsUs.size) if (samplePtsUs[i] < samplePtsUs[i - 1]) {
                reordered = true
                break
            }
            // Explicit MP4 policy, grounded in AOSP SampleTable's composition-time frame-index table.
            // This is not a generic rule for other extractors/containers. Keep the evidence bit.
            val presentation = samplePtsUs.copyOf()
            if (reordered) presentation.sort()
            for (i in 1 until presentation.size) {
                if (presentation[i] == presentation[i - 1]) return IndexedPtsValidation.Rejected("duplicate_presentation_pts")
            }
            return IndexedPtsValidation.Verified(IndexedPresentationTimes(presentation, reordered))
        }
    }
}

internal sealed interface IndexedPtsValidation {
    data class Verified(val times: IndexedPresentationTimes) : IndexedPtsValidation
    data class Rejected(val reason: String) : IndexedPtsValidation
}
