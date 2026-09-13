package com.example.planbook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.planbook.data.PlanBookRepository
import com.example.planbook.model.Notebook
import com.example.planbook.model.SubNotebookPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 子计划本管理页状态（设置页 → 计划本管理，ADR-0004） */
data class NotebookManagerUiState(
    /** 主计划本（全局唯一，子计划本的挂载点） */
    val master: Notebook? = null,
    /** 主计划本下的全部子计划本（含隐藏） */
    val subs: List<Notebook> = emptyList(),
    /** 活动子计划本 id（写操作目标） */
    val activeSubId: Long? = null,
    /** #11：导入结果提示（成功/失败文案），null = 无待提示 */
    val importMessage: String? = null
)

@HiltViewModel
class NotebookManagerViewModel @Inject constructor(
    private val repository: PlanBookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotebookManagerUiState())
    val uiState: StateFlow<NotebookManagerUiState> = _uiState.asStateFlow()

    /** 调色板全部颜色。colors 列表是私有的，借助 forIndex 的轮转特性枚举到出现重复为止。 */
    val paletteColors: List<String> = buildList {
        var i = 0
        while (true) {
            val c = SubNotebookPalette.forIndex(i)
            if (contains(c)) break
            add(c)
        }
    }

    init {
        viewModelScope.launch {
            combine(
                repository.getMasterNotebook(),
                repository.getSubNotebooksOfMaster(),
                repository.getActiveSubOfMaster()
            ) { master, subs, active -> Triple(master, subs, active) }
                .collect { (master, subs, active) ->
                    _uiState.update {
                        it.copy(master = master, subs = subs, activeSubId = active?.id)
                    }
                }
        }
    }

    /** 新建子计划本（颜色由调色板轮转分配；若当前无活动子本则自动设为活动） */
    fun createSubNotebook(name: String) {
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.createSubNotebook(master.id, name)
        }
    }

    /** 重命名子计划本（PRD 4.1.3） */
    fun renameSubNotebook(notebook: Notebook, newName: String) {
        viewModelScope.launch {
            repository.renameNotebook(notebook.copy(name = newName))
        }
    }

    /** 删除子计划本：至少保留一个（删活动子本时仓库会自动换活动） */
    fun deleteSubNotebook(notebook: Notebook) {
        viewModelScope.launch {
            if (_uiState.value.subs.size <= 1) return@launch
            repository.deleteNotebook(notebook)
        }
    }

    /** 切换活动子计划本（写操作目标；首页新建任务的落点） */
    fun setActiveSubNotebook(subId: Long) {
        viewModelScope.launch {
            repository.setActiveSubNotebook(subId)
        }
    }

    /** 显示开关：只影响计划本页聚合显示，不动数据 */
    fun setSubNotebookVisible(subId: Long, visible: Boolean) {
        viewModelScope.launch {
            repository.setSubNotebookVisible(subId, visible)
        }
    }

    /**
     * 手动改颜色。repository 没有单独的颜色更新方法，但 renameNotebook 实际是
     * notebookDao().update(整实体)，copy 换 color 后传入即可更新任意字段。
     */
    fun setSubNotebookColor(notebook: Notebook, color: String) {
        viewModelScope.launch {
            repository.renameNotebook(notebook.copy(color = color))
        }
    }

    /**
     * #11：导入 ICS——解析 + 建导入子计划本（只读快照，默认显示、不抢活动）。
     * 失败（解析异常/无事件/读库失败）只更新提示文案，不产生半成品数据（事务回滚）。
     */
    fun importIcs(sourceName: String, content: String) {
        viewModelScope.launch {
            val message = try {
                val tasks = com.example.planbook.data.ics.IcsParser.parse(content)
                if (tasks.isEmpty()) {
                    "「$sourceName」里没有解析到任何课程事件"
                } else {
                    val master = repository.getMasterNotebookOnce()
                        ?: error("主计划本不存在")
                    repository.importIcsSubNotebook(master.id, sourceName, tasks)
                    "已导入「$sourceName」：${tasks.size} 条课程"
                }
            } catch (e: Exception) {
                android.util.Log.e("NotebookManager", "ICS 导入失败", e)
                "导入失败：${e.message ?: "无法解析该文件"}"
            }
            _uiState.update { it.copy(importMessage = message) }
        }
    }

    /** 清除导入结果提示 */
    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }
}
