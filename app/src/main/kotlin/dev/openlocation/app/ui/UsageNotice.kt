package dev.openlocation.app.ui

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.openlocation.app.R
import dev.openlocation.app.data.UsageConsent

@Composable
fun usageNoticeAccepted(): Boolean {
    val context = LocalContext.current
    var accepted by remember { mutableStateOf(UsageConsent.accepted(context)) }
    if (!accepted)
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.usage_notice_title)) },
            text = { Text(stringResource(R.string.usage_notice_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        UsageConsent.accept(context)
                        accepted = true
                    }
                ) {
                    Text(stringResource(R.string.usage_notice_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { (context as? Activity)?.finishAffinity() }) {
                    Text(stringResource(R.string.usage_notice_exit))
                }
            },
        )
    return accepted
}
