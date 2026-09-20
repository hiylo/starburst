/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelProjectOverview.kt
 * Date : 2026/09/20 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** 某类文件的统计：文件数量、总行数（代码/注释/空行）与总字节数。 */
data class FileTypeStat(
    val extension: String,
    val displayName: String,
    val fileCount: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int,
    val totalBytes: Long,
) {
    /** 总行数 = 代码 + 注释 + 空行。 */
    val lineCount: Int get() = codeLines + commentLines + blankLines
}

/** 体积最大的单个文件（用于 TopN 展示）。 */
data class LargeFile(
    val path: String,
    val bytes: Long,
)

/** Git 仓库统计（若项目不是 git 仓库则为 null）。 */
data class GitStat(
    val branch: String,
    val commitCount: Int,
    val lastCommit: String,
    val modifiedFiles: Int,
    val diffStat: String,
)

/** 项目概览的加载状态。 */
sealed interface ProjectOverviewState {
    /** 尚未加载。 */
    object Idle : ProjectOverviewState

    /** 正在扫描。 */
    object Loading : ProjectOverviewState

    /** 加载完成。 */
    data class Loaded(
        val directory: String,
        val stats: List<FileTypeStat>,
        val largeFiles: List<LargeFile>,
        val gitStat: GitStat?,
    ) : ProjectOverviewState {
        val totalFiles: Int get() = stats.sumOf { it.fileCount }
        val totalLines: Int get() = stats.sumOf { it.lineCount }
        val totalBytes: Long get() = stats.sumOf { it.totalBytes }
    }

    /** 加载失败。 */
    data class Error(val message: String) : ProjectOverviewState
}

/** 项目概览 shell 命令超时（毫秒）。 */
private const val PROJECT_OVERVIEW_TIMEOUT_MS = 60_000L

/** 「最大的文件」列表保留的条目数。 */
private const val TOP_LARGE_FILES = 10

/** 扫描时跳过的非源码目录（构建产物、依赖、版本库等）。 */
private val PRUNE_DIRS = listOf(
    ".git", ".hg", ".svn",
    "node_modules", "vendor",
    "build", "dist", "out", "target",
    ".gradle", ".idea", ".next", ".nuxt", ".cache",
    "__pycache__", ".venv", "venv", "coverage",
)

/** C 风格注释（`//`、`/* */`、Javadoc `*`）的扩展名集合。 */
private const val C_STYLE_EXTS =
    "java|kt|kts|js|jsx|ts|tsx|mjs|cjs|c|h|cpp|cc|cxx|hpp|cs|go|rs|swift|m|mm|scala|php|dart|groovy|gradle|sql|css|scss|less"

/** `#` 风格注释的扩展名集合。 */
private const val HASH_STYLE_EXTS =
    "py|sh|bash|zsh|rb|pl|pm|yml|yaml|properties|conf|ini|toml|r|ps1|cmake"

/**
 * 逐文件分类「代码/注释/空行」的 awk 脚本（尽力而为）。
 *
 * 通过 `find -print0 | xargs -0 awk` 对每个文件统计三类行数：
 * - 空行：整行仅空白字符；
 * - 注释：C 风格（`//`、`/* */` 含跨行块注释、Javadoc `*` 行）或 `#` 风格（`#` 开头）；
 * - 其余归为代码。
 * 字符串/多行字符串中的注释标记未做排除，属已知近似口径。
 */
