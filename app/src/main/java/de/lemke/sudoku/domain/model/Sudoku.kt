package de.lemke.sudoku.domain.model

import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import java.time.LocalDateTime
import java.util.*
import kotlin.math.sqrt

@JvmInline
value class SudokuId(val value: String) {
    companion object {
        fun generate(): SudokuId = SudokuId(UUID.randomUUID().toString())
    }
}

class Sudoku(
    val id: SudokuId,
    val size: Int,
    val history: MutableList<HistoryItem>,
    val difficulty: Difficulty,
    var hintsUsed: Int,
    var notesMade: Int,
    var errorsMade: Int,
    var seconds: Int,
    var timer: Timer?,
    var gameListener: GameListener?,
    val created: LocalDateTime,
    var updated: LocalDateTime,
    val fields: MutableList<Field>,
    var regionalHighlightingUsed: Boolean,
    var numberHighlightingUsed: Boolean,
    var autoNotesUsed: Boolean,
    val modeLevel: Int,
) {
    companion object {
        const val MODE_NORMAL = 0
        const val MODE_DAILY = -1
        const val MODE_LEVEL_ERROR_LIMIT = 3
        const val MODE_DAILY_ERROR_LIMIT = 3

        fun create(
            sudokuId: SudokuId = SudokuId.generate(),
            size: Int,
            history: MutableList<HistoryItem> = mutableListOf(),
            difficulty: Difficulty,
            hintsUsed: Int = 0,
            notesMade: Int = 0,
            errorsMade: Int = 0,
            seconds: Int = 0,
            timer: Timer? = null,
            gameListener: GameListener? = null,
            created: LocalDateTime = LocalDateTime.now(),
            updated: LocalDateTime = LocalDateTime.now(),
            fields: MutableList<Field>,
            regionalHighlightingUsed: Boolean = false,
            numberHighlightingUsed: Boolean = false,
            autoNotessUsed: Boolean = false,
            modeLevel: Int,
        ): Sudoku = Sudoku(
            id = sudokuId,
            size = size,
            history = history,
            difficulty = difficulty,
            hintsUsed = hintsUsed,
            notesMade = notesMade,
            errorsMade = errorsMade,
            seconds = seconds,
            timer = timer,
            gameListener = gameListener,
            created = created,
            updated = updated,
            fields = fields,
            regionalHighlightingUsed = regionalHighlightingUsed,
            numberHighlightingUsed = numberHighlightingUsed,
            autoNotesUsed = autoNotessUsed,
            modeLevel = modeLevel,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sudoku

        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id.hashCode()

    val isSudokuLevel: Boolean
        get() = modeLevel > 0

    val isDailySudoku: Boolean
        get() = modeLevel == MODE_DAILY

    val isNormalSudoku: Boolean
        get() = modeLevel == MODE_NORMAL

    val errors: Int
        get() = fields.count { it.error }

    val filled: Boolean
        get() = fields.all { it.value != null }

    val completed: Boolean
        get() = fields.all { !it.error && it.value != null }

    val resumed: Boolean
        get() = timer != null

    val progress: Int
        get() {
            val total = fields.count { !it.given }
            return fields.count { !it.given && it.correct } * 100 / total
        }

    val itemCount: Int
        get() = (this.size * this.size)

    val blockSize: Int
        get() = sqrt(this.size.toDouble()).toInt()

    val timeString: String
        get() = if (seconds >= 3600) String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

    val sizeString: String
        get() = "$size×$size"

    fun errorLimitReached(errorLimit: Int): Boolean = if (errorLimit == 0) false else errorsMade >= errorLimit

    fun reset() {
        fields.forEach { it.reset() }
        history.clear()
        hintsUsed = 0
        errorsMade = 0
        seconds = 0
        notesMade = 0
        regionalHighlightingUsed = false
        numberHighlightingUsed = false
        autoNotesUsed = false
        timer?.cancel()
        timer = null
        gameListener = null
    }

    fun startTimer(delay: Long = 1500) {
        if (completed) return
        timer?.cancel()
        timer = Timer()
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                seconds++
                updated = LocalDateTime.now()
                gameListener?.onTimeChanged()
            }
        }, delay, 1000)
    }

    fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    fun move(index: Int, value: Int?, isNote: Boolean = false) = move(Position.create(index, size), value, isNote)

    fun move(position: Position, value: Int?, isNote: Boolean = false): Boolean {
        val field = get(position)
        if (field.given || field.hint || field.value == value && value != null || field.value != null && value != null) return false
        if (isNote) {
            if (value != null) {
                if (field.toggleNote(value)) notesMade++
            } else field.notes.clear()
            gameListener?.onFieldChanged(position)
        } else {
            if (field.value == null && value == null) field.notes.clear()
            else history.add(HistoryItem(position, if (value == null) field.value else null))
            field.value = value
            gameListener?.onFieldChanged(position)
            gameListener?.onHistoryChange(history.size)
            if (value != null) {
                if (field.error) {
                    errorsMade++
                    gameListener?.onError()
                } else {
                    removeNumberNotesFromNeighbors(position, value)
                }
                if (completed) {
                    stopTimer()
                    gameListener?.onCompleted(position)
                }
            }
        }
        return true
    }

    private fun removeNumberNotesFromNeighbors(position: Position, value: Int?) {
        get(position).notes.clear()
        gameListener?.onFieldChanged(position)
        getNeighbors(position).forEach {
            if (it.notes.remove(value)) gameListener?.onFieldChanged(it.position)
        }
    }

    fun setHint(index: Int) = setHint(Position.create(index, size))

    fun setHint(position: Position) {
        hintsUsed++
        get(position).setHint()
        gameListener?.onFieldChanged(position)
        removeNumberNotesFromNeighbors(position, get(position).value)
        if (completed) {
            stopTimer()
            gameListener?.onCompleted(position)
        }
    }

    fun revertLastChange(adapter: SudokuViewAdapter) {
        if (history.size != 0) {
            val item = history.removeAt(history.lastIndex)
            get(item.position.index).value = item.deletedNumber
            adapter.updateFieldView(item.position.index)
            gameListener?.onHistoryChange(history.size)
            gameListener?.onFieldChanged(item.position)
        }
    }

    operator fun get(position: Position): Field = fields[position.index]
    operator fun set(position: Position, field: Field) {
        fields[position.index] = field.clone(position = position)
    }

    operator fun get(index: Int): Field = fields[index]
    operator fun set(index: Int, field: Field) {
        fields[index] = field.clone(position = Position.create(index, size))
    }

    operator fun get(row: Int, column: Int) = fields[Position.create(size = size, row = row, column = column).index]
    operator fun set(row: Int, column: Int, field: Field) {
        fields[Position.create(size = size, row = row, column = column).index] =
            field.clone(position = Position.create(size = size, row = row, column = column))
    }

    private fun getRow(row: Int): List<Field> = fields.filter { it.position.row == row }
    private fun getColumn(column: Int): List<Field> = fields.filter { it.position.column == column }
    private fun getBlock(block: Int): List<Field> = fields.filter { it.position.block == block }
    private fun getNeighbors(position: Position): List<Field> = getRow(position.row) + getColumn(position.column) + getBlock(position.block)
    fun getNeighbors(index: Int): List<Field> = getNeighbors(Position.create(index, size))

    fun isRowCompleted(row: Int): Boolean = getRow(row).all { it.correct }
    fun isColumnCompleted(column: Int): Boolean = getColumn(column).all { it.correct }
    fun isBlockCompleted(block: Int): Boolean = getBlock(block).all { it.correct }

    private fun getPossibleValues(position: Position): List<Int> {
        val values = (1..size).toMutableList()
        getRow(position.row).forEach { values.remove(it.value) }
        getColumn(position.column).forEach { values.remove(it.value) }
        getBlock(position.block).forEach { values.remove(it.value) }
        return values
    }

    fun getCompletedNumbers(): List<Pair<Int, Boolean>> {
        val numbers = MutableList(size) { 0 }
        fields.forEach { field ->
            if (field.correct) numbers[field.value!! - 1]++
        }
        return numbers.mapIndexed { index, i -> Pair(index + 1, i >= size) }
    }

    fun autoNotes() {
        autoNotesUsed = true
        fields.forEach { field ->
            if (field.value == null) {
                field.notes.clear()
                field.notes.addAll(getPossibleValues(field.position))
                gameListener?.onFieldChanged(field.position)
            }
        }
    }

    fun clearAllNotes() {
        fields.forEach { field ->
            field.notes.clear()
            gameListener?.onFieldChanged(field.position)
        }
    }


}

interface GameListener {
    fun onHistoryChange(length: Int)
    fun onFieldClicked(position: Position)
    fun onFieldChanged(position: Position)
    fun onCompleted(position: Position)
    fun onError()
    fun onTimeChanged()
}

