package com.grumpy_phoenix.speedrun_split_tracker.ui.cast

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * The standard Cast button. It hides itself while no Cast device is on the network and opens the
 * device picker, which requires the hosting activity to be a FragmentActivity.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        // Relies on the activity's AppCompat theme, which MediaRouter's button and dialogs need.
        factory = { context ->
            MediaRouteButton(context).also { CastButtonFactory.setUpMediaRouteButton(context, it) }
        },
        modifier = modifier.size(48.dp),
    )
}
