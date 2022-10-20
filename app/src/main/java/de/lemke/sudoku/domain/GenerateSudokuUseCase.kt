package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

class GenerateSudokuUseCase @Inject constructor(
    private val generateFields: GenerateFieldsUseCase,
) {
    suspend operator fun invoke(size: Int = 9, difficulty: Difficulty): Sudoku = withContext(Dispatchers.Default) {
        val sudokuId = SudokuId.generate()
        return@withContext Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = difficulty,
            fields = generateFields(size, difficulty, sudokuId),
            modeLevel = Sudoku.MODE_NORMAL,
        )
    }
}
