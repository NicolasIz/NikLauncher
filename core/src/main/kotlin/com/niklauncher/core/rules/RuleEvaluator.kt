package com.niklauncher.core.rules

/**
 * Evaluates Mojang rule lists.
 *
 * The contract Mojang uses: an empty list allows everything, otherwise the
 * entry starts out denied and each matching rule overwrites the verdict, so the
 * last match wins.
 */
object RuleEvaluator {

    fun isAllowed(rules: List<Rule>, environment: LaunchEnvironment): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, environment)) {
                allowed = rule.action == RuleAction.ALLOW
            }
        }
        return allowed
    }

    private fun matches(rule: Rule, environment: LaunchEnvironment): Boolean {
        rule.os?.let { os ->
            if (os.name != null && !os.name.equals(environment.osName, ignoreCase = true)) return false
            if (os.arch != null && !os.arch.equals(environment.osArch, ignoreCase = true)) return false
            if (os.version != null) {
                // A malformed pattern must not take the whole resolution down.
                val pattern = runCatching { Regex(os.version) }.getOrNull() ?: return false
                if (!pattern.containsMatchIn(environment.osVersion)) return false
            }
        }
        for ((feature, expected) in rule.features) {
            if ((environment.features[feature] ?: false) != expected) return false
        }
        return true
    }
}
