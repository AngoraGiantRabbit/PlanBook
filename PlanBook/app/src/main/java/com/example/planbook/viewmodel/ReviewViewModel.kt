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
    /** 主计划本（ADR-0004：复盘挂主计划本） */
    val currentNotebook: Notebook? = null,
    /** 全部子计划本（当日任务查询用） */
    val subNotebooks: List<Notebook> = emptyList(),
    /** 活动子计划本（复盘页拆分长期任务时，子任务落到它） */
    val activeSubNotebook: Notebook? = null,
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
        // 跟踪主计划本 + 子计划本 + 活动子本（ADR-0004）；复盘日期挂主计划本
        viewModelScope.launch {
            combine(
                repository.getMasterNotebook(),
                repository.getSubNotebooksOfMaster(),
                repository.getActiveSubOfMaster()
            ) { master, subs, active -> Triple(master, subs, active) }
                .collect { (master, subs, active) ->
                    _calendarState.update {
                        it.copy(currentNotebook = master, subNotebooks = subs, activeSubNotebook = active)
                    }
                    master?.let { loadReviewDates(it.id) }
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
        val subIds = _calendarState.value.subNotebooks.map { it.id }
        _editState.update { it.copy(date = date, loaded = false) }
        viewModelScope.launch {
            val review = repository.getReview(notebook.id, date)
            val completed = repository.getCompletedTasksForDate(notebook.id, subIds, date)
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

    /** 长期任务拆分：用拆分出的子任务（用户已编辑好的）新建一条，落到活动子计划本，原长期保留（PRD 4.4.4） */
    fun splitLongTermTask(subTask: Task) {
        val master = _calendarState.value.currentNotebook ?: return
        val activeSubId = _calendarState.value.activeSubNotebook?.id ?: master.id
        viewModelScope.launch {
            repository.addTask(subTask.copy(id = 0, notebookId = activeSubId))
            // 刷新编辑页，长期任务列表可能变化
            openReviewEdit(_editState.value.date)
        }
    }
}
