package com.wafflestudio.k8s.pod

import com.slack.api.Slack
import com.slack.api.methods.request.files.FilesUploadV2Request
import com.wafflestudio.k8s.pod.Pod.ContainerStatus.Waiting
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface PodAlert {
    suspend fun invoke(pod: Pod): Boolean
}

@ConditionalOnProperty("slack.token", matchIfMissing = false)
@Component
class SlackPodAlert(
    @Value("\${slack.token}") token: String,
    @Value("\${slack.default-channel-id:C05FSP4MEVC}") private val defaultChannelId: String,
) : PodAlert {
    private val client = Slack.getInstance().methodsAsync(token)
    private val log = LoggerFactory.getLogger(javaClass)
    private val namespaceToChannel = emptyMap<String, String>()

    override suspend fun invoke(pod: Pod): Boolean {
        val channel = namespaceToChannel[pod.namespace] ?: defaultChannelId
        val fileName = "${pod.namespace}-${pod.name}.txt"

        return runCatching {
            client.filesUploadV2 { req ->
                req.channel(channel)
                    .uploadFiles(listOf(
                        FilesUploadV2Request.UploadFile.builder()
                            .content(pod.alertMessage)
                            .filename(fileName)
                            .title(fileName)
                            .build()
                    ))
                    .initialComment("[Pod Failed]\nNamespace: ${pod.namespace}\nPod: ${pod.name}")
            }
                .await()
                .isOk
        }.getOrElse {
            log.error("Failed to upload pod alert to Slack (channel: {}, pod: {}.{})", channel, pod.namespace, pod.name, it)
            false
        }
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
