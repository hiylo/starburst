/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : GitViewModel.kt
 * Date : 2026/09/07 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.git

import android.content.Context
import org.hiylo.starburst.logging.AppLogger as Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.SuggestionProvider
import org.hiylo.starburst.data.api.getCurrentProject
import org.hiylo.starburst.data.api.listProjects
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.shell.ServerShellRegistry
import org.hiylo.starburst.data.shell.ShellCommandTimeoutException
import org.hiylo.starburst.data.sync.LocalSyncSecretStore
import org.hiylo.starburst.ml.MnnLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

private const val TAG = "GitViewModel"

/** 提交历史每次加载/追加的条数。 */
private const val COMMIT_PAGE_SIZE = 20
private const val COMMIT_MSG_MAX_TOKENS = 128

/** Git 仓库在仓库选择器中的种类。 */
enum class GitRepoKind { ROOT, SUBMODULE, NESTED }

/** 一个可操作的 Git 仓库（顶层 worktree 或嵌套/子模块仓库）。 */
data class GitRepo(
    val path: String,
    val label: String,
    val kind: GitRepoKind,
)

/** 单个文件变更：路径、增删行数与状态。 */
data class GitChange(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val status: String, // added / modified / deleted / untracked
)

/** 一条提交记录。 */
data class GitCommit(
    val hash: String,
    val author: String,
    val date: String,
    val message: String,
)

/** 单个文件的 diff 视图内容（原始 unified diff 文本）。 */
data class GitFileDiff(
    val path: String,
    val content: String,
    val additions: Int,
    val deletions: Int,
)

/** shell 命令执行结果：退出码与输出文本。 */
private data class CommandResult(val exitCode: Int, val output: String)

/** Git 操作失败（退出码非 0）时抛出，message 为 git 命令的报错输出。 */
private class GitOperationException(message: String) : Exception(message)

/** Git 页面的 UI 状态。 */
data class GitUiState(
    val directory: String = "",
    val repos: List<GitRepo> = emptyList(),
    val selectedRepo: String = "",
    val branch: String? = null,
    val branches: List<String> = emptyList(),
    val isClean: Boolean = true,
    val changes: List<GitChange> = emptyList(),
    val commits: List<GitCommit> = emptyList(),
    val selectedDiff: GitFileDiff? = null,
    val selectedCommit: GitCommit? = null,
    val commitChanges: List<GitChange> = emptyList(),
    val commitDiff: String = "",
    val isLoadingCommit: Boolean = false,
    val commitFileDiff: GitFileDiff? = null,
    val remotes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val hasStash: Boolean = false,
    val aheadCount: Int = 0,
    val hasMoreCommits: Boolean = false,
    val isLoadingMoreCommits: Boolean = false,
    val notRepository: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRunning: Boolean = false,
    val operationMessage: String? = null,
    val operationError: String? = null,
)

/**
 * Git 页面 ViewModel。
 *
 * 复用终端 PTY 机制：为每条 git 命令创建一个临时 PTY，发送非交互命令并通过
 * begin/end 标记收集输出，随后关闭并移除该 PTY。所有命令显式 `-C <repoRoot>`，
 * 关闭分页与交互提示，避免阻塞。
 */
