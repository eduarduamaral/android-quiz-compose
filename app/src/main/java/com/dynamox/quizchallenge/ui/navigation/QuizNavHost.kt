package com.dynamox.quizchallenge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dynamox.quizchallenge.ui.history.HistoryScreen
import com.dynamox.quizchallenge.ui.nameentry.NameEntryScreen
import com.dynamox.quizchallenge.ui.quiz.QuizScreen
import com.dynamox.quizchallenge.ui.result.ResultScreen

@Composable
fun QuizNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = QuizDestination.NameEntry) {
        composable<QuizDestination.NameEntry> {
            NameEntryScreen(
                onStartQuiz = { name -> navController.navigate(QuizDestination.Quiz(name)) },
                onViewHistory = { navController.navigate(QuizDestination.History) },
            )
        }

        composable<QuizDestination.Quiz> { backStackEntry ->
            val args = backStackEntry.toRoute<QuizDestination.Quiz>()
            QuizScreen(
                onFinished = { correctCount, totalQuestions ->
                    navController.navigate(
                        QuizDestination.Result(args.playerName, correctCount, totalQuestions),
                    ) {
                        popUpTo<QuizDestination.NameEntry>()
                    }
                },
            )
        }

        composable<QuizDestination.Result> { backStackEntry ->
            val args = backStackEntry.toRoute<QuizDestination.Result>()
            ResultScreen(
                playerName = args.playerName,
                correctCount = args.correctCount,
                totalQuestions = args.totalQuestions,
                onRestart = {
                    navController.navigate(QuizDestination.Quiz(args.playerName)) {
                        popUpTo<QuizDestination.NameEntry>()
                    }
                },
                onChangePlayer = {
                    navController.navigate(QuizDestination.NameEntry) {
                        popUpTo<QuizDestination.NameEntry> { inclusive = true }
                    }
                },
                onViewHistory = { navController.navigate(QuizDestination.History) },
            )
        }

        composable<QuizDestination.History> {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