private val CLASSIFY_AWK = """
    function ext_of(f,   n, p) {
        n = f; sub(/^.*\//, "", n);
        p = index(n, ".");
        return (p > 1) ? tolower(substr(n, p + 1)) : "";
    }
    FNR == 1 {
        if (NR > 1) flush();
        prev = FILENAME;
        inBlock = 0;
        code = comment = blank = 0;
        e = ext_of(FILENAME);
        if (e ~ /^($C_STYLE_EXTS)$/) style = 1;
        else if (e ~ /^($HASH_STYLE_EXTS)$/) style = 2;
        else style = 0;
    }
    {
        s = ${'$'}0;
        if (s ~ /^[[:blank:]]*${'$'}/) { blank++; next; }
        if (style == 1) {
            if (inBlock) { comment++; if (s ~ /\*\//) inBlock = 0; }
            else {
                t = s; sub(/^[[:blank:]]+/, "", t);
                if (t ~ /^\/\//) comment++;
                else if (t ~ /^\/\*/) { comment++; if (t !~ /\*\//) inBlock = 1; }
                else if (t ~ /^\*/) comment++;
                else if (t ~ /^<!--/) comment++;
                else { code++; if (t ~ /\/\*/ && t !~ /\*\//) inBlock = 1; }
            }
        } else if (style == 2) {
            t = s; sub(/^[[:blank:]]+/, "", t);
            if (t ~ /^#/) comment++; else code++;
        } else {
            code++;
        }
    }
    END { flush(); }
    function flush() {
        if (NR > 0) printf "%s\t%d\t%d\t%d\n", prev, code, comment, blank;
    }
""".trimIndent()

/**
 * 扫描当前会话项目目录，按文件类型统计文件数量、代码/注释/空行数与字节数。
 *
 * 通过连接级常驻 PTY 一次性在服务端统计（`wc -c` 取字节数，awk 分类行构成），
 * 避免逐个文件发起读文件请求。结果按类型（扩展名）聚合，并映射为可读的语言名。
 */
internal fun ChatViewModel.loadProjectOverview() {
    val directory = sessionDirectory?.trim()?.trimEnd('/')
    if (directory.isNullOrBlank()) {
        _projectOverview.value =
            ProjectOverviewState.Error(context.getString(R.string.project_overview_no_directory))
        return
    }
    if (_projectOverview.value is ProjectOverviewState.Loading) return
    _projectOverview.value = ProjectOverviewState.Loading
    viewModelScope.launch {
        try {
            val raw = overviewShell.runCommand(buildOverviewScript(directory), PROJECT_OVERVIEW_TIMEOUT_MS)
            val (stats, largeFiles) = parseOverview(raw, directory)
            val gitStat = parseGitStat(raw)
            if (stats.isEmpty()) {
                _projectOverview.value =
                    ProjectOverviewState.Error(context.getString(R.string.project_overview_empty))
            } else {
                _projectOverview.value = ProjectOverviewState.Loaded(directory, stats, largeFiles, gitStat)
            }
        } catch (e: Exception) {
            e.rethrowCancellation()
            if (BuildConfig.DEBUG) Log.d(TAG, "Failed to load project overview: ${e.message}")
            _projectOverview.value =
                ProjectOverviewState.Error(context.getString(R.string.project_overview_error))
        }
    }
}

/**
 * 组装统计脚本，输出两段（`__OVERVIEW_WC__` 字节数、`__OVERVIEW_CLASSIFY__` 行构成）。
 * 跳过 [PRUNE_DIRS]，两个 `find` 各自遍历一次（低频操作，可接受）。
 */
private fun buildOverviewScript(directory: String): String {
    val q = "'" + directory.replace("'", "'\\''") + "'"
    val prune = PRUNE_DIRS.joinToString(" -o ") { "-path '*/$it'" }
    return buildString {
        append("printf '\\n__OVERVIEW_WC__\\n'\n")
        append("find $q \\( $prune \\) -prune -o -type f -print0 2>/dev/null | xargs -0 wc -c 2>/dev/null\n")
        append("printf '\\n__OVERVIEW_CLASSIFY__\\n'\n")
        append("find $q \\( $prune \\) -prune -o -type f -print0 2>/dev/null | xargs -0 awk '")
        append(CLASSIFY_AWK)
        append("' 2>/dev/null\n")
        append("printf '\\n__OVERVIEW_GIT__\\n'\n")
        append("cd $q 2>/dev/null && {\n")
        append("  echo \"branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)\"\n")
        append("  echo \"commits=$(git rev-list --count HEAD 2>/dev/null)\"\n")
        append("  echo \"last=$(git log -1 --format='%h %s' 2>/dev/null | head -1)\"\n")
        append("  echo \"modified=$(git status --porcelain 2>/dev/null | wc -l)\"\n")
        append("  echo \"diffstat=$(git diff --shortstat 2>/dev/null | tr -d '\\n')\"\n")
        append("} || echo 'not_a_repo'\n")
        append("printf '\\n__OVERVIEW_END__\\n'\n")
    }
}

