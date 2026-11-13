package com.pheeeew.domain.model.group

import kotlin.jvm.JvmInline

@JvmInline
value class GroupId private constructor(
    val value: String,
) {
    companion object {
        fun parse(value: String): GroupId? = value.takeIf(UUID_PATTERN::matches)?.let(::GroupId)

        private val UUID_PATTERN =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
