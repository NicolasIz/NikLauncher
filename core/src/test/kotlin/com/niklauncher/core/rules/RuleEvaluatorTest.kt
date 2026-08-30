package com.niklauncher.core.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleEvaluatorTest {

    private val android = LaunchEnvironment.ANDROID_ARM64

    @Test
    fun `empty rule list allows`() {
        assertTrue(RuleEvaluator.isAllowed(emptyList(), android))
    }

    @Test
    fun `non-empty rules default to denied`() {
        val rules = listOf(Rule(action = RuleAction.ALLOW, os = OsConstraint(name = "osx")))
        assertFalse(RuleEvaluator.isAllowed(rules, android))
    }

    @Test
    fun `matching os allows`() {
        val rules = listOf(Rule(action = RuleAction.ALLOW, os = OsConstraint(name = "linux")))
        assertTrue(RuleEvaluator.isAllowed(rules, android))
    }

    @Test
    fun `later disallow overrides earlier allow`() {
        val rules = listOf(
            Rule(action = RuleAction.ALLOW),
            Rule(action = RuleAction.DISALLOW, os = OsConstraint(name = "linux")),
        )
        assertFalse(RuleEvaluator.isAllowed(rules, android))
    }

    @Test
    fun `arch constraint is honoured`() {
        val armOnly = listOf(Rule(action = RuleAction.ALLOW, os = OsConstraint(arch = "arm64")))
        assertTrue(RuleEvaluator.isAllowed(armOnly, android))

        val x86Only = listOf(Rule(action = RuleAction.ALLOW, os = OsConstraint(arch = "x86")))
        assertFalse(RuleEvaluator.isAllowed(x86Only, android))
    }

    @Test
    fun `feature flags gate rules`() {
        val rules = listOf(
            Rule(action = RuleAction.ALLOW, features = mapOf("has_custom_resolution" to true)),
        )
        assertFalse(RuleEvaluator.isAllowed(rules, android))
        assertTrue(RuleEvaluator.isAllowed(rules, android.withFeature("has_custom_resolution", true)))
    }

    @Test
    fun `malformed version regex denies instead of throwing`() {
        val rules = listOf(
            Rule(action = RuleAction.ALLOW, os = OsConstraint(name = "linux", version = "^(unclosed")),
        )
        assertFalse(RuleEvaluator.isAllowed(rules, android))
    }
}
