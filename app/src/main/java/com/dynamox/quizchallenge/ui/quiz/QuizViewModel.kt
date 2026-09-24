package com.dynamox.quizchallenge.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.dynamox.quizchallenge.domain.model.AppError
import com.dynamox.quizchallenge.domain.model.Question
import com.dynamox.quizchallenge.domain.model.QuizSession
import com.dynamox.quizchallenge.domain.usecase.GetUniqueQuestionUseCase
import com.dynamox.quizchallenge.domain.usecase.SaveScoreUseCase
import com.dynamox.quizchallenge.domain.usecase.SubmitAnswerUseCase
import com.dynamox.quizchallenge.ui.navigation.QuizDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getUniqueQuestion: GetUniqueQuestionUseCase,
    private val submitAnswerUseCase: SubmitAnswerUseCase,
    private val saveScoreUseCase: SaveScoreUseCase,
) : ViewModel() {

    private var session = QuizSession.start(savedStateHandle.toRoute<QuizDestination.Quiz>().playerName)
    private var currentQuestion: Question? = null

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    init {
        loadNextQuestion()
    }

    fun onOptionSelected(option: String) {
        val state = _uiState.value as? QuizUiState.InProgress ?: return
        if (state.isAnswerRevealed) return
        _uiState.value = state.copy(selectedOption = option)
    }

    fun onSubmitAnswer() {
        val state = _uiState.value as? QuizUiState.InProgress ?: return
        val option = state.selectedOption ?: return
        val question = currentQuestion ?: return
        _uiState.value = state.copy(isSubmitting = true)
        viewModelScope.launch {
            submitAnswerUseCase(question.id, option).fold(
                onSuccess = { isCorrect -> onAnswerRevealed(state, isCorrect) },
                onFailure = { error -> _uiState.value = QuizUiState.Error(error.toAppError()) },
            )
        }
    }

    /** Called when the player taps "next question" (or "see result" on the last question). */
    fun onNextQuestion() {
        loadNextQuestion()
    }

    /** Called from the error state's retry button; simply attempts to load a question again. */
    fun onRetry() {
        loadNextQuestion()
    }

    private suspend fun onAnswerRevealed(state: QuizUiState.InProgress, isCorrect: Boolean) {
        session = session.withAnswer(state.question.id, isCorrect)
        if (session.isFinished) {
            saveScoreUseCase(session.playerName, session.correctCount, session.totalQuestions)
        }
        _uiState.value = state.copy(
            isSubmitting = false,
            revealedCorrect = isCorrect,
            correctCountSoFar = session.correctCount,
        )
    }

    private fun loadNextQuestion() {
        _uiState.value = QuizUiState.Loading
        viewModelScope.launch {
            getUniqueQuestion(session.seenQuestionIds).fold(
                onSuccess = { question ->
                    currentQuestion = question
                    _uiState.value = QuizUiState.InProgress(
                        questionNumber = session.answeredCount + 1,
                        totalQuestions = session.totalQuestions,
                        question = question,
                    )
                },
                onFailure = { error -> _uiState.value = QuizUiState.Error(error.toAppError()) },
            )
        }
    }

    private fun Throwable.toAppError(): AppError = this as? AppError ?: AppError.Unknown(message)
}
