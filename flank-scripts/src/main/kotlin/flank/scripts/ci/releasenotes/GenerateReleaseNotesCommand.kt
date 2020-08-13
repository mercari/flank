package flank.scripts.ci.releasenotes

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import flank.scripts.ci.getLatestReleaseTag
import flank.scripts.ci.nexttag.generateNextReleaseTag
import kotlinx.coroutines.*
import java.io.File

class GenerateReleaseNotesCommand :
    CliktCommand("Command to append item to release notes", name = "generateReleaseNotes") {

    private val token by option(help = "Git Token")
        .default("9a232b2d3defdbfca6b1a4f5c636c11b7837718c")
        //.default(System.getenv("GITHUB_TOKEN"))
    internal val releaseNotesFile by option(help = "Path to release_notes.md").default("/Users/piotr/Projekty/gogo/flank/release_notes.md")

    override fun run() {
        runBlocking {
            val latestReleaseTag = getLatestReleaseTag(token)
            File(releaseNotesFile).appendReleaseNotes(
                messages = generateReleaseNotes(latestReleaseTag, token),
                releaseTag = generateNextReleaseTag(latestReleaseTag)
            )

            //"824eea3220100ceba183088248726107d32bcce1"
            // /Users/piotr/Projekty/gogo/flank/release_notes.md
        }
    }
}