@HiltViewModel
class GitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    @ApplicationContext private val context: Context,
    private val suggestionProvider: SuggestionProvider,
    private val settingsRepository: SettingsRepository,
    private val secretStore: LocalSyncSecretStore,
    private val shellRegistry: ServerShellRegistry,
) : ViewModel() {

    private val conn = ServerConnection.from(
        url = savedStateHandle.get<String>("serverUrl").orEmpty(),
        username = savedStateHandle.get<String>("username").orEmpty().ifBlank { "opencode" },
        password = savedStateHandle.get<String>("password").orEmpty().ifEmpty { null },
    )
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()
    private val directory = savedStateHandle.get<String>("directory").orEmpty()

    private val _uiState = MutableStateFlow(GitUiState(directory = directory))
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    private val _generatingMessage = MutableStateFlow(false)
    val generatingMessage: StateFlow<Boolean> = _generatingMessage.asStateFlow()

    private val _generatedMessage = MutableStateFlow<String?>(null)
    val generatedMessage: StateFlow<String?> = _generatedMessage.asStateFlow()

    private val _generateError = MutableStateFlow<String?>(null)
    val generateError: StateFlow<String?> = _generateError.asStateFlow()

    /** 连接级共享 PTY 会话：按 server 复用，与服务器管理页共用同一条 PTY。 */
    private var ptySessionAcquired = false
    private val ptySession by lazy {
        ptySessionAcquired = true
        shellRegistry.acquire(serverId.ifBlank { conn.baseUrl }, api, conn, directory)
    }

    /** 已加载的提交数量，用于「加载更多」时计算 `--skip`。 */
    private var loadedCommitCount = 0

    override fun onCleared() {
        if (ptySessionAcquired) {
            shellRegistry.release(serverId.ifBlank { conn.baseUrl })
        }
        super.onCleared()
    }

    init {
        refresh()
    }

    /** 重新加载当前选中仓库的完整 Git 状态。 */
    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { refreshInternal() }
        }
    }

    private suspend fun refreshInternal() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            // 首次进入可能存在目录解析/PTY 竞争，失败后自动重试一次。
            if (!refreshOnce()) {
                delay(600)
                refreshOnce()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh git state", e)
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    /** 执行一次刷新。返回 true 表示已成功展示 Git 数据；false 表示需重试或确实不是仓库。 */
    private suspend fun refreshOnce(): Boolean {
        val root = resolveDirectory()
        if (root.isBlank()) {
            _uiState.update { it.copy(notRepository = false, isLoading = false, error = context.getString(R.string.git_load_failed)) }
            return false
        }

        val data = loadReadData(root)
        if (data == null) {
            _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.git_load_failed)) }
            return false
        }

        if (data.topLevel.isNotBlank()) {
            // 目录本身是 git 仓库：正常展示，并后台补充子仓库。
            val repos = detectReposFromData(root, data.submodules)
            val effectiveRepo = _uiState.value.selectedRepo.ifBlank { root }
                .let { sel -> repos.firstOrNull { it.path == sel }?.path ?: root }
            loadedCommitCount = data.commits.size
            _uiState.update {
                it.copy(
                    repos = repos,
                    selectedRepo = effectiveRepo,
                    directory = data.topLevel,
                    branch = data.branch,
                    branches = data.branches,
                    remotes = data.remotes,
                    tags = data.tags,
                    hasStash = data.hasStash,
                    aheadCount = data.aheadCount,
                    isClean = data.changes.isEmpty(),
                    changes = data.changes,
                    commits = data.commits,
                    hasMoreCommits = data.commits.size >= COMMIT_PAGE_SIZE,
                    isLoadingMoreCommits = false,
                    selectedDiff = null,
                    notRepository = false,
                    isLoading = false,
                    error = null,
                )
            }
            detectNestedReposAsync(root)
            return true
        } else {
            // 目录本身不是 git 仓库，但可能包含多个仓库：查找并让用户选择。
            val nested = findNestedRepos(root)
            if (nested.isEmpty()) {
                _uiState.update { it.copy(notRepository = true, isLoading = false, error = null) }
                return false
            }
            val repos = nested.map { GitRepo(it, labelFor(it, root.trimEnd('/')), GitRepoKind.NESTED) }
            val first = repos.first().path
            val firstData = loadReadData(first)
            if (firstData == null) {
                _uiState.update {
                    it.copy(repos = repos, selectedRepo = first, isLoading = false, error = context.getString(R.string.git_load_failed))
                }
                return false
            }
            loadedCommitCount = firstData.commits.size
            _uiState.update {
                it.copy(
                    repos = repos,
                    selectedRepo = first,
                    directory = firstData.topLevel.ifBlank { first },
                    branch = firstData.branch,
                    branches = firstData.branches,
                    remotes = firstData.remotes,
                    tags = firstData.tags,
                    hasStash = firstData.hasStash,
                    aheadCount = firstData.aheadCount,
                    isClean = firstData.changes.isEmpty(),
                    changes = firstData.changes,
                    commits = firstData.commits,
                    hasMoreCommits = firstData.commits.size >= COMMIT_PAGE_SIZE,
                    isLoadingMoreCommits = false,
                    selectedDiff = null,
                    notRepository = false,
                    isLoading = false,
                    error = null,
                )
            }
            return true
        }
    }

    private suspend fun resolveDirectory(): String {
        val configured = directory.trim().trimEnd('/')
        if (configured.isNotBlank()) return configured
        // 回退到服务器当前项目；首次进入可能存在网络/时序竞争，重试几次。
        repeat(3) { attempt ->
            val current = runCatching { api.getCurrentProject(conn).worktree.ifBlank { null } }.getOrNull()
            if (!current.isNullOrBlank()) return current
            val first = runCatching { api.listProjects(conn).firstOrNull()?.worktree.orEmpty() }.getOrNull()
            if (!first.isNullOrBlank()) return first
            if (attempt < 2) delay(300)
        }
        return ""
    }

    /** 批量只读数据的解析结果。 */
    private data class GitReadData(
        val topLevel: String,
        val branch: String?,
        val branches: List<String>,
        val remotes: List<String>,
        val changes: List<GitChange>,
        val commits: List<GitCommit>,
        val submodules: String,
        val tags: List<String>,
        val hasStash: Boolean,
        val aheadCount: Int,
    )

    /** 在一个临时 PTY 中一次性执行所有只读 git 命令并解析，返回结构化数据；失败返回 null。 */
    private suspend fun loadReadData(root: String): GitReadData? {
        val id = UUID.randomUUID().toString().replace("-", "")
        val end = "__OPENGIT_END_${id}__"
        val raw = executeScript(gitReadScript(root) + "printf '\\n$end\\n'\n", end)
        if (raw.isBlank()) return null
        val topLevel = section(raw, "TOPLEVEL")
        val branch = section(raw, "BRANCH").trim().ifBlank { null }
        val branches = parseBranches(section(raw, "BRANCHES"))
        val remotes = section(raw, "REMOTES").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        val changes = parseChanges(section(raw, "STATUS"))
        val numstat = parseNumstat(section(raw, "NUMSTAT"))
        val mergedChanges = changes.map { change ->
            val counts = numstat[change.path]
            change.copy(
                additions = counts?.first ?: change.additions,
                deletions = counts?.second ?: change.deletions,
            )
        }
        val commits = parseCommits(section(raw, "LOG"))
        val submodules = section(raw, "SUBMODULES")
        val tags = section(raw, "TAGS").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val hasStash = section(raw, "STASH").trim().isNotBlank()
        val aheadCount = section(raw, "AHEAD").trim().toIntOrNull() ?: 0
        return GitReadData(topLevel, branch, branches, remotes, mergedChanges, commits, submodules, tags, hasStash, aheadCount)
    }

    /** 选择仓库选择器中的另一个仓库。 */
    fun selectRepo(path: String) {
        if (path == _uiState.value.selectedRepo) return
        _uiState.update { it.copy(selectedRepo = path) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val data = loadReadData(path) ?: return@runCatching
                    loadedCommitCount = data.commits.size
                    _uiState.update {
                        it.copy(
                            branch = data.branch,
                            branches = data.branches,
                            remotes = data.remotes,
                            tags = data.tags,
                            hasStash = data.hasStash,
                            aheadCount = data.aheadCount,
                            isClean = data.changes.isEmpty(),
                            changes = data.changes,
                            commits = data.commits,
                            hasMoreCommits = data.commits.size >= COMMIT_PAGE_SIZE,
                            isLoadingMoreCommits = false,
                            selectedDiff = null,
                            isLoading = false,
                            error = null,
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to load repo $path", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }

    /** 加载单个文件的 diff；未跟踪文件展示其全部内容（作为新增）。 */
    fun loadDiff(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, selectedCommit = null, commitDiff = "") }
                val root = _uiState.value.selectedRepo.ifBlank { return@withContext }
                val change = _uiState.value.changes.firstOrNull { it.path == path }
                val content = runCatching {
                    if (change?.status == "untracked") {
                        runCommand(gitCmd(root, "diff --no-index -- /dev/null ${shQuote(path)}"))
                    } else {
                        val head = runCommand(gitCmd(root, "diff HEAD -- ${shQuote(path)}"))
                        if (head.isNotBlank()) head
                        else runCommand(gitCmd(root, "diff --cached -- ${shQuote(path)}"))
                    }
                }.getOrElse { "" }
                _uiState.update {
                    it.copy(
                        selectedDiff = GitFileDiff(
                            path = path,
                            content = content,
                            additions = change?.additions ?: 0,
                            deletions = change?.deletions ?: 0,
                        ),
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** 加载某次提交涉及的文件变更与完整 diff；再次点击同一提交则折叠。 */
    fun loadCommitDetail(commit: GitCommit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val root = _uiState.value.selectedRepo.ifBlank { return@withContext }
                if (_uiState.value.selectedCommit?.hash == commit.hash) {
                    _uiState.update { it.copy(selectedCommit = null, commitChanges = emptyList(), commitDiff = "", isLoadingCommit = false) }
                    return@withContext
                }
                _uiState.update { it.copy(selectedCommit = commit, isLoadingCommit = true, commitChanges = emptyList(), commitDiff = "", selectedDiff = null) }

                // 单 PTY 批量执行 diff-tree + show，避免两次 shell 启动开销。
                val id = UUID.randomUUID().toString().replace("-", "")
                val end = "__OPENGIT_END_${id}__"
                val g = "git -c color.ui=false --no-pager -C ${shQuote(root)}"
                val script = buildString {
                    append("printf '\\n__GIT_NAMESTATUS__\\n'\n")
                    append("$g diff-tree --no-commit-id --name-status -r ${commit.hash}\n")
                    append("printf '\\n__GIT_NUMSTAT__\\n'\n")
                    append("$g diff-tree --no-commit-id --numstat -r ${commit.hash}\n")
                    append("printf '\\n__GIT_SHOW__\\n'\n")
                    append("$g show --stat --format= ${shQuote(commit.hash)}\n")
                    append("printf '\\n$end\\n'\n")
                }
                val raw = runCatching { executeScript(script, end) }.getOrDefault("")
                val nameStatus = parseNameStatus(section(raw, "NAMESTATUS"))
                val numstat = parseNumstat(section(raw, "NUMSTAT"))
                val changes = nameStatus.map { c ->
                    val counts = numstat[c.path]
                    c.copy(additions = counts?.first ?: 0, deletions = counts?.second ?: 0)
                }
                val diff = section(raw, "SHOW")
                _uiState.update { it.copy(commitChanges = changes, commitDiff = diff, isLoadingCommit = false, commitFileDiff = null) }
            }
        }
    }

    /** 加载某次提交中单个文件的 diff；再次点击同一文件则折叠。 */
    fun loadCommitFileDiff(commitHash: String, path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val root = _uiState.value.selectedRepo.ifBlank { return@withContext }
                if (_uiState.value.commitFileDiff?.path == path) {
                    _uiState.update { it.copy(commitFileDiff = null) }
                    return@withContext
                }
                val content = runCatching {
                    runCommand(gitCmd(root, "show --no-ext-diff --format= ${shQuote(commitHash)} -- ${shQuote(path)}"))
                }.getOrDefault("")
                _uiState.update {
                    it.copy(commitFileDiff = GitFileDiff(path = path, content = content, additions = 0, deletions = 0))
                }
            }
        }
    }

    /**
     * 提交变更。若 [paths] 非空则仅暂存并提交这些路径（`git add -- <paths>`），
     * 否则使用 `git add -A` 提交全部变更。
     */
    fun commit(message: String, paths: List<String> = emptyList()) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        runOperation("commit") {
            val root = _uiState.value.selectedRepo
            val addArgs = if (paths.isNotEmpty()) {
                "add -- " + paths.joinToString(" ") { shQuote(it) }
            } else {
                "add -A"
            }
            val add = runCommandResult(gitCmd(root, addArgs))
            if (add.exitCode != 0) throw GitOperationException(add.output)
            val cm = runCommandResult(gitCmd(root, "-c core.editor=true commit -m ${shQuote(trimmed)}"))
            if (cm.exitCode != 0) throw GitOperationException(cm.output)
        }
    }

    /** 推送到指定 remote 的当前分支（`git push <remote> <branch>`）。 */
    fun push(remote: String) = runOperation("push") {
        val root = _uiState.value.selectedRepo
        val target = _uiState.value.branch?.takeIf { it.isNotBlank() } ?: "HEAD"
        val r = runCommandResult(gitCmd(root, "push ${shQuote(remote)} ${shQuote(target)}"))
        if (r.exitCode != 0) throw GitOperationException(r.output)
    }

    /** 从指定 remote 拉取当前分支（`git pull --rebase <remote> <branch>`）。 */
    fun pull(remote: String) = runOperation("pull") {
        val root = _uiState.value.selectedRepo
        val target = _uiState.value.branch?.takeIf { it.isNotBlank() } ?: "HEAD"
        val r = runCommandResult(gitCmd(root, "pull --rebase ${shQuote(remote)} ${shQuote(target)}"))
        if (r.exitCode != 0) throw GitOperationException(r.output)
    }

    /** 从所有 remote 拉取最新引用（`git fetch --all`）。 */
    fun fetch() = runOperation("fetch") {
        val r = runCommandResult(gitCmd(_uiState.value.selectedRepo, "fetch --all"))
        if (r.exitCode != 0) throw GitOperationException(r.output)
    }

    /** 将当前未提交变更暂存到 stash（`git stash push -m <message>`）。 */
    fun stash() = runOperation("stash") {
        val root = _uiState.value.selectedRepo
        val message = "starburst-stash-${System.currentTimeMillis()}"
        val r = runCommandResult(gitCmd(root, "stash push -m ${shQuote(message)}"))
        if (r.exitCode != 0) throw GitOperationException(r.output)
    }

    /** 弹出最近的 stash（`git stash pop`）。 */
    fun stashPop() = runOperation("stashPop") {
        val r = runCommandResult(gitCmd(_uiState.value.selectedRepo, "stash pop"))
        if (r.exitCode != 0) throw GitOperationException(r.output)
    }

    /** 在当前 HEAD 创建 tag（`git tag <name>`）。 */
    fun createTag(name: String) {
        val tagName = name.trim()
        if (tagName.isEmpty()) return
        runOperation("createTag") {
            val r = runCommandResult(gitCmd(_uiState.value.selectedRepo, "tag ${shQuote(tagName)}"))
            if (r.exitCode != 0) throw GitOperationException(r.output)
        }
    }

    /** 追加加载更多提交记录（`git log --skip=<已加载> -<页大小>`）。 */
    fun loadMoreCommits() {
        if (_uiState.value.isLoadingMoreCommits) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val root = _uiState.value.selectedRepo.ifBlank { return@withContext }
                _uiState.update { it.copy(isLoadingMoreCommits = true) }
                val skip = loadedCommitCount
                val output = runCatching {
                    runCommand(gitCmd(root, "log --skip=$skip -$COMMIT_PAGE_SIZE $logPrettyFormat"))
                }.getOrDefault("")
                val more = parseCommits(output)
                if (more.isEmpty()) {
                    _uiState.update { it.copy(hasMoreCommits = false, isLoadingMoreCommits = false) }
                } else {
                    loadedCommitCount += more.size
                    _uiState.update {
                        it.copy(
                            commits = it.commits + more,
                            hasMoreCommits = more.size >= COMMIT_PAGE_SIZE,
                            isLoadingMoreCommits = false,
                        )
                    }
                }
            }
        }
    }

    /** 切换到已有分支（`git checkout <branch>`）。 */
    fun checkout(branch: String) = runOperation("checkout") {
        val r = runCommandResult(gitCmd(_uiState.value.selectedRepo, "checkout ${shQuote(branch)}"))
        if (r.exitCode != 0) throw GitOperationException(r.output)
    }

    /** 创建并切换到新分支（`git checkout -b <branch>`）。 */
    fun createBranch(branch: String) {
        val name = branch.trim()
        if (name.isEmpty()) return
        runOperation("createBranch") {
            val r = runCommandResult(gitCmd(_uiState.value.selectedRepo, "checkout -b ${shQuote(name)}"))
            if (r.exitCode != 0) throw GitOperationException(r.output)
        }
    }

    /** 清空操作结果提示。 */
    fun clearOperationMessage() {
        _uiState.update { it.copy(operationMessage = null, operationError = null) }
    }

    /** 清空已生成的提交信息。 */
    fun clearGeneratedMessage() {
        _generatedMessage.value = null
        _generateError.value = null
    }

    /**
     * 根据本次变更与最近提交的风格生成 commit message。
     * 优先使用「生成建议」里配置的云端 LLM，失败或未配置时回退端侧模型。
     */
    fun generateCommitMessage() {
        if (_generatingMessage.value) return
        viewModelScope.launch {
            _generatingMessage.value = true
            _generatedMessage.value = null
            _generateError.value = null
            try {
                val changes = _uiState.value.changes
                val recent = _uiState.value.commits.take(10)
                val prompt = buildCommitMessagePrompt(changes, recent)

                var message: String? = null
                var onDeviceAvailable = false
                val baseUrl = settingsRepository.llmProviderBaseUrl.first()
                val model = settingsRepository.llmProviderModel.first()
                if (baseUrl.isNotBlank() && model.isNotBlank()) {
                    val apiKey = secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY).orEmpty()
                    message = runCatching {
                        suggestionProvider.chat(
                            SuggestionProvider.Config(baseUrl = baseUrl, apiKey = apiKey, model = model),
                            prompt,
                            maxTokens = COMMIT_MSG_MAX_TOKENS,
                        ).trim().takeIf { it.isNotBlank() }
                    }.getOrNull()
                }

                if (message == null) {
                    onDeviceAvailable = MnnLlm.ensureLoaded(context)
                    if (onDeviceAvailable) {
                        MnnLlm.reset()
                        message = MnnLlm.generate(prompt, maxTokens = 128).trim().takeIf { it.isNotBlank() }
                    }
                }
                _generatedMessage.value = message
                _generateError.value = when {
                    message != null -> null
                    !onDeviceAvailable && (baseUrl.isBlank() || model.isBlank()) ->
                        context.getString(R.string.git_generate_no_model)
                    else -> context.getString(R.string.git_generate_failed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "commit message generation failed", e)
                _generateError.value = context.getString(R.string.git_generate_failed)
            } finally {
                _generatingMessage.value = false
            }
        }
    }

    /** 构造 commit message 生成的提示词：包含最近提交风格与本次变更摘要。 */
    private fun buildCommitMessagePrompt(changes: List<GitChange>, recent: List<GitCommit>): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        val changeDesc = changes.joinToString("\n") { c ->
            "${c.status} ${c.path} (+${c.additions}/-${c.deletions})"
        }.ifBlank { "(no changes)" }
        val recentDesc = recent.joinToString("\n") { it.message }.ifBlank { "(none)" }
        return if (isZh) {
            "根据下面的代码变更和最近提交的风格，生成一条简洁的 commit message（遵循最近提交的格式习惯）。\n" +
                "只输出 commit message 本身，不要解释、不要加引号、不要 markdown。\n\n" +
                "最近提交风格：\n$recentDesc\n\n" +
                "本次变更：\n$changeDesc"
        } else {
            "Generate a concise commit message based on the changes below and the style of recent commits.\n" +
                "Output ONLY the commit message itself — no explanation, no quotes, no markdown.\n\n" +
                "Recent commit style:\n$recentDesc\n\n" +
                "Changes:\n$changeDesc"
        }
    }

    private fun runOperation(name: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.update { it.copy(isRunning = true, error = null, operationError = null) }
                try {
                    block()
                    _uiState.update { it.copy(isRunning = false, operationMessage = "success") }
                    refreshInternal()
                } catch (e: GitOperationException) {
                    Log.e(TAG, "Git operation $name failed: ${e.message}", e)
                    _uiState.update { it.copy(isRunning = false, operationMessage = "failed", operationError = e.message) }
                } catch (e: Exception) {
                    Log.e(TAG, "Git operation $name failed", e)
                    _uiState.update { it.copy(isRunning = false, operationMessage = "failed", operationError = e.message) }
                }
            }
        }
    }

    // ============ 仓库检测 ============

    /** 从批量读取的 submodule 输出构建仓库选择器（根 + 子模块）。 */
    private fun detectReposFromData(root: String, submodulesOutput: String): List<GitRepo> {
        val result = linkedMapOf<String, GitRepo>()
        val normalizedRoot = root.trimEnd('/')
        result[normalizedRoot] = GitRepo(normalizedRoot, labelFor(normalizedRoot, normalizedRoot), GitRepoKind.ROOT)

        submodulesOutput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val relPath = parts[1]
                    val abs = (normalizedRoot + "/" + relPath.trimStart('/')).trimEnd('/')
                    result.getOrPut(abs) {
                        GitRepo(abs, labelFor(abs, normalizedRoot), GitRepoKind.SUBMODULE)
                    }
                }
            }
        return result.values.toList()
    }

    /** 查找 [root] 目录下包含的 git 仓库（非子模块），返回绝对路径列表。 */
    private suspend fun findNestedRepos(root: String): List<String> {
        return runCatching {
            runCommand("find ${shQuote(root)} -mindepth 2 -name .git -not -path '*/.git/*'")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { gitPath ->
                    val abs = gitPath.trimEnd('/').substringBeforeLast('/').trimEnd('/')
                    abs.takeIf { it.isNotBlank() && it != root.trimEnd('/') }
                }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())
    }

    /** 后台查找嵌套仓库（非子模块），不阻塞首屏展示。 */
    private fun detectNestedReposAsync(root: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                findNestedRepos(root).forEach { abs ->
                    if (_uiState.value.repos.none { it.path == abs }) {
                        _uiState.update { st ->
                            st.copy(repos = st.repos + GitRepo(abs, labelFor(abs, root.trimEnd('/')), GitRepoKind.NESTED))
                        }
                    }
                }
            }
        }
    }

    private fun labelFor(path: String, root: String): String {
        val normalized = path.trimEnd('/')
        if (normalized == root.trimEnd('/')) {
            return normalized.substringAfterLast('/').ifBlank { normalized }
        }
        val relative = normalized.removePrefix(root.trimEnd('/')).trimStart('/')
        return relative.ifBlank { normalized.substringAfterLast('/') }
    }

    // ============ 输出解析 ============

    private fun parseBranches(output: String): List<String> = output
        .lineSequence()
        .map { it.trim().removePrefix("*").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    private fun parseChanges(output: String): List<GitChange> = output
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            if (line.length < 2) return@mapNotNull null
            val xy = line.substring(0, 2)
            var path = line.substring(2).trim()
            if (path.contains(" -> ")) path = path.substringAfterLast(" -> ")
            val status = when {
                xy == "??" -> "untracked"
                xy[0] == 'D' || xy[1] == 'D' -> "deleted"
                xy[0] == 'A' || xy[1] == 'A' -> "added"
                else -> "modified"
            }
            GitChange(path = path, additions = 0, deletions = 0, status = status)
        }
        .toList()

    private fun parseNameStatus(output: String): List<GitChange> = output
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val status = when (parts[0].trim().firstOrNull()) {
                'A' -> "added"
                'D' -> "deleted"
                'R' -> "modified"
                'T' -> "modified"
                else -> "modified"
            }
            val path = if (parts.size >= 3) parts[2] else parts[1]
            GitChange(path = path, additions = 0, deletions = 0, status = status)
        }
        .toList()

    private fun parseNumstat(output: String): Map<String, Pair<Int, Int>> = output
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val added = parts[0].toIntOrNull() ?: 0
            val deleted = parts[1].toIntOrNull() ?: 0
            parts[2] to (added to deleted)
        }
        .toMap()

    private fun parseCommits(output: String): List<GitCommit> = output
        .split('\n')
        .mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size < 4) return@mapNotNull null
            GitCommit(
                hash = parts[0],
                author = parts[1],
                date = parts[2],
                message = parts[3],
            )
        }
        .toList()

    // ============ PTY 命令执行 ============

    private fun gitCmd(root: String, args: String): String =
        "git -c color.ui=false --no-pager -C ${shQuote(root)} $args"

    /** `git log` 的输出格式：以单位分隔符拼接 hash/作者/日期/标题，与 [parseCommits] 对应。 */
    private val logPrettyFormat = "--pretty=format:%H%x1f%an%x1f%ad%x1f%s --date=short"

    /**
     * 生成单次批量读取仓库只读数据的 shell 脚本，各段用 `__GIT_<NAME>__` 标记分隔，
     * 一次性在单个 PTY 中执行，避免逐命令新建 PTY 带来的慢与不稳定。
     */
    private fun gitReadScript(root: String): String {
        val q = shQuote(root)
        val g = "git -c color.ui=false --no-pager -C $q"
        return buildString {
            append("printf '\\n__GIT_TOPLEVEL__\\n'\n"); append("$g rev-parse --show-toplevel\n")
            append("printf '\\n__GIT_BRANCH__\\n'\n"); append("$g rev-parse --abbrev-ref HEAD\n")
            append("printf '\\n__GIT_STATUS__\\n'\n"); append("$g status --porcelain=v1\n")
            append("printf '\\n__GIT_NUMSTAT__\\n'\n"); append("$g diff --numstat\n")
            append("printf '\\n__GIT_LOG__\\n'\n"); append("$g log -$COMMIT_PAGE_SIZE $logPrettyFormat\n")
            append("printf '\\n__GIT_BRANCHES__\\n'\n"); append("$g branch\n")
            append("printf '\\n__GIT_REMOTES__\\n'\n"); append("$g remote\n")
            append("printf '\\n__GIT_TAGS__\\n'\n"); append("$g tag --sort=-creatordate\n")
            append("printf '\\n__GIT_STASH__\\n'\n"); append("$g stash list\n")
            append("printf '\\n__GIT_AHEAD__\\n'\n"); append("$g rev-list --count @{upstream}..HEAD 2>/dev/null || printf '0\\n'\n")
            append("printf '\\n__GIT_SUBMODULES__\\n'\n"); append("$g submodule status --recursive\n")
        }
    }

    /** 从批量输出中提取指定段落（`__GIT_<NAME>__` 与其后一个标记之间）。 */
    private fun section(raw: String, name: String): String {
        val marker = "__GIT_${name}__"
        val lines = cleanTerminal(raw).lines()
        val start = lines.indexOf(marker)
        if (start == -1) return ""
        val endIdx = lines.subList(start + 1, lines.size).indexOfFirst { it.startsWith("__GIT_") }
        val end = if (endIdx == -1) lines.size else start + 1 + endIdx
        return lines.subList(start + 1, end).joinToString("\n").trim()
    }

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun cleanTerminal(raw: String): String = raw
        .replace(ANSI_OSC, "")
        .replace(ANSI_CSI, "")
        .replace("\u001B", "")
        .replace("\r", "")

    private fun extract(begin: String, end: String, raw: String): String {
        val lines = cleanTerminal(raw).lines()
        val beginIdx = lines.indexOf(begin)
        val endIdx = lines.indexOf(end)
        if (beginIdx == -1 || endIdx == -1 || endIdx <= beginIdx) return ""
        return lines.subList(beginIdx + 1, endIdx).joinToString("\n").trim()
    }

    private suspend fun runCommand(command: String, timeoutMs: Long = 30_000): String {
        var result = runCommandOnce(command, timeoutMs)
        if (result.isBlank()) {
            // PTY 偶发空输出（shell 尚未就绪/标记丢失），重试一次提升成功率。
            result = runCommandOnce(command, timeoutMs)
        }
        return result
    }

    /** 通过常驻 PTY 执行一条 shell 命令，返回 begin/end 标记之间的输出。 */
    private suspend fun runCommandOnce(command: String, timeoutMs: Long = 30_000): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val begin = "OPENGIT_B_$id"
        val end = "OPENGIT_E_$id"
        val script = "printf '$begin\\n'\n$command 2>&1\nprintf '\\n$end\\n'\n"
        // 读类命令超时保持宽容：返回空串由调用方降级/重试。
        val raw = runCatching { ptySession.run(script, end, timeoutMs) }.getOrElse {
            if (it is ShellCommandTimeoutException) return ""
            throw it
        }
        return extract(begin, end, raw)
    }

    /** 执行 shell 命令并返回退出码与输出（用于需要区分成功/失败的操作）。
     *  命令超时时由 [run] 抛 [ShellCommandTimeoutException]，此处转为退出码 -1，绝不误报成功。 */
    private suspend fun runCommandResult(command: String, timeoutMs: Long = 60_000): CommandResult {
        val id = UUID.randomUUID().toString().replace("-", "")
        val begin = "OPENGIT_B_$id"
        val end = "OPENGIT_E_$id"
        val exit = "OPENGIT_X_$id"
        val script = "printf '$begin\\n'\n$command 2>&1\nprintf '${exit}%d\\n' \"\$?\"\nprintf '\\n$end\\n'\n"
        val raw = runCatching { ptySession.run(script, end, timeoutMs) }.getOrElse {
            if (it is ShellCommandTimeoutException) return CommandResult(-1, "")
            throw it
        }
        val body = extract(begin, end, raw)
        val lines = body.lineSequence().toList()
        val exitLine = lines.lastOrNull { it.startsWith(exit) }
        // 缺失退出码行说明命令超时/标记丢失，回退 -1（非零）而非 0，避免误报成功。
        val code = exitLine?.removePrefix(exit)?.trim()?.toIntOrNull() ?: -1
        val output = lines.filterNot { it.startsWith(exit) }.joinToString("\n").trim()
        return CommandResult(code, output)
    }

    /** 通过常驻 PTY 执行多行脚本，读取到 [endMarker] 后返回原始输出。 */
    private suspend fun executeScript(script: String, endMarker: String, timeoutMs: Long = 60_000): String {
        return ptySession.run(script, endMarker, timeoutMs)
    }

    private companion object {
        val ANSI_CSI = Regex("\u001B\\[[0-9;?]*[A-Za-z]")
        val ANSI_OSC = Regex("\u001B\\][^\u001B\u0007]*(?:\u0007|\u001B\\\\)")
    }
}

