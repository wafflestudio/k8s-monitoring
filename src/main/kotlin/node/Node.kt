package com.wafflestudio.k8s.node

import com.fasterxml.jackson.databind.JsonNode

data class Node(
    val type: Type,
    val name: String,
    val nodeGroup: String?,
    val status: Status,
) {

    enum class Type {
        ADDED, DELETED, MODIFIED, UNKNOWN
    }

    enum class Status {
        READY, NOT_READY, UNKNOWN
    }

    val isAdded: Boolean
        get() = type == Type.ADDED && status != Status.READY

    val isDeleted: Boolean
        get() = type == Type.DELETED

    companion object {
        fun fromOrNull(jsonNode: JsonNode): Node? = jsonNode.run {
            val type = get("type")?.asText() ?: return null
            val name = get("object")?.get("metadata")?.get("name")?.asText() ?: return null
            val nodeGroup = get("object")?.get("metadata")?.get("labels")?.get("eks.amazonaws.com/nodegroup")?.asText()
            val readyStatus = get("object")?.get("status")?.get("conditions")
                ?.firstOrNull { it["type"]?.asText() == "Ready" }
                ?.get("status")
                ?.asText()

            return Node(
                type = Type.values().firstOrNull { it.name == type.uppercase() } ?: Type.UNKNOWN,
                name = name,
                nodeGroup = nodeGroup,
                status = when (readyStatus) {
                    "True" -> Status.READY
                    "False" -> Status.NOT_READY
                    else -> Status.UNKNOWN
                }
            )
        }
    }
}
