package com.grumpy_phoenix.speedrun_split_tracker.cast

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.grumpy_phoenix.speedrun_split_tracker.domain.Clock
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mirrors the timer to a connected Cast receiver. Sends a snapshot whenever it changes and when a
 * session starts; while a run is going it also resends every few seconds, which corrects drift
 * and refills a receiver that was reloaded. Call [release] when the owner is destroyed.
 */
class TimerCaster(context: Context, private val clock: Clock, private val scope: CoroutineScope) {

    private val _available = MutableStateFlow(false)

    /** False when Cast cannot be used here (e.g. no Google Play services); hide the button then. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var castContext: CastContext? = null
    private var session: CastSession? = null
    private var title: String? = null
    private var timer: TimerState? = null
    private var keepAliveJob: Job? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionEnded(session: CastSession, error: Int) = detach()
        override fun onSessionSuspended(session: CastSession, reason: Int) = detach()
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    }

    init {
        CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
            .addOnSuccessListener { cast ->
                castContext = cast
                cast.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                cast.sessionManager.currentCastSession?.let(::attach)
                _available.value = true
            }
            .addOnFailureListener { Log.w(TAG, "Cast is unavailable", it) }
    }

    /** Updates what the TV shows; [timer] is null when no timer is open. */
    fun publish(title: String?, timer: TimerState?) {
        this.title = title
        this.timer = timer
        send()
        updateKeepAlive()
    }

    fun release() {
        keepAliveJob?.cancel()
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }

    private fun attach(session: CastSession) {
        this.session = session
        send()
        updateKeepAlive()
    }

    private fun detach() {
        session = null
        updateKeepAlive()
    }

    private fun send() {
        val session = session ?: return
        val message = castMessageJson(title, timer, clock.nowMs())
        session.sendMessage(CAST_NAMESPACE, message).setResultCallback { status ->
            if (!status.isSuccess) Log.w(TAG, "Sending to the receiver failed: $status")
        }
    }

    private fun updateKeepAlive() {
        val needed = session != null && timer?.phase == TimerPhase.Running
        if (!needed) {
            keepAliveJob?.cancel()
            keepAliveJob = null
        } else if (keepAliveJob?.isActive != true) {
            keepAliveJob = scope.launch {
                while (isActive) {
                    delay(KEEP_ALIVE_MS)
                    send()
                }
            }
        }
    }

    private companion object {
        const val TAG = "TimerCaster"
        const val KEEP_ALIVE_MS = 5_000L
    }
}
