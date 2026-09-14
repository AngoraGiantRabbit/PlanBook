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

    /** 编辑页日期驱动源：任务观察管道按它切换 */
    private val editDateState = MutableStateFlow<String?>(null)

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
        // 管道 2：编辑页任务数据（三页联动）——当日已完成 + 待细化长期任务，
        // tasks/task_completions 任何写操作（含计划本/待办页勾选）即时重发
        viewModelScope.launch {
            combine(
                repository.getMasterNotebook(),
                repository.getSubNotebooksOfMaster(),
                editDateState
            ) { master, subs, date -> Triple(master, subs, date) }
                .flatMapLatest { (master, subs, date) ->
                    if (master == null || date == null) flowOf(null)
                    else combine(
                        repository.observeExpandedTasks(
                            master.id,
                            subs.filter { it.isVisible }.map { it.id },
                            listOf(LocalDate.parse(date))
                        ),
                        repository.observeLongTermActive(master.id, LocalDate.now().toString())
                    ) { expanded, longTerm ->
                        expanded.filter { it.isCompleted } to longTerm
                    }
                }
                .collect { data ->
                    if (data != null) {
                        _editState.update {
                            it.copy(completedTasks = data.first, longTermTasks = data.second)
                        }
                    }
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

    /** 打开某日的复盘编辑页：任务列表走观察管道自动跟随，这里只加载复盘文本（PRD 4.4.4）。
     *  注意：review_edit 是独立导航路由、独立 VM 实例，不能依赖 calendarState（异步未就绪），
     *  主计划本用 once 查询同步获取。 */
    fun openReviewEdit(date: String) {
        _editState.update { it.copy(date = date, loaded = false) }
        editDateState.value = date
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            val review = repository.getReview(master.id, date)
            _editState.update {
                it.copy(
                    content = review?.content ?: "",
                    loaded = true
                )
            }
        }
    }

    /** 自动保存：停止输入后调用（PRD 4.4.4：停止输入 1 秒后保存）。
     *  主计划本 once 查询（独立路由 VM 实例不依赖 calendarState，见 openReviewEdit 注释）。 */
    fun saveReviewContent(content: String) {
        _editState.update { it.copy(content = content) }
        val date = _editState.value.date
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.saveReview(
                Review(notebookId = master.id, date = date, content = content)
            )
        }
    }

    /** 长期任务拆分：用拆分出的子任务（用户已编辑好的）新建一条，落到活动子计划本，原长期保留（PRD 4.4.4）。
     *  任务列表由观察管道自动刷新，无需手动重载。 */
    fun splitLongTermTask(subTask: Task) {
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            val activeSubId = repository.getActiveSubOfMaster().first()?.id ?: master.id
            repository.addTask(subTask.copy(id = 0, notebookId = activeSubId))
        }
    }
}
