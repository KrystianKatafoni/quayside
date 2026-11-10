package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.time.TimeSource

/**
 * Manual measurement of AM-311 (M1-M7) on the real `<claudeHome>/projects`. Skipped without
 * `QUAYSIDE_AM311_SCAN`, so CI and `check` never run it (ENG-T6, ENG-T7). Optional
 * `QUAYSIDE_AM311_PROJECTS` is a comma-separated list of project directories for M4.
 *
 * The report `build/am311/report.md` holds aggregates only: counts, sizes, times and sanitized entry type names.
 * Never a path, a directory name, a UUID or a text. M8 (supervisor ids) and M9 (`--resume`) are manual.
 */
@EnabledIfEnvironmentVariable(named = "QUAYSIDE_AM311_SCAN", matches = ".+")
class SessionFilesOnDiskMeasurement {
    private class Scan(
        val files: List<Pair<Path, String>>,
        val dirCount: Int,
        val subdirCount: Int,
        val otherCount: Int,
    )

    @Test
    fun `scan session files and write the aggregate report`() {
        val root = expand(checkNotNull(System.getenv("QUAYSIDE_AM311_SCAN")))
        val projects =
            System
                .getenv("QUAYSIDE_AM311_PROJECTS")
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
        val scan = scan(root)
        val facts = scan.files.map { (file, dir) -> FileFactsReader.read(file, dir) }

        val report =
            listOf("# AM-311 pomiar plików sesji (agregaty)", "") +
                structure(scan) + firstEntries(facts) + directoryRule(facts) + projectDetection(scan, facts, projects) +
                skipped(facts) + title(facts) + cost(scan)
        val out = Path.of("build/am311").createDirectories().resolve("report.md")
        out.writeText(report.joinToString("\n", postfix = "\n"))
    }

    private fun expand(path: String): Path =
        Path.of(
            if (path.startsWith("~")) {
                System.getProperty("user.home") +
                    path.drop(1)
            } else {
                path
            },
        )

    private fun scan(root: Path): Scan {
        val dirs = Files.list(root).use { s -> s.filter { it.isDirectory() }.toList() }
        val files = mutableListOf<Pair<Path, String>>()
        var subdirs = 0
        var other = 0
        dirs.forEach { dir ->
            Files.list(dir).use { s -> s.toList() }.forEach { entry ->
                when {
                    entry.isDirectory() -> subdirs++
                    entry.isRegularFile() && entry.extension == "jsonl" -> files += entry to dir.name
                    else -> other++
                }
            }
        }
        return Scan(files, dirs.size, subdirs, other)
    }

    private fun structure(scan: Scan): List<String> =
        listOf(
            "## Struktura",
            "- katalogi projektów: ${scan.dirCount}",
            "- pliki `.jsonl`: ${scan.files.size}",
            "- podkatalogi w katalogach projektów: ${scan.subdirCount}",
            "- inne pliki: ${scan.otherCount}",
            "",
        )

    private fun firstEntries(facts: List<FileFacts>): List<String> {
        val perPosition =
            (0 until FIRST_ENTRIES).map { i ->
                facts
                    .mapNotNull { it.firstTypes.getOrNull(i) }
                    .groupingBy { it }
                    .eachCount()
                    .toSortedMap()
            }
        return listOf(
            "## M1 początek pliku",
            "- pierwszy wpis ma `cwd`: ${ratio(facts.count { it.firstEntryHasCwd }, facts.size)}",
        ) +
            perPosition.mapIndexed { i, histogram -> "- typy wpisu ${i + 1}: $histogram" } + ""
    }

    private fun directoryRule(facts: List<FileFacts>): List<String> {
        val withCwd = facts.filter { it.firstCwd != null }
        val first = withCwd.count { matches(it.firstCwd, it) }
        val last = withCwd.count { matches(it.lastCwd, it) }
        val multi = facts.count { it.distinctCwds > 1 }
        return listOf(
            "## M2 i M3 kodowanie nazwy katalogu",
            "- pliki z jakimkolwiek `cwd`: ${withCwd.size}",
            "- `dirNameOf(pierwszy cwd)` = nazwa katalogu: ${ratio(first, withCwd.size)}",
            "- `dirNameOf(ostatni cwd)` = nazwa katalogu: ${ratio(last, withCwd.size)}",
            "- pliki z więcej niż jednym `cwd` (głowa + ogon): $multi",
        ) + subsets(withCwd) + ""
    }