/** 提取两段统计并聚合为 [FileTypeStat] 列表与 [LargeFile] TopN。 */
private fun parseOverview(raw: String, directory: String): Pair<List<FileTypeStat>, List<LargeFile>> {
    val wcSection = sectionOf(raw, "__OVERVIEW_WC__", "__OVERVIEW_CLASSIFY__")
    val classifySection = sectionOf(raw, "__OVERVIEW_CLASSIFY__", "__OVERVIEW_END__")

    // path -> 字节数（来自 wc -c）。
    val bytesByPath = LinkedHashMap<String, Long>()
    for (line in wcSection.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val sep = trimmed.indexOfFirst { it == ' ' || it == '\t' }
        if (sep <= 0) continue
        val bytes = trimmed.substring(0, sep).toLongOrNull() ?: continue
        val path = trimmed.substring(sep).trim()
        if (path.isEmpty() || path == "total") continue
        bytesByPath[path] = bytes
    }

    // path -> (code, comment, blank)（来自 awk 分类）。
    data class LineSplit(val code: Int, val comment: Int, val blank: Int)
    val splitByPath = LinkedHashMap<String, LineSplit>()
    for (line in classifySection.lineSequence()) {
        val parts = line.split('\t')
        if (parts.size < 4) continue
        val code = parts[1].trim().toIntOrNull() ?: continue
        val comment = parts[2].trim().toIntOrNull() ?: continue
        val blank = parts[3].trim().toIntOrNull() ?: continue
        splitByPath[parts[0]] = LineSplit(code, comment, blank)
    }

    if (splitByPath.isEmpty()) return emptyList<FileTypeStat>() to emptyList()

    // 聚合：ext -> FileTypeStat（未合并展示名前的中间态）。
    data class Accum(val files: Int, val code: Int, val comment: Int, val blank: Int, val bytes: Long)
    val perExt = LinkedHashMap<String, Accum>()
    for ((path, split) in splitByPath) {
        val ext = extensionOf(path)
        val bytes = bytesByPath[path] ?: 0L
        val prev = perExt[ext]
        perExt[ext] = if (prev == null) {
            Accum(1, split.code, split.comment, split.blank, bytes)
        } else {
            Accum(prev.files + 1, prev.code + split.code, prev.comment + split.comment, prev.blank + split.blank, prev.bytes + bytes)
        }
    }

    val perExtStat = perExt.map { (ext, acc) ->
        FileTypeStat(
            extension = ext,
            displayName = displayNameFor(ext),
            fileCount = acc.files,
            codeLines = acc.code,
            commentLines = acc.comment,
            blankLines = acc.blank,
            totalBytes = acc.bytes,
        )
    }
    // 同类语言可能有多种扩展名（如 .js/.jsx），按展示名合并后按行数、文件数降序排列。
    val stats = perExtStat
        .groupBy { it.displayName }
        .map { (name, group) ->
            FileTypeStat(
                extension = group.first().extension,
                displayName = name,
                fileCount = group.sumOf { it.fileCount },
                codeLines = group.sumOf { it.codeLines },
                commentLines = group.sumOf { it.commentLines },
                blankLines = group.sumOf { it.blankLines },
                totalBytes = group.sumOf { it.totalBytes },
            )
        }
        .sortedWith(compareByDescending<FileTypeStat> { it.lineCount }.thenByDescending { it.fileCount })

    // TopN 最大文件：按字节数降序，展示相对项目目录的路径。
    val largeFiles = bytesByPath.entries
        .sortedByDescending { it.value }
        .take(TOP_LARGE_FILES)
        .map { (path, bytes) -> LargeFile(relativePath(directory, path), bytes) }

    return stats to largeFiles
}

