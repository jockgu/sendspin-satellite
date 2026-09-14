package com.nanopixel.sendspinsatellite.playback

internal class AudioFocusPolicy {
    enum class Action { REQUEST, START, SUSPEND, RESUME, STOP, NONE }

    enum class Change { GAIN, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK, LOSS }

    private var connected = false
    private var suspended = false

    fun onConnect(): Action {
        connected = true
        suspended = false
        return Action.REQUEST
    }

    fun onFocusRequestResult(granted: Boolean): Action {
        if (!connected) return Action.NONE
        if (!granted) {
            connected = false
            suspended = false
            return Action.STOP
        }
        return Action.START
    }

    fun onFocusChange(change: Change): Action {
        if (!connected) return Action.NONE
        return when (change) {
            Change.GAIN -> if (suspended) {
                suspended = false
                Action.RESUME
            } else {
                Action.NONE
            }
            Change.LOSS_TRANSIENT,
            Change.LOSS_TRANSIENT_CAN_DUCK -> if (suspended) {
                Action.NONE
            } else {
                suspended = true
                Action.SUSPEND
            }
            Change.LOSS -> {
                connected = false
                suspended = false
                Action.STOP
            }
        }
    }

    fun onStop(): Action {
        connected = false
        suspended = false
        return Action.STOP
    }
}