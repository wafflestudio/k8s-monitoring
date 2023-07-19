package com.wafflestudio.k8s.pod

import com.fasterxml.jackson.databind.JsonNode
import com.wafflestudio.k8s.pod.Pod.ContainerStatus.Running
import com.wafflestudio.k8s.pod.Pod.ContainerStatus.Terminated
import com.wafflestudio.k8s.pod.Pod.ContainerStatus.UnKnown
import com.wafflestudio.k8s.pod.Pod.ContainerStatus.Waiting
import java.time.Instant

data class Pod(
    val type: Type,
    val namespace: String,
    val name: String,
    val phase: Phase,
    val containerStatuses: List<ContainerStatus>,
    val startTime: Instant,
) {
    enum class Type {
        ADDED, DELETED, MODIFIED, UNKNOWN
    }

    enum class Phase {
        RUNNING, PENDING, UNKNOWN
    }

    sealed class ContainerStatus {
        abstract val name: String
        abstract val image: String

        data class Running(
            override val name: String,
            override val image: String,
            val startedAt: Instant,
        ) : ContainerStatus()

        data class Waiting(
            override val name: String,
            override val image: String,
            val reason: String,
            val message: String?,
        ) : ContainerStatus() {
            val failed: Boolean get() = reason != "PodInitializing" && reason != "ContainerCreating"
        }

        data class Terminated(
            override val name: String,
            override val image: String,
            val exitCode: Int,
            val reason: String,
            val startedAt: Instant,
            val finishedAt: Instant,
        ) : ContainerStatus()

        data class UnKnown(
            override val name: String,
            override val image: String,
            val statusJson: String,
        ) : ContainerStatus()
    }

    companion object {
        fun fromOrNull(jsonNode: JsonNode): Pod? = jsonNode.run {
            val type = get("type")?.asText() ?: return null
            val namespace = get("object")?.get("metadata")?.get("namespace")?.asText() ?: return null
            val name = get("object")?.get("metadata")?.get("name")?.asText() ?: return null
            val phase = get("object")?.get("status")?.get("phase")?.asText() ?: return null
            val startTime = get("object")?.get("status")?.get("startTime")?.asText() ?: return null
            val containerStatuses = get("object")?.get("status")?.get("containerStatuses")
                ?.map {
                    val containerName = it["name"].asText()
                    val containerImage = it["image"].asText()

                    when {
                        it["state"]["running"] != null -> Running(
                            name = containerName,
                            image = containerImage,
                            startedAt = Instant.parse(it["state"]["running"]["startedAt"].asText())
                        )

                        it["state"]["waiting"] != null -> Waiting(
                            name = containerName,
                            image = containerImage,
                            reason = it["state"]["waiting"]["reason"].asText(),
                            message = it["state"]["waiting"]["message"]?.asText(),
                        )

                        it["state"]["terminated"] != null -> Terminated(
                            name = containerName,
                            image = containerImage,
                            exitCode = it["state"]["terminated"]["exitCode"].asInt(),
                            reason = it["state"]["terminated"]["reason"].asText(),
                            startedAt = Instant.parse(it["state"]["terminated"]["startedAt"].asText()),
                            finishedAt = Instant.parse(it["state"]["terminated"]["finishedAt"].asText())
                        )

                        else -> UnKnown(
                            name = containerName,
                            image = containerImage,
                            statusJson = it.toPrettyString(),
                        )
                    }
                }
                ?: emptyList()

            return Pod(
                type = Type.values().firstOrNull { it.name == type.uppercase() } ?: Type.UNKNOWN,
                namespace = namespace,
                name = name,
                phase = Phase.values().firstOrNull { it.name == phase.uppercase() } ?: Phase.UNKNOWN,
                containerStatuses = containerStatuses,
                startTime = Instant.parse(startTime),
            )
                .also { // for debug
                    if (it.type == Type.UNKNOWN || it.phase == Phase.UNKNOWN || it.containerStatuses.any { it is UnKnown }) {
                        println("Unknown Pod $it")
                    }
                }
        }
    }
}
