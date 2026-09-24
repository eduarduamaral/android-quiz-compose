package com.dynamox.quizchallenge.ui.quiz

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dynamox.quizchallenge.domain.model.AppError
import com.dynamox.quizchallenge.domain.model.Question
import com.dynamox.quizchallenge.domain.usecase.GetUniqueQuestionUseCase
import com.dynamox.quizchallenge.domain.usecase.SaveScoreUseCase
import com.dynamox.quizchallenge.domain.usecase.SubmitAnswerUseCase
import com.dynamox.quizchallenge.fakes.FakeQuizRepository
import com.dynamox.quizchallenge.fakes.FakeScoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var quizRepository: FakeQuizRepository
    private lateinit var scoreRepository: FakeScoreRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        quizRepository = FakeQuizRepository()
        scoreRepository = FakeScoreRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun question(id: String) = Question(id = id, statement = "Statement $id", options = listOf("A", "B"))

    private fun createViewModel(playerName: String = "Ada"): QuizViewModel = QuizViewModel(
        savedStateHandle = SavedStateHandle(mapOf("playerName" to playerName)),
        getUniqueQuestion = GetUniqueQuestionUseCase(quizRepository),
        submitAnswerUseCase = SubmitAnswerUseCase(quizRepository),
        saveScoreUseCase = SaveScoreUseCase(scoreRepository),
    )

    @Test
    fun `loads the first question on init`() = runTest {
        quizRepository.enqueueQuestion(question("1"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(QuizUiState.Loading, awaitItem())
            val state = awaitItem() as QuizUiState.InProgress
            assertEquals(1, state.questionNumber)
            assertEquals("1", state.question.id)
        }
    }

    @Test
    fun `selecting and submitting a correct answer reveals success`() = runTest {
        quizRepository.enqueueQuestion(question("1"))
        quizRepository.answerResult = true
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // InProgress, unanswered

            viewModel.onOptionSelected("A")
            val selected = awaitItem() as QuizUiState.InProgress
            assertEquals("A", selected.selectedOption)

            viewModel.onSubmitAnswer()
            awaitItem() // isSubmitting = true
            val revealed = awaitItem() as QuizUiState.InProgress
            assertEquals(true, revealed.revealedCorrect)
            assertEquals(1, revealed.correctCountSoFar)
        }
    }

    @Test
    fun `an incorrect answer is revealed without incrementing the score`() = runTest {
        quizRepository.enqueueQuestion(question("1"))
        quizRepository.answerResult = false
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // InProgress, unanswered
            viewModel.onOptionSelected("A")
            awaitItem() // selected
            viewModel.onSubmitAnswer()
            awaitItem() // isSubmitting = true
            val revealed = awaitItem() as QuizUiState.InProgress
            assertEquals(false, revealed.revealedCorrect)
            assertEquals(0, revealed.correctCountSoFar)
        }
    }

    @Test
    fun `saves the final score once the tenth question is answered`() = runTest {
        repeat(10) { quizRepository.enqueueQuestion(question(it.toString())) }
        quizRepository.answerResult = true
        val viewModel = createViewModel(playerName = "Ada")

        viewModel.uiState.test {
            repeat(10) { index ->
                awaitItem() // Loading
                awaitItem() // InProgress, unanswered
                viewModel.onOptionSelected("A")
                awaitItem() // selected
                viewModel.onSubmitAnswer()
                awaitItem() // isSubmitting = true
                val revealed = awaitItem() as QuizUiState.InProgress
                assertEquals(index == 9, revealed.isLastQuestion)
                if (index < 9) viewModel.onNextQuestion()
            }
        }

        val saved = scoreRepository.savedScores.single()
        assertEquals("Ada", saved.playerName)
        assertEquals(10, saved.correctCount)
        assertEquals(10, saved.totalQuestions)
    }

    @Test
    fun `emits an error state when fetching a question fails`() = runTest {
        quizRepository.nextFailure = AppError.Network
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Loading
            val error = awaitItem() as QuizUiState.Error
            assertEquals(AppError.Network, error.error)
        }
    }

    @Test
    fun `retrying after an error loads a question again`() = runTest {
        quizRepository.nextFailure = AppError.Network
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // Error

            quizRepository.nextFailure = null
            quizRepository.enqueueQuestion(question("1"))
            viewModel.onRetry()

            awaitItem() // Loading
            val state = awaitItem() as QuizUiState.InProgress
            assertEquals("1", state.question.id)
        }
    }
}
