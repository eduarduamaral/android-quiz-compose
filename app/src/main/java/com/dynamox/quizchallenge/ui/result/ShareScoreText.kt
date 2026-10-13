package com.dynamox.quizchallenge.ui.result

/**
 * Builds the plain-text message for the system share sheet. [template] is resolved by the caller
 * (via `stringResource`) so this stays a Context-free function that is trivial to unit test.
 */
fun buildShareMessage(
    template: String,
    playerName: String,
    correctCount: Int,
    totalQuestions: Int,
): String = String.format(template, playerName, correctCount, totalQuestions)
