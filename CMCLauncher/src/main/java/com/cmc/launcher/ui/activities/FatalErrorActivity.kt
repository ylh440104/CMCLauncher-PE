package com.cmc.launcher.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.cmc.launcher.R
import com.cmc.launcher.context.COPY_LABEL_THROWABLE_STACK
import com.cmc.launcher.ui.base.AbstractAppCompatActivity
import com.cmc.launcher.ui.theme.showThemed
import com.cmc.launcher.utils.copyText
import com.cmc.launcher.utils.getSerializableSafely
import dagger.hilt.android.AndroidEntryPoint

private const val BUNDLE_THROWABLE = "BUNDLE_THROWABLE"

@AndroidEntryPoint
class FatalErrorActivity : AbstractAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras ?: return run { finish() }
        val throwable = extras.getSerializableSafely(BUNDLE_THROWABLE, Throwable::class.java) ?: return run { finish() }

        val message = getString(R.string.crash_launch_crash_message)
        val throwableStack = Log.getStackTraceString(throwable)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.crash_launch_crash_title))
            .setMessage(message + "\n\n" + throwableStack)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setNeutralButton(android.R.string.copy) { _, _ ->
                copyText(COPY_LABEL_THROWABLE_STACK, throwableStack, this)
                finish()
            }
            .setCancelable(false)
            .showThemed()
    }
}

fun showFatalError(context: Context, throwable: Throwable) {
    val intent = Intent(context, FatalErrorActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(BUNDLE_THROWABLE, throwable)
    }
    context.startActivity(intent)
}
