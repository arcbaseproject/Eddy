package app.eddy.browser.privacy

/** Conservative subset of Adblock network syntax. Unsupported options never become broader blocks. */
internal class NetworkRule private constructor(
    val domain: String?,
    val exception: Boolean,
    private val pattern: Regex,
    private val thirdParty: Boolean?,
    private val includedPages: List<String>,
    private val excludedPages: List<String>,
) {
    fun matches(url: String, pageHost: String, thirdParty: Boolean): Boolean =
        (this.thirdParty == null || this.thirdParty == thirdParty) &&
            (includedPages.isEmpty() || includedPages.any { within(pageHost, it) }) &&
            excludedPages.none { within(pageHost, it) } && pattern.containsMatchIn(url)

    companion object {
        private fun within(host: String, domain: String) = host == domain || host.endsWith(".$domain")

        fun parse(raw: String): NetworkRule? {
            var text = raw.trim()
            val exception = text.startsWith("@@")
            if (exception) text = text.drop(2)
            if (text.isEmpty() || text.startsWith('!') || text.startsWith('#') || text.contains("##") || text.contains("#@#")) return null
            val parts = text.split('$', limit = 2)
            val body = parts[0]
            if (!body.startsWith("||") && !body.startsWith('/') && !body.startsWith("|http")) return null
            var thirdParty: Boolean? = null
            var included = emptyList<String>()
            var excluded = emptyList<String>()
            for (option in parts.getOrNull(1)?.split(',').orEmpty()) {
                when {
                    option == "third-party" -> thirdParty = true
                    option == "~third-party" -> thirdParty = false
                    option.startsWith("domain=") -> {
                        val domains = option.removePrefix("domain=").lowercase().split('|')
                        if (domains.any { it.removePrefix("~").isEmpty() }) return null
                        included = domains.filterNot { it.startsWith('~') }
                        excluded = domains.filter { it.startsWith('~') }.map { it.drop(1) }
                    }
                    else -> return null
                }
            }
            val domain = if (body.startsWith("||")) {
                body.drop(2).takeWhile { it !in "^/*:|" }.lowercase()
                    .takeIf { ContentBlocker.parseLine(it) != null } ?: return null
            } else null
            // Slash-delimited regular expressions need a separate engine; do not reinterpret them as literals.
            if (body.startsWith('/') && body.endsWith('/')) return null
            val source = if (domain != null) body.drop(2) else body.removePrefix("|")
            val expression = buildString {
                if (domain != null) append("^[a-z][a-z0-9+.-]*://(?:[^/?#]+\\.)?")
                else if (body.startsWith('|')) append('^')
                source.forEachIndexed { index, char ->
                    when (char) {
                        '*' -> append(".*")
                        '^' -> append("(?:[^a-zA-Z0-9_.%\\-]|$)")
                        '|' -> if (index == source.lastIndex) append('$') else append("\\|")
                        else -> append(Regex.escape(char.toString()))
                    }
                }
            }
            return NetworkRule(domain, exception, Regex(expression, RegexOption.IGNORE_CASE), thirdParty, included, excluded)
        }
    }
}

/** Domain index avoids scanning a full subscription for every resource. */
internal class NetworkRules(lines: Iterable<String>) {
    private val rules = lines.mapNotNull(NetworkRule::parse).groupBy { it.domain }

    fun decision(url: String, host: String, pageHost: String, thirdParty: Boolean): Boolean? {
        var blocked = false
        val candidates = sequence {
            yieldAll(rules[null].orEmpty())
            var domain = host
            while (domain.contains('.')) {
                yieldAll(rules[domain].orEmpty())
                domain = domain.substringAfter('.')
            }
        }
        for (rule in candidates) {
            if (!rule.matches(url, pageHost, thirdParty)) continue
            if (rule.exception) return false
            blocked = true
        }
        return if (blocked) true else null
    }
}
