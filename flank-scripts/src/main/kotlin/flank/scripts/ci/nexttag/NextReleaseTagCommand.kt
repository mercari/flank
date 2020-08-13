package flank.scripts.ci.nexttag

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import flank.scripts.ci.getLatestReleaseTag
import flank.scripts.utils.runCommand
import kotlinx.coroutines.runBlocking

class NextReleaseTagCommand : CliktCommand(help = "Set next release tag variable", name = "nextReleaseTag") {

    private val token by option(help = "Git Token").default(System.getenv("GITHUB_TOKEN").orEmpty())

    override fun run() {
        runBlocking {
            setNextReleaseTagCommand(token)
        }
    }
}

private suspend fun setNextReleaseTagCommand(token: String) {
    "echo '::set-env name=NEXT_RELEASE_TAG::${generateNextReleaseTag(
        getLatestReleaseTag(token)
    )}'".runCommand()
}
