package com.wafflestudio.k8s.pod

import com.slack.api.Slack
import com.wafflestudio.k8s.pod.Pod.ContainerStatus.Waiting
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface PodAlert {
    fun invoke(pod: Pod)

    val Pod.message: String
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

    @ConditionalOnProperty("slack.token", matchIfMissing = false)
    @Component
    class SlackPodAlert(
        @Value("\${slack.token}") token: String,
    ) : PodAlert {
        private val client = Slack.getInstance().methodsAsync(token)
        private val namespaceToChannel = emptyMap<String, String>()

        override fun invoke(pod: Pod) {
            val channel = namespaceToChannel[pod.namespace] ?: "k8s-알람"

            client.filesUpload { builder ->
                builder.apply {
                    filetype("text")
                    title("${pod.namespace}-${pod.name}.txt")
                    channels(listOf(channel))
                    content(pod.message)
                    initialComment("[Pod Failed]\nNamespace: ${pod.namespace}\nPod: ${pod.name}")
                }
            }
        }
    }

    @ConditionalOnMissingBean(SlackPodAlert::class)
    @Component
    class NoOpPodAlert : PodAlert {
        override fun invoke(pod: Pod) {
            println(pod.message)
        }
    }
}
