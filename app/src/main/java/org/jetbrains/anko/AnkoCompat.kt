package org.jetbrains.anko

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

/**
 * Local drop-in replacement for the two anko-commons helpers this project used.
 * anko-commons was only ever published to JCenter/Bintray, which has been shut
 * down, so the artifact can no longer be resolved. Re-declaring the same
 * package + symbol names here means every existing `import org.jetbrains.anko.*`
 * call site keeps working unchanged.
 */

val Context.defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this)

fun doAsync(task: () -> Unit) {
    Thread(task).start()
}
