package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import kotlin.math.pow

class GenerateSudokuUseCase @Inject constructor(
    private val validateNumber: ValidateNumberUseCase,
) {
    private var random: Random = Random()
    private val initialSudoku9x9 = listOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9,
        4, 5, 6, 7, 8, 9, 1, 2, 3,
        7, 8, 9, 1, 2, 3, 4, 5, 6,
        2, 3, 1, 5, 6, 4, 8, 9, 7,
        5, 6, 4, 8, 9, 7, 2, 3, 1,
        8, 9, 7, 2, 3, 1, 5, 6, 4,
        3, 1, 2, 6, 4, 5, 9, 7, 8,
        6, 4, 5, 9, 7, 8, 3, 1, 2,
        9, 7, 8, 3, 1, 2, 6, 4, 5
    )
    private val initialSudoku4x4 = listOf(
        1, 2, 3, 4,
        3, 4, 1, 2,
        2, 3, 4, 1,
        4, 1, 2, 3
    )

    suspend operator fun invoke(size: Int = 9, difficulty: Difficulty): Sudoku = withContext(Dispatchers.Default) {
        //1.    Find any filled sudoku of sudoku. (use trivial ones will not affect final result)
        val sudoku = generateSudoku(size, difficulty)
        //2.    for each number from 1 to 9 (say num), (i.e 1, 2, 3, 5, 6, 7, 8, 9) take a random number from range [1 to 9],
        //      traverse the sudoku, swap num with your random number.
        shuffleNumbers(sudoku)
        //3.    Now shuffle rows. Take the first group of 3 rows , shuffle them , and do it for all rows. (in 9 X 9 sudoku),
        //      do it for second group and as well as third.
        shuffleRows(sudoku)
        //4.    Swap columns, again take block of 3 columns , shuffle them, and do it for all 3 blocks
        shuffleColumns(sudoku)
        //5.    swap the row blocks itself (ie 3X9 blocks)
        shuffleBlockRows(sudoku)
        //6.    do the same for columns, swap blockwise
        shuffleBlockColumns(sudoku)
        //7.    remove numbers and check if it is solvable.
        removeNumbers(sudoku, difficulty)

        return@withContext sudoku
    }

    private fun generateSudoku(size: Int, difficulty: Difficulty): Sudoku {
        val sudokuId = SudokuId.generate()
        return Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = difficulty,
            fields = MutableList(size) { index ->
                Field(
                    sudokuId = sudokuId,
                    position = Position.create(size, index),
                    value = getInitialSudoku(size)[index],
                    solution = getInitialSudoku(size)[index],
                    given = true,
                )
            })
    }

    private fun getInitialSudoku(size: Int): List<Int> =
        when (size) {
            4 -> initialSudoku4x4
            9 -> initialSudoku9x9
            else -> initialSudoku9x9
        }

    private fun shuffleNumbers(sudoku: Sudoku) {
        for (i in 0 until sudoku.size) {
            val ranNum: Int = Random().nextInt(sudoku.size)
            swapNumbers(sudoku, i, ranNum)
        }
    }

    private fun swapNumbers(sudoku: Sudoku, n1: Int, n2: Int) {
        for (i in 0 until sudoku.size) {
            if (sudoku[i].value == n1) {
                sudoku[i].value = n2
                sudoku[i].solution = n2
            } else if (sudoku[i].value == n2) {
                sudoku[i].value = n1
                sudoku[i].solution = n1
            }
        }
    }

    private fun shuffleRows(sudoku: Sudoku) {
        var blockNumber: Int
        for (i in 0 until sudoku.size) {
            blockNumber = i / sudoku.blockSize
            swapRows(sudoku, i, blockNumber * sudoku.blockSize + random.nextInt(sudoku.blockSize))
        }
    }

    private fun swapRows(sudoku: Sudoku, r1: Int, r2: Int) {
        val row: List<Field> = sudoku.getRow(r1)
        sudoku.setRow(r1, sudoku.getRow(r2))
        sudoku.setRow(r2, row)
    }

    private fun shuffleColumns(sudoku: Sudoku) {
        var blockNumber: Int
        for (i in 0 until sudoku.size) {
            blockNumber = i / sudoku.blockSize
            swapColumns(sudoku, i, blockNumber * sudoku.blockSize + random.nextInt(sudoku.blockSize))
        }
    }

    private fun swapColumns(sudoku: Sudoku, c1: Int, c2: Int) {
        val column: List<Field> = sudoku.getColumn(c1)
        sudoku.setColumn(c1, sudoku.getColumn(c2))
        sudoku.setColumn(c2, column)
    }

    private fun shuffleBlockRows(sudoku: Sudoku) {
        for (i in 0 until sudoku.blockSize) swapBlockRows(sudoku, i, random.nextInt(sudoku.blockSize))
    }

    private fun swapBlockRows(sudoku: Sudoku, r1: Int, r2: Int) {
        for (i in 0 until sudoku.blockSize) swapRows(sudoku, r1 * sudoku.blockSize + i, r2 * sudoku.blockSize + i)
    }

    private fun shuffleBlockColumns(sudoku: Sudoku) {
        for (i in 0 until sudoku.blockSize) swapBlockColumns(sudoku, i, random.nextInt(sudoku.blockSize))
    }

    private fun swapBlockColumns(sudoku: Sudoku, c1: Int, c2: Int) {
        for (i in 0 until sudoku.blockSize) swapColumns(sudoku, c1 * sudoku.blockSize + i, c2 * sudoku.blockSize + i)
    }

    private suspend fun removeNumbers(sudoku: Sudoku, difficulty: Difficulty) {
        val diff = if (difficulty.value == Difficulty.max) -1
        else (sudoku.size.toDouble().pow(2.0) * (difficulty.value * 0.35f / Difficulty.max + 0.4f)).toInt()
        removedRandomNumber(sudoku, diff)
    }

    private suspend fun removedRandomNumber(sudoku: Sudoku, numbersToRemove: Int, tries: Int = 20): Sudoku {
        val index = random.nextInt(sudoku.maxIndex + 1)
        val newSudoku = sudoku.copy()
        return if (newSudoku[index].value != null) {
            newSudoku[index].value = null
            newSudoku[index].given = false
            val solutions = mutableListOf<Sudoku>()
            solveFieldForSolutionGame(newSudoku, Position.first(sudoku.size), solutions)
            if (solutions.size == 1) { //continue while solutions.size() is one
                if (numbersToRemove - 1 == 0) newSudoku else removedRandomNumber(newSudoku, numbersToRemove - 1, tries)
            } else { //try to remove more numbers
                if (tries - 1 > 0) removedRandomNumber(sudoku, numbersToRemove, tries - 1) else sudoku
            }
        } else { //skip already removed numbers
            removedRandomNumber(sudoku, numbersToRemove, tries)
        }
    }

    private suspend fun solveFieldForSolutionGame(sudoku: Sudoku, position: Position, solutions: MutableList<Sudoku>) {
        if (position == Position.last(position.size)) { //reached the end
            if (sudoku[position].value == null) {
                for (n in 1..sudoku.size) {
                    if (validateNumber(sudoku, position, n)) {
                        sudoku[position].value = n
                        sudoku[position].solution = n
                        solutions.add(sudoku) //no errors: add solution
                        return
                    }
                }
            } else {
                solutions.add(sudoku) //no errors: add solution
                return
            }
        }
        else if (sudoku[position].value == null) {
            for (n in 1..sudoku.size) {
                if (validateNumber(sudoku, position, n)) {
                    sudoku[position].value = n
                    sudoku[position].solution = n
                    solveFieldForSolutionGame(sudoku.copy(), position.next(), solutions)
                }
            }
        } else solveFieldForSolutionGame(sudoku, position.next(), solutions)
    }
}