    private fun subsets(withCwd: List<FileFacts>): List<String> {
        val groups: Map<String, (String) -> Boolean> =
            mapOf(
                "znak spoza ASCII" to { cwd -> cwd.any { it.code > ASCII_MAX } },
                "spacja" to { cwd -> cwd.contains(' ') },
                "dłuższe niż 200 znaków" to { cwd -> cwd.length > LONG_CWD },
            )
        return groups.map { (label, predicate) ->
            val subset = withCwd.filter { predicate(checkNotNull(it.firstCwd)) }
            "- ścieżki: $label: pliki ${subset.size}, zgodne ${subset.count { matches(it.firstCwd, it) }}"
        }
    }

    private fun matches(
        cwd: String?,
        facts: FileFacts,
    ): Boolean = cwd != null && SessionFiles.dirNameOf(Path.of(cwd)) == facts.dirName

    private fun projectDetection(
        scan: Scan,
        facts: List<FileFacts>,
        projects: List<String>,
    ): List<String> {
        val dirNames = scan.files.map { it.second }.distinct()
        val lines =
            projects.mapIndexed { i, text ->
                val project = Path.of(text)
                val byName = SessionFiles.candidates(project, dirNames).toSet()
                val inDirs = facts.filter { it.dirName in byName }
                val byCwd =
                    inDirs.count {
                        it.firstCwd
                            ?.let(Path::of)
                            ?.normalize()
                            ?.startsWith(project.normalize()) ==
                            true
                    }
                "- projekt ${i + 1}: po nazwie katalogu ${inDirs.size}, po `cwd` $byCwd, fałszywi kandydaci ${inDirs.size - byCwd}"
            }
        return listOf("## M4 wykrywanie projektu", "- projekty podane: ${projects.size}") + lines + ""
    }

    private fun skipped(facts: List<FileFacts>): List<String> =
        listOf(
            "## M5 pliki do pominięcia",
            "- nazwa `agent-*.jsonl`: ${facts.count { it.agentFileName }}",
            "- `isSidechain` w pierwszym wpisie: ${facts.count { it.sidechain }}",
            "- bez wpisu `user` w głowie: ${facts.count { it.firstUser == FirstUser.NONE }}",
            "- `sessionId` = nazwa pliku: ${ratio(facts.count { it.idMatchesStem }, facts.size)}",
            "",
        )

    private fun title(facts: List<FileFacts>): List<String> =
        listOf(
            "## M7 tytuł",
            "- pierwszy `user` w głowie: ${facts.groupingBy { it.firstUser }.eachCount().toSortedMap()}",
            "- jest wpis `summary`: ${ratio(facts.count { it.hasSummary }, facts.size)}",
            "",
        )

    @Suppress("InjectDispatcher")
    private fun cost(scan: Scan): List<String> {
        val reader = SessionFileReader(Dispatchers.IO)
        val sizes = scan.files.map { Files.size(it.first) }
        val stat = timed(scan.files) { Files.getLastModifiedTime(it) }
        val first = timed(scan.files) { read(reader, it) }
        val second = timed(scan.files) { read(reader, it) }
        return listOf(
            "## M6 koszt skanu",
            "- rozmiar pliku bajty: p50 ${percentile(
                sizes,
                P50,
            )}, p95 ${percentile(sizes, P95)}, max ${sizes.maxOrNull() ?: 0}",
            "- `stat` mikrosekundy: p50 ${percentile(
                stat,
                P50,
            ) / NANOS_PER_MICRO}, p95 ${percentile(stat, P95) / NANOS_PER_MICRO}",
            "- `read` pierwszy przebieg (zimny po `sudo purge`) ms: ${describe(first)}",
            "- `read` drugi przebieg (ciepły) ms: ${describe(second)}",
            "",
        )
    }

    @Suppress("ForbiddenMethodCall")
    private fun read(
        reader: SessionFileReader,
        file: Path,
    ): Outcome<SessionFileSummary, SessionFileProblem> = runBlocking { reader.read(file) }

    private fun timed(
        files: List<Pair<Path, String>>,
        action: (Path) -> Any,
    ): List<Long> =
        files.map { (file, _) ->
            val mark = TimeSource.Monotonic.markNow()
            action(file)
            mark.elapsedNow().inWholeNanoseconds
        }

    private fun describe(nanos: List<Long>): String =
        "p50 ${percentile(nanos, P50) / NANOS_PER_MILLI}, p95 ${percentile(nanos, P95) / NANOS_PER_MILLI}, " +
            "suma ${nanos.sum() / NANOS_PER_MILLI}"

    private companion object {
        const val FIRST_ENTRIES = 5
        const val ASCII_MAX = 127
        const val LONG_CWD = 200
        const val P50 = 50
        const val P95 = 95
        const val NANOS_PER_MICRO = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
