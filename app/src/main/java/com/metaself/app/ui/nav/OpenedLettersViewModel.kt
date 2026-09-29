package com.metaself.app.ui.nav

import androidx.lifecycle.ViewModel
import com.metaself.app.data.letter.LetterOpen
import com.metaself.app.data.letter.OpenedLetters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Whether a weekly letter notification's tap is waiting (D103), so the navigation can open it, and the taking of it. */
@HiltViewModel
class OpenedLettersViewModel @Inject constructor(private val opened: OpenedLetters) : ViewModel() {
    val pending: StateFlow<LetterOpen?> = opened.pending

    /** The waiting open, handed out once. */
    fun take(): LetterOpen? = opened.take()
}
