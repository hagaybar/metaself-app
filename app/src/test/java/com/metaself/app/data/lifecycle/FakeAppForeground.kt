package com.metaself.app.data.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow

/** An [AppForeground] a test sets by hand; in the foreground unless told otherwise. */
class FakeAppForeground(initial: Boolean = true) : AppForeground {
    override val isForeground = MutableStateFlow(initial)
}
