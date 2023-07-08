package com.wafflestudio.k8s.job

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

data class Job(
    val type: Type,
    val namespace: String,
    val name: String,
    val cronJobName: String?,
    val status: Status,
    val startTime: Instant,
    val completionTime: Instant?,
) {
    enum class Type {
        ADDED, DELETED, MODIFIED, UNKNOWN
    }

    enum class Status {
        COMPLETE, FAILED, UNKNOWN
    }

    companion object {
        fun fromOrNull(jsonNode: JsonNode): Job? = jsonNode.run {
            val type = get("type")?.asText() ?: return null
            val namespace = get("object")?.get("metadata")?.get("namespace")?.asText() ?: return null
            val name = get("object")?.get("metadata")?.get("name")?.asText() ?: return null
            val startTime = get("object")?.get("status")?.get("startTime")?.asText() ?: return null
            val cronJobName = get("object")?.get("metadata")?.get("ownerReferences")
                ?.firstOrNull { it.get("kind")?.asText() == "CronJob" }
                ?.get("name")
                ?.asText()
            val status = get("object")?.get("status")?.get("conditions")
                ?.firstOrNull()
                ?.get("type")
                ?.asText()
            val completionTime = get("object")?.get("status")?.get("completionTime")?.asText()

            return Job(
                type = Type.values().firstOrNull { it.name == type.uppercase() } ?: Type.UNKNOWN,
                namespace = namespace,
                name = name,
                cronJobName = cronJobName,
                status = Status.values().firstOrNull { it.name == status?.uppercase() } ?: Status.UNKNOWN,
                startTime = Instant.parse(startTime),
                completionTime = completionTime?.let { Instant.parse(it) }
            )
        }
    }
}
