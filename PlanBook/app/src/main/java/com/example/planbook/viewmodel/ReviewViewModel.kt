package com.example.planbook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.planbook.data.PlanBookRepository
import com.example.planbook.model.Notebook
import com.example.planbook.model.Review
import com.example.planbook.model.Task
import com.example.planbook.model.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 复盘 Tab：月历与有内容日期 */
data class ReviewCalendarUiState(
    val currentNotebook: Notebook? = null,
    val reviewDates: Set<String> = emptySet(),  // 有复盘内容的日期 yyyy-MM-dd
    val month: LocalDate = LocalDate.now().withDayOfMonth(1)
)

/** 复盘编辑页状态 */
data class ReviewEditUiState(
    val date: String = LocalDate.now().toString(),
    val content: String = "",
    val completedTasks: List<Task> = emptyList(),
    val longTermTasks: List<Task> = emptyList(),
    val loaded: Boolean = false
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: PlanBookRepository
) : ViewModel() {

    private val _calendarState = MutableStateFlow(ReviewCalendarUiState())
    val calendarState: StateFlow<ReviewCalendarUiState> = _calendarState.asStateFlow()

    private val _editState = MutableStateFlow(ReviewEditUiState())
    val editState: StateFlow<ReviewEditUiState> = _editState.asStateFlow()

    init {
        // 跟踪当前计划本 + 该计划本所有复盘日期
        viewModelScope.launch {
            repository.getCurrentNotebook().collect { notebook ->
                _calendarState.update { it.copy(currentNotebook = notebook) }
                notebook?.let { loadReviewDates(it.id) }
            }
        }
    }

    private fun loadReviewDates(notebookId: Long) {
        viewModelScope.launch {
            repository.getReviewsByNotebook(notebookId).collect { reviews ->
                _calendarState.update {
                    it.copy(reviewDates = reviews.filter { r -> r.content.isNotBlank() }.map { r -> r.date }.toSet())
                }
            }
        }
    }

    fun changeMonth(delta: Int) {
        _calendarState.update { it.copy(month = it.month.plusMonths(delta.toLong())) }
    }

    /** 打开某日的复盘编辑页：加载内容 + 当日已完成任务 + 待细化长期任务（PRD 4.4.4） */
    fun openReviewEdit(date: String) {
        val notebook = _calendarState.value.currentNotebook ?: return
        _editState.update { it.copy(date = date, loaded = false) }
        viewModelScope.launch {
            val review = repository.getReview(notebook.id, date)
            val completed = repository.getCompletedTasksForDate(notebook.id, date)
            // DDL 未过的长期任务（PRD 4.4.4：待细化）
            val longTerm = repository.getLongTermTasksActive(notebook.id, date)
            _editState.update {
                it.copy(
                    content = review?.content ?: "",
                    completedTasks = completed,
                    longTermTasks = longTerm,
                    loaded = true
                )
            }
        }
    }

    /** 自动保存：停止输入后调用（PRD 4.4.4：停止输入 1 秒后保存） */
    fun saveReviewContent(content: String) {
        _editState.update { it.copy(content = content) }
        val notebook = _calendarState.value.currentNotebook ?: return
        val date = _editState.value.date
        viewModelScope.launch {
            repository.saveReview(
                Review(notebookId = notebook.id, date = date, content = content)
            )
        }
    }

    /** 长期任务拆分为临时/灵活任务，原长期保留（PRD 4.4.4） */
    fun splitLongTermTask(task: Task, asFlex: Boolean) {
        val notebook = _calendarState.value.currentNotebook ?: return
        viewModelScope.launch {
            repository.addTask(
                task.copy(
                    id = 0,
                    notebookId = notebook.id,
                    type = if (asFlex) TaskType.FLEX else TaskType.ONE_OFF,
                    startTime = if (asFlex) null else task.startTime,
                    endTime = if (asFlex) null else task.endTime
                )
            )
        }
    }
}
