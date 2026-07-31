package app.arbor.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Zero-UI entry point used only by launcher aliases.
 *
 * The real app task is rooted at [MainActivity], never at an alias component.
 * Changing or disabling a launcher alias therefore cannot close the screen the
 * user is currently using. [MainActivity] is singleTask, so tapping any alias
 * also brings the existing Arbor task forward instead of creating a duplicate.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
