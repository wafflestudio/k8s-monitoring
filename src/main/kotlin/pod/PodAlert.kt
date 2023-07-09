package com.wafflestudio.k8s.pod

import com.slack.api.Slack
import com.wafflestudio.k8s.pod.Pod.ContainerStatus.Waiting
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface PodAlert {
    suspend fun invoke(pod: Pod): Boolean
}

@ConditionalOnProperty("slack.token", matchIfMissing = false)
@Component
class SlackPodAlert(
    @Value("\${slack.token}") token: String,
) : PodAlert {
    private val client = Slack.getInstance().methodsAsync(token)
    private val namespaceToChannel = emptyMap<String, String>()

    override suspend fun invoke(pod: Pod): Boolean {
        val channel = namespaceToChannel[pod.namespace] ?: "k8s-알람"

        return client.filesUpload { builder ->
            builder.apply {
                filetype("text")
                title("${pod.namespace}-${pod.name}.txt")
                channels(listOf(channel))
                content(pod.alertMessage)
                initialComment("[Pod Failed]\nNamespace: ${pod.namespace}\nPod: ${pod.name}")
            }
        }
            .await()
            .isOk
    }
}

@ConditionalOnMissingBean(SlackPodAlert::class)
@Component
class NoOpPodAlert : PodAlert {
    override suspend fun invoke(pod: Pod): Boolean {
        println(pod.alertMessage)
        return true
    }
}

val Pod.alertAllowed: Boolean
    get() = metadata.alertCount < 5

val Pod.alertMessage: String
    get() = buildString {
        appendLine("[Pod Failed]")
        appendLine("Namespace: $namespace")
        appendLine("Name: $name")
        appendLine("Phase: $phase")
        appendLine("Container:")
        appendLine(
            containerStatuses
                .filterIsInstance<Waiting>()
                .filter { it.failed }
                .joinToString("\n") { "    - name: ${it.name}\n      image: ${it.image}\n      reason: ${it.reason}\n      message: ${it.message}" }
        )
        appendLine("StartTime: ${ZonedDateTime.ofInstant(startTime, ZoneOffset.ofHours(9))}")
    }
