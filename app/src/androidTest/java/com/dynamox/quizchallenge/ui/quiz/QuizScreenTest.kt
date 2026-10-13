package com.dynamox.quizchallenge.ui.quiz

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dynamox.quizchallenge.R
import com.dynamox.quizchallenge.domain.model.AppError
import com.dynamox.quizchallenge.domain.model.Question
import com.dynamox.quizchallenge.ui.theme.QuizChallengeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders the stateless [QuizContent] directly, so no ViewModel or Hilt graph is involved. */
@RunWith(AndroidJUnit4::class)
class QuizScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val question = Question(id = "1", statement = "What is 2 + 2?", options = listOf("3", "4", "5"))

    private fun inProgress(
        selectedOption: String? = null,
        revealedCorrect: Boolean? = null,
        questionNumber: Int = 1,
    ) = QuizUiState.InProgress(
        questionNumber = questionNumber,
        totalQuestions = 10,
        question = question,
        selectedOption = selectedOption,
        revealedCorrect = revealedCorrect,
    )

    private fun string(resId: Int, vararg args: Any) = context.getString(resId, *args)

    private fun setContent(
        state: QuizUiState,
        onOptionSelected: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onNext: (QuizUiState.InProgress) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuizChallengeTheme {
                QuizContent(
                    uiState = state,
                    onOptionSelected = onOptionSelected,
                    onSubmit = onSubmit,
                    onNext = onNext,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun loadingState_showsIndeterminateProgress() {
        setContent(QuizUiState.Loading)

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun inProgressState_rendersCounterStatementAndOptions() {
        setContent(inProgress(questionNumber = 3))

        composeRule.onNodeWithText(string(R.string.quiz_question_counter, 3, 10)).assertIsDisplayed()
        composeRule.onNodeWithText(question.statement).assertIsDisplayed()
        question.options.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun tappingAnOption_reportsItsText() {
        var selected: String? = null
        setContent(inProgress(), onOptionSelected = { selected = it })

        composeRule.onNodeWithText("4").performClick()

        assertEquals("4", selected)
    }

    @Test
    fun submitButton_isDisabledUntilAnOptionIsSelected() {
        setContent(inProgress())

        composeRule.onNodeWithText(string(R.string.quiz_submit_button)).assertIsNotEnabled()
    }

    @Test
    fun submitButton_callsOnSubmitWhenAnOptionIsSelected() {
        var submitted = false
        setContent(inProgress(selectedOption = "4"), onSubmit = { submitted = true })

        composeRule.onNodeWithText(string(R.string.quiz_submit_button)).assertIsEnabled().performClick()

        assertTrue(submitted)
    }

    @Test
    fun correctAnswer_showsSuccessFeedbackAndNextButton() {
        var nextState: QuizUiState.InProgress? = null
        val state = inProgress(selectedOption = "4", revealedCorrect = true)
        setContent(state, onNext = { nextState = it })

        composeRule.onNodeWithText(string(R.string.quiz_correct_feedback)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.quiz_next_button)).performClick()

        assertEquals(state, nextState)
    }

    @Test
    fun incorrectAnswer_showsFailureFeedback() {
        setContent(inProgress(selectedOption = "3", revealedCorrect = false))

        composeRule.onNodeWithText(string(R.string.quiz_incorrect_feedback)).assertIsDisplayed()
    }

    @Test
    fun lastQuestion_showsFinishButtonAfterReveal() {
        setContent(inProgress(selectedOption = "4", revealedCorrect = true, questionNumber = 10))

        composeRule.onNodeWithText(string(R.string.quiz_finish_button)).assertIsDisplayed()
    }

    @Test
    fun errorState_showsMessageAndRetryCallsOnRetry() {
        var retried = false
        setContent(QuizUiState.Error(AppError.Network, RetryAction.LoadQuestion), onRetry = { retried = true })

        composeRule.onNodeWithText(string(R.string.error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_retry_button)).performClick()

        assertTrue(retried)
    }

    @Test
    fun errorState_doesNotRenderTheQuestion() {
        setContent(QuizUiState.Error(AppError.ServerError, RetryAction.SubmitAnswer(question, "4")))

        composeRule.onNodeWithText(question.statement).assertDoesNotExist()
    }
}
