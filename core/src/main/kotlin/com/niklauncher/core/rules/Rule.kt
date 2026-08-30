package com.niklauncher.core.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Whether a matching [Rule] turns a feature on or off. */
@Serializable
enum class RuleAction {
    @SerialName("allow")
    ALLOW,

    @SerialName("disallow")
    DISALLOW,
}

/**
 * The `os` block of a rule. Every non-null field must match for the rule to
 * apply; [version] is a regular expression, as Mojang writes it.
 */
@Serializable
data class OsConstraint(
    val name: String? = null,
    val version: String? = null,
    val arch: String? = null,
)

/**
 * A single conditional entry as it appears in a Mojang version manifest, used
 * to gate libraries and command-line arguments.
 */
@Serializable
data class Rule(
    val action: RuleAction = RuleAction.ALLOW,
    val os: OsConstraint? = null,
    val features: Map<String, Boolean> = emptyMap(),
)
