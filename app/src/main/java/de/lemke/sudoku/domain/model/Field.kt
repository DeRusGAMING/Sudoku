package de.lemke.sudoku.domain.model

data class Field(
    val sudokuId: SudokuId,
    val position: Position,
    var value: Int? = null,
    var solution: Int? = null,
    val notes: MutableList<Int> = mutableListOf(),
    var given: Boolean = false,
    var hint: Boolean = false,
) {
    val error: Boolean
        get() = value != null && value != solution

    fun toggleNote(note: Int) {
        if (!notes.remove(note)) {
            notes.add(note)
            notes.sort()
        }
    }

    fun setHint() {
        hint = true
        value = solution
    }

    fun clone(
        sudokuId: SudokuId = this.sudokuId,
        position: Position = this.position,
        value: Int? = this.value,
        solution: Int? = this.solution,
        notes: MutableList<Int> = ArrayList(this.notes),
        preNumber: Boolean = this.given,
        hint: Boolean = this.hint,
    ): Field = Field(sudokuId, position, value, solution, notes, preNumber, hint)

    fun cloneInitial(
        sudokuId: SudokuId,
        position: Position = this.position,
        value: Int? = this.value,
        solution: Int? = this.solution,
        preNumber: Boolean = this.given,
    ): Field = if (this.given) Field(
        sudokuId = sudokuId,
        position = position,
        value = value,
        solution = solution,
        given = preNumber,
        notes = mutableListOf(),
        hint = false,
    )
    else Field(
        sudokuId = sudokuId,
        position = position,
        value = null,
        solution = solution,
        given = preNumber,
        notes = mutableListOf(),
        hint = false,
    )

    fun reset() {
        if (!given) value = null
        hint = false
        notes.clear()
    }
}