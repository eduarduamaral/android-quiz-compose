package com.dynamox.quizchallenge.fakes

import com.dynamox.quizchallenge.domain.model.AppError
import com.dynamox.quizchallenge.domain.model.Question
import com.dynamox.quizchallenge.domain.repository.QuizRepository
import java.util.ArrayDeque

/** In-memory [QuizRepository] test double: enqueue questions/results, no network involved. */
class FakeQuizRepository : QuizRepository {
    private val questionQueue = ArrayDeque<Question>()
    var answerResult: Boolean = true
    var nextFailure: AppError? = null

    fun enqueueQuestion(question: Question) {
        questionQueue.addLast(question)
    }

    override suspend fun fetchQuestion(): Result<Question> {
        nextFailure?.let { return Result.failure(it) }
        val question = questionQueue.pollFirst() ?: error("Test bug: no fake question was enqueued")
        return Result.success(question)
    }

    override suspend fun submitAnswer(questionId: String, answer: String): Result<Boolean> {
        nextFailure?.let { return Result.failure(it) }
        return Result.success(answerResult)
    }
}
