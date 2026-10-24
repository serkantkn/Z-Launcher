package com.serkantkn.zeta.data.model

import com.serkantkn.zeta.R
import com.serkantkn.zeta.util.ReleaseNote

/**
 * What changed, version by version.
 *
 * The list is the source of truth for the "what's new" page: add an entry when the version code
 * goes up and it appears by itself. Anything already at or below somebody's last seen version is
 * never shown again, so old entries can stay here as a record without troubling anybody.
 *
 * Keep it to what a person would notice. "Refactored the media controller" is not a change
 * anybody asked about.
 */
object ReleaseNotes {

    val ALL: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = 4,
            name = "0.9.1 beta 3.1",
            released = "2026-10-24",
            titleRes = R.string.whats_new_v4_title,
            lineRes = listOf(R.string.whats_new_v4_line1)
        ),
        ReleaseNote(
            version = 3,
            name = "0.9.0 beta 3",
            released = "2026-10-24",
            titleRes = R.string.whats_new_v3_title,
            lineRes = listOf(
                R.string.whats_new_v3_line1,
                R.string.whats_new_v3_line2,
                R.string.whats_new_v3_line3,
                R.string.whats_new_v3_line4,
                R.string.whats_new_v3_line5,
                R.string.whats_new_v3_line6,
                R.string.whats_new_v3_line7
            )
        ),
        ReleaseNote(
            version = 2,
            name = "0.8.0 beta 2",
            released = "2026-10-22",
            titleRes = R.string.whats_new_v2_title,
            lineRes = listOf(
                R.string.whats_new_v2_line1,
                R.string.whats_new_v2_line2,
                R.string.whats_new_v2_line3,
                R.string.whats_new_v2_line4,
                R.string.whats_new_v2_line5
            )
        ),
        ReleaseNote(
            version = 1,
            name = "0.7.0 beta 1",
            released = "2026-10-20",
            titleRes = R.string.whats_new_v1_title,
            lineRes = listOf(
                R.string.whats_new_v1_line1,
                R.string.whats_new_v1_line9,
                R.string.whats_new_v1_line10,
                R.string.whats_new_v1_line11,
                R.string.whats_new_v1_line2,
                R.string.whats_new_v1_line3,
                R.string.whats_new_v1_line4,
                R.string.whats_new_v1_line5,
                R.string.whats_new_v1_line6,
                R.string.whats_new_v1_line7,
                R.string.whats_new_v1_line8
            )
        )
    )
}
