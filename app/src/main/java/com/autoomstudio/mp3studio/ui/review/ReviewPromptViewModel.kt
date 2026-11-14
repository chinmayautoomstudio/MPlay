package com.autoomstudio.mp3studio.ui.review

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.review.InAppReviewLauncher
import com.autoomstudio.mp3studio.data.review.ReviewPromptRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReviewPromptViewModel(
    private val repository: ReviewPromptRepository,
    private val launcher: InAppReviewLauncher,
) : ViewModel() {

    val softPromptVisible: StateFlow<Boolean> = repository.softPromptVisible

    fun onSoftPromptShown() = repository.onSoftPromptShown()

    fun onNotNow() {
        viewModelScope.launch { repository.dismiss() }
    }

    fun onRate(activity: Activity) {
        viewModelScope.launch {
            repository.markCompleted()
            launcher.launch(activity)
        }
    }

    fun onDismissRequest() = repository.clearSoftPrompt()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MPlayApp).container
                ReviewPromptViewModel(
                    container.reviewPromptRepository,
                    container.inAppReviewLauncher,
                )
            }
        }
    }
}
