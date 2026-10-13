package com.dynamox.quizchallenge.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamox.quizchallenge.domain.model.AppError
import com.dynamox.quizchallenge.domain.model.Question
import com.dynamox.quizchallenge.domain.model.QuizSession
import com.dynamox.quizchallenge.domain.usecase.GetUniqueQuestionUseCase
import com.dynamox.quizchallenge.domain.usecase.SaveScoreUseCase
import com.dynamox.quizchallenge.domain.usecase.SubmitAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getUniqueQuestion: GetUniqueQuestionUseCase,
    private val submitAnswerUseCase: SubmitAnswerUseCase,
    private val saveScoreUseCase: SaveScoreUseCase,
) : ViewModel() {

    // Progress lives in SavedStateHandle so a quiz survives process death, not just rotation.
    // The current question is not persisted: init re-fetches one for the restored session.
    private var session = restoreSession()

    // Prevents a double-tap on "next"/"retry" from starting two overlapping fetches.
    private var isFetchingQuestion = false

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
        // isAnswerRevealed only flips after the network call resolves; isSubmitting covers the gap.
        if (state.isAnswerRevealed || state.isSubmitting) return
        val option = state.selectedOption ?: return
        submitAnswer(state, option)
    }

    /** Called when the player taps "next question" (or "see result" on the last question). */
    fun onNextQuestion() {
        loadNextQuestion()
    }

    /** Called from the error state's retry button; replays whichever operation failed. */
    fun onRetry() {
        val action = (_uiState.value as? QuizUiState.Error)?.retryAction ?: return
        when (action) {
            RetryAction.LoadQuestion -> loadNextQuestion()
            is RetryAction.SubmitAnswer -> submitAnswer(inProgressState(action.question), action.option)
        }
    }

    private fun submitAnswer(state: QuizUiState.InProgress, option: String) {
        _uiState.value = state.copy(selectedOption = option, isSubmitting = true)
        viewModelScope.launch {
            submitAnswerUseCase(state.question.id, option).fold(
                onSuccess = { isCorrect -> onAnswerRevealed(state, isCorrect) },
                onFailure = { error ->
                    _uiState.value = QuizUiState.Error(
                        error = error.toAppError(),
                        retryAction = RetryAction.SubmitAnswer(state.question, option),
                    )
                },
            )
        }
    }

    private suspend fun onAnswerRevealed(state: QuizUiState.InProgress, isCorrect: Boolean) {
        session = session.withAnswer(state.question.id, isCorrect)
        persistSession()
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
        if (isFetchingQuestion) return
        isFetchingQuestion = true
        _uiState.value = QuizUiState.Loading
        viewModelScope.launch {
            getUniqueQuestion(session.seenQuestionIds).fold(
                onSuccess = { question ->
                    isFetchingQuestion = false
                    _uiState.value = inProgressState(question)
                },
                onFailure = { error ->
                    isFetchingQuestion = false
                    _uiState.value = QuizUiState.Error(error.toAppError(), RetryAction.LoadQuestion)
                },
            )
        }
    }

    private fun inProgressState(question: Question) = QuizUiState.InProgress(
        questionNumber = session.answeredCount + 1,
        totalQuestions = session.totalQuestions,
        question = question,
    )

    private fun restoreSession(): QuizSession {
        // Read by key rather than SavedStateHandle.toRoute(): the latter goes through
        // android.os.Bundle and fails in plain JVM unit tests.
        val playerName = checkNotNull(savedStateHandle.get<String>(PLAYER_NAME_ARG)) {
            "Missing '$PLAYER_NAME_ARG' navigation argument"
        }
        return QuizSession(
            playerName = playerName,
            answeredCount = savedStateHandle.get<Int>(KEY_ANSWERED_COUNT) ?: 0,
            correctCount = savedStateHandle.get<Int>(KEY_CORRECT_COUNT) ?: 0,
            seenQuestionIds = savedStateHandle.get<ArrayList<String>>(KEY_SEEN_QUESTION_IDS)?.toSet() ?: emptySet(),
        )
    }

    private fun persistSession() {
        savedStateHandle[KEY_ANSWERED_COUNT] = session.answeredCount
        savedStateHandle[KEY_CORRECT_COUNT] = session.correctCount
        savedStateHandle[KEY_SEEN_QUESTION_IDS] = ArrayList(session.seenQuestionIds)
    }

    private companion object {
        const val PLAYER_NAME_ARG = "playerName"
        const val KEY_ANSWERED_COUNT = "quiz_answeredCount"
        const val KEY_CORRECT_COUNT = "quiz_correctCount"
        const val KEY_SEEN_QUESTION_IDS = "quiz_seenQuestionIds"
    }
}

private fun Throwable.toAppError(): AppError = this as? AppError ?: AppError.Unknown(message)
