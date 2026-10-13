package com.dynamox.quizchallenge.domain.usecase

import com.dynamox.quizchallenge.domain.model.Question
import com.dynamox.quizchallenge.domain.repository.QuizRepository
import javax.inject.Inject

/**
 * Fetches the next question, avoiding repeats within a run. The API returns a random question on
 * every call and never excludes ones already served, so this retries a few times on a repeat and
 * falls back to the last result rather than looping forever.
 */
class GetUniqueQuestionUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
) {
    suspend operator fun invoke(seenQuestionIds: Set<String>): Result<Question> {
        var lastResult = quizRepository.fetchQuestion()
        repeat(MAX_ATTEMPTS - 1) {
            val question = lastResult.getOrNull()
            val isFailure = lastResult.isFailure
            if (isFailure || question == null || question.id !in seenQuestionIds) {
                return lastResult
            }
            lastResult = quizRepository.fetchQuestion()
        }
        return lastResult
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