/** 解析 `__OVERVIEW_GIT__` 段，返回 [GitStat]；非 git 仓库返回 null。 */
private fun parseGitStat(raw: String): GitStat? {
    val section = sectionOf(raw, "__OVERVIEW_GIT__", "__OVERVIEW_END__")
    if (section.isBlank() || section.contains("not_a_repo")) return null
    var branch = ""
    var commitCount = 0
    var lastCommit = ""
    var modifiedFiles = 0
    var diffStat = ""
    for (line in section.lineSequence()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("branch=") -> branch = trimmed.removePrefix("branch=").trim()
            trimmed.startsWith("commits=") -> commitCount = trimmed.removePrefix("commits=").trim().toIntOrNull() ?: 0
            trimmed.startsWith("last=") -> lastCommit = trimmed.removePrefix("last=").trim()
            trimmed.startsWith("modified=") -> modifiedFiles = trimmed.removePrefix("modified=").trim().toIntOrNull() ?: 0
            trimmed.startsWith("diffstat=") -> diffStat = trimmed.removePrefix("diffstat=").trim()
        }
    }
    if (branch.isBlank()) return null
    return GitStat(branch, commitCount, lastCommit, modifiedFiles, diffStat)
}

/** 提取 [begin] 与 [end] 两个标记行之间的内容。 */
private fun sectionOf(raw: String, begin: String, end: String): String {
    val lines = raw.lineSequence().toList()
    val beginIdx = lines.indexOf(begin)
    val endIdx = lines.indexOf(end)
    if (beginIdx == -1 || endIdx == -1 || endIdx <= beginIdx) return ""
    return lines.subList(beginIdx + 1, endIdx).joinToString("\n")
}

/** 将绝对路径转为相对项目目录的路径（不在目录内时回退原样）。 */
private fun relativePath(directory: String, path: String): String {
    val prefix = directory.trimEnd('/') + "/"
    return if (path.startsWith(prefix)) path.removePrefix(prefix) else path
}

/** 从文件路径提取小写扩展名；无扩展名（含隐藏文件）归为 `(none)`。 */
private fun extensionOf(path: String): String {
    val name = path.substringAfterLast('/')
    val dot = name.lastIndexOf('.')
    return if (dot <= 0) "(none)" else name.substring(dot + 1).lowercase()
}

/** 将扩展名映射为可读的语言/类型名，未识别的回退为大写扩展名。 */
private fun displayNameFor(ext: String): String = when (ext) {
    "java" -> "Java"
    "kt", "kts" -> "Kotlin"
    "vue" -> "Vue"
    "js", "jsx", "mjs", "cjs" -> "JavaScript"
    "ts", "tsx", "mts", "cts" -> "TypeScript"
    "py" -> "Python"
    "c" -> "C"
    "h", "hpp" -> "C/C++ Header"
    "cpp", "cc", "cxx" -> "C++"
    "cs" -> "C#"
    "go" -> "Go"
    "rs" -> "Rust"
    "rb" -> "Ruby"
    "php" -> "PHP"
    "swift" -> "Swift"
    "m", "mm" -> "Objective-C"
    "sh", "bash", "zsh" -> "Shell"
    "gradle", "groovy" -> "Groovy"
    "json" -> "JSON"
    "xml" -> "XML"
    "yml", "yaml" -> "YAML"
    "md", "markdown" -> "Markdown"
    "html", "htm" -> "HTML"
    "css", "scss", "sass", "less" -> "CSS"
    "sql" -> "SQL"
    "properties" -> "Properties"
    "toml" -> "TOML"
    "ini", "conf", "config" -> "Config"
    else -> ext.uppercase()
}
