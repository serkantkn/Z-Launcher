package com.serkantkn.zunelauncher.data.model

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ReleaseNote

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
            version = 1,
            titleRes = R.string.whats_new_v1_title,
            lineRes = listOf(
                R.string.whats_new_v1_line1,
                R.string.whats_new_v1_line2,
                R.string.whats_new_v1_line3,
                R.string.whats_new_v1_line4
            )
        )
    )
}
