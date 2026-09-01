package com.niklauncher.core.launch

import com.niklauncher.core.manifest.Argument
import com.niklauncher.core.manifest.VersionJson
import com.niklauncher.core.rules.LaunchEnvironment
import com.niklauncher.core.rules.RuleEvaluator

/** A fully resolved command line, ready for the JNI Invocation API. */
data class LaunchCommand(
    val jvmArguments: List<String>,
    val mainClass: String,
    val gameArguments: List<String>,
)

/**
 * Expands a version manifest's argument templates into a concrete command line.
 *
 * Handles both encodings Mojang has used: the flat `minecraftArguments` string
 * of 1.12 and earlier, and the rule-aware `arguments` object from 1.13 on. When
 * a manifest supplies no JVM arguments - which every legacy version does - a
 * minimal set is synthesised, since the game cannot start without a classpath.
 */
class LaunchArgumentBuilder(
    private val environment: LaunchEnvironment = LaunchEnvironment.ANDROID_ARM64,
) {

    fun build(version: VersionJson, context: LaunchContext, extraJvmArguments: List<String> = emptyList()): LaunchCommand {
        val substitutions = context.substitutions()
        val ruleEnvironment = environment.copy(
            features = environment.features + context.features,
        )

        val jvm = version.arguments?.jvm
            ?.let { expand(it, ruleEnvironment, substitutions) }
            ?.takeIf { it.isNotEmpty() }
            ?: legacyJvmArguments(substitutions)

        val game = when {
            version.arguments != null -> expand(version.arguments.game, ruleEnvironment, substitutions)
            !version.minecraftArguments.isNullOrBlank() ->
                version.minecraftArguments.split(' ')
                    .filter { it.isNotBlank() }
                    .map { substitute(it, substitutions) }
            else -> emptyList()
        }

        val mainClass = version.mainClass
            ?: throw IllegalArgumentException("Version ${version.id} declares no mainClass")

        return LaunchCommand(
            jvmArguments = forInvocationApi(jvm + extraJvmArguments),
            mainClass = mainClass,
            gameArguments = game,
        )
    }

    /**
     * Rewrites the options the JNI Invocation API will not take.
     *
     * `-cp` and `-classpath` are not JVM options at all: they belong to the
     * `java` binary, which translates them into `-Djava.class.path=` before it
     * ever creates a VM. NikLauncher creates the VM itself and so never runs
     * that binary, and HotSpot answers a `-cp` in JavaVMInitArgs with
     * "Unrecognized option: -cp" and refuses to start - which is exactly how
     * the first launch on a real device died.
     *
     * Mojang's manifests emit the flag and its value as two separate entries,
     * so both have to be consumed together. A flag with nothing after it
     * cannot mean anything and is dropped.
     */
    private fun forInvocationApi(arguments: List<String>): List<String> = buildList {
        var index = 0
        while (index < arguments.size) {
            val argument = arguments[index]
            when {
                argument in CLASSPATH_FLAGS -> {
                    arguments.getOrNull(index + 1)?.let { add(CLASSPATH_PROPERTY + it) }
                    index += 2
                }

                CLASSPATH_FLAGS.any { argument.startsWith("$it=") } -> {
                    add(CLASSPATH_PROPERTY + argument.substringAfter('='))
                    index++
                }

                else -> {
                    add(argument)
                    index++
                }
            }
        }
    }

    private fun expand(
        arguments: List<Argument>,
        ruleEnvironment: LaunchEnvironment,
        substitutions: Map<String, String>,
    ): List<String> = buildList {
        for (argument in arguments) {
            when (argument) {
                is Argument.Literal -> add(substitute(argument.value, substitutions))
                is Argument.Conditional ->
                    if (RuleEvaluator.isAllowed(argument.rules, ruleEnvironment)) {
                        argument.values.forEach { add(substitute(it, substitutions)) }
                    }
            }
        }
    }

    /**
     * The classpath and native path every pre-1.13 version needs but does not
     * declare, because the vanilla launcher used to hard-code them.
     */
    private fun legacyJvmArguments(substitutions: Map<String, String>): List<String> = listOf(
        "-Djava.library.path=" + (substitutions["natives_directory"] ?: ""),
        "-cp",
        substitutions["classpath"] ?: "",
    )

    private fun substitute(template: String, substitutions: Map<String, String>): String {
        if (!template.contains("\${")) return template
        val out = StringBuilder(template.length)
        var index = 0
        while (index < template.length) {
            val start = template.indexOf("\${", index)
            if (start < 0) {
                out.append(template, index, template.length)
                break
            }
            val end = template.indexOf('}', start)
            if (end < 0) {
                out.append(template, index, template.length)
                break
            }
            out.append(template, index, start)
            val key = template.substring(start + 2, end)
            // An unknown placeholder is left intact rather than blanked, so it
            // is visible in logs instead of silently becoming an empty argument.
            out.append(substitutions[key] ?: template.substring(start, end + 1))
            index = end + 1
        }
        return out.toString()
    }

    private companion object {
        /**
         * Every spelling the `java` binary accepts, because a manifest is free
         * to use any of them and all three mean the same thing to it.
         */
        val CLASSPATH_FLAGS = setOf("-cp", "-classpath", "--class-path")
        const val CLASSPATH_PROPERTY = "-Djava.class.path="
    }
}
