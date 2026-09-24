package com.dynamox.quizchallenge.data.repository

import com.dynamox.quizchallenge.data.remote.QuizApi
import com.dynamox.quizchallenge.data.remote.dto.AnswerRequestDto
import com.dynamox.quizchallenge.data.remote.dto.toDomain
import com.dynamox.quizchallenge.data.remote.safeApiCall
import com.dynamox.quizchallenge.domain.model.Question
import com.dynamox.quizchallenge.domain.repository.QuizRepository
import javax.inject.Inject

class QuizRepositoryImpl @Inject constructor(
    private val api: QuizApi,
) : QuizRepository {

    override suspend fun fetchQuestion(): Result<Question> = safeApiCall {
        api.getQuestion().toDomain()
    }

    override suspend fun submitAnswer(questionId: String, answer: String): Result<Boolean> =
        safeApiCall {
            api.submitAnswer(questionId, AnswerRequestDto(answer)).result
        }
}
