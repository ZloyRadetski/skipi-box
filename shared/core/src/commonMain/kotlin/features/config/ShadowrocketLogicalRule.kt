// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/**
 * A condition inside an OR / AND rule: either one plain `(TYPE,value)` match or a nested group.
 * NOT is not modelled: Xray has no inversion, so such lines stay preserved text.
 */
sealed interface ShadowrocketCondition {
    data class Leaf(
        val type: String,
        val value: String,
        /** Trailing flags such as `no-resolve`, kept so the line serialises back unchanged. */
        val option: String? = null,
    ) : ShadowrocketCondition

    data class Group(
        val operator: ShadowrocketLogicalOperator,
        val children: List<ShadowrocketCondition>,
    ) : ShadowrocketCondition
}

enum class ShadowrocketLogicalOperator { Or, And }

val ShadowrocketLogicalOperator.keyword: String
    get() = when (this) {
        ShadowrocketLogicalOperator.Or -> "OR"
        ShadowrocketLogicalOperator.And -> "AND"
    }

fun ShadowrocketCondition.Group.depth(): Int =
    1 + (children.filterIsInstance<ShadowrocketCondition.Group>().maxOfOrNull { it.depth() } ?: 0)

/** `((A),(B))` as written after the operator: every child parenthesised and the list parenthesised again. */
fun ShadowrocketCondition.Group.toBodyText(): String = children.joinToString(",", prefix = "(", postfix = ")") { child ->
    when (child) {
        is ShadowrocketCondition.Leaf -> listOfNotNull(child.type, child.value, child.option).joinToString(",", "(", ")")
        is ShadowrocketCondition.Group -> "(${child.operator.keyword},${child.toBodyText()})"
    }
}

class ShadowrocketLogicalRuleParts(
    val group: ShadowrocketCondition.Group,
    val policy: String,
    val option: String?,
)

private const val MaxLogicalRuleDepth = 16

/**
 * Parses `OR,((TYPE,value),(AND,((TYPE,value),(TYPE,value)))),POLICY[,option]`. Returns null for anything that is
 * not a well-formed OR / AND rule, including NOT, so the caller keeps the line as text.
 */
fun parseShadowrocketLogicalRule(line: String): ShadowrocketLogicalRuleParts? {
    val reader = LogicalRuleReader(line)
    return try {
        val operator = reader.readOperator() ?: return null
        reader.expect(',')
        val group = ShadowrocketCondition.Group(operator, reader.readChildList(depth = 1))
        reader.expect(',')
        val tail = reader.rest().split(',').map(String::trim)
        val policy = tail.first().takeIf(String::isNotEmpty) ?: return null
        ShadowrocketLogicalRuleParts(group, policy, tail.drop(1).joinToString(", ").takeIf(String::isNotBlank))
    } catch (_: LogicalRuleSyntaxError) {
        null
    }
}

private class LogicalRuleSyntaxError : Exception()

private class LogicalRuleReader(private val text: String) {
    private var index = 0

    fun rest(): String = text.substring(index)

    fun readOperator(): ShadowrocketLogicalOperator? {
        return when (readToken(setOf(',')).uppercase()) {
            "OR" -> ShadowrocketLogicalOperator.Or
            "AND" -> ShadowrocketLogicalOperator.And
            else -> null
        }
    }

    fun expect(char: Char) {
        skipSpace()
        if (index >= text.length || text[index] != char) throw LogicalRuleSyntaxError()
        index++
    }

    /** `( (child) , (child) ... )` */
    fun readChildList(depth: Int): List<ShadowrocketCondition> {
        if (depth > MaxLogicalRuleDepth) throw LogicalRuleSyntaxError()
        expect('(')
        val children = mutableListOf<ShadowrocketCondition>()
        while (true) {
            children += readChild(depth)
            skipSpace()
            when (text.getOrNull(index)) {
                ',' -> index++
                ')' -> {
                    index++
                    return children
                }
                else -> throw LogicalRuleSyntaxError()
            }
        }
    }

    private fun readChild(depth: Int): ShadowrocketCondition {
        expect('(')
        val name = readToken(setOf(',', ')')).also { if (it.isEmpty()) throw LogicalRuleSyntaxError() }
        expect(',')
        val child = when (name.uppercase()) {
            "OR" -> ShadowrocketCondition.Group(ShadowrocketLogicalOperator.Or, readChildList(depth + 1))
            "AND" -> ShadowrocketCondition.Group(ShadowrocketLogicalOperator.And, readChildList(depth + 1))
            else -> {
                val value = readToken(setOf(',', ')')).also { if (it.isEmpty()) throw LogicalRuleSyntaxError() }
                skipSpace()
                val option = if (text.getOrNull(index) == ',') {
                    index++
                    readToken(setOf(')')).takeIf(String::isNotEmpty)
                } else {
                    null
                }
                ShadowrocketCondition.Leaf(name.uppercase(), value, option)
            }
        }
        expect(')')
        return child
    }

    private fun readToken(stops: Set<Char>): String {
        val start = index
        while (index < text.length && text[index] !in stops) {
            if (text[index] == '(') throw LogicalRuleSyntaxError()
            index++
        }
        if (index >= text.length && ')' in stops) throw LogicalRuleSyntaxError()
        return text.substring(start, index).trim()
    }

    private fun skipSpace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }
}
