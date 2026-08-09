package com.wafflestudio.k8s.job

import com.slack.api.Slack
import com.slack.api.methods.request.files.FilesUploadV2Request
import com.wafflestudio.k8s.discord.DiscordAlertClient
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface JobAlert {
    suspend fun invoke(job: Job): Boolean
}

@ConditionalOnProperty(name = ["alert.provider"], havingValue = "discord")
@Component
class DiscordJobAlert(
    private val client: DiscordAlertClient,
) : JobAlert {
    override suspend fun invoke(job: Job): Boolean =
        client.send(
            comment = "[Job Failed]\nNamespace: ${job.namespace}\nCronJob: ${job.cronJobName}",
            fileName = "${job.namespace}-${job.cronJobName}.txt",
            content = job.alertMessage,
        )
}

@ConditionalOnProperty(name = ["alert.provider"], havingValue = "slack")
@Component
class SlackJobAlert(
    @Value("\${slack.token}") token: String,
    @Value("\${slack.default-channel-id:C05FSP4MEVC}") private val defaultChannelId: String,
) : JobAlert {
    private val client = Slack.getInstance().methodsAsync(token)
    private val log = LoggerFactory.getLogger(javaClass)
    private val namespaceToChannel = emptyMap<String, String>()

    override suspend fun invoke(job: Job): Boolean {
        val channel = namespaceToChannel[job.namespace] ?: defaultChannelId
        val fileName = "${job.namespace}-${job.cronJobName}.txt"

        return runCatching {
            client.filesUploadV2 { req ->
                req.channel(channel)
                    .uploadFiles(listOf(
                        FilesUploadV2Request.UploadFile.builder()
                            .content(job.alertMessage)
                            .filename(fileName)
                            .title(fileName)
                            .build()
                    ))
                    .initialComment("[Job Failed]\nNamespace: ${job.namespace}\nCronJob: ${job.cronJobName}")
            }
                .await()
                .isOk
        }.getOrElse {
            log.error("Failed to upload job alert to Slack (channel: {}, job: {}.{})", channel, job.namespace, job.name, it)
            false
        }
    }
}

@ConditionalOnProperty(name = ["alert.provider"], havingValue = "none", matchIfMissing = true)
@Component
class NoOpJobAlert : JobAlert {
    override suspend fun invoke(job: Job): Boolean {
        println(job.alertMessage)
        return true
    }
}

val Job.alertMessage
    get() = buildString {
        appendLine("[Job Failed]")
        appendLine("Namespace: $namespace")
        appendLine("CronJob: $cronJobName")
        appendLine("Job: $name")
        appendLine("Status: $status")
        appendLine("StartTime: ${ZonedDateTime.ofInstant(startTime, ZoneOffset.ofHours(9))}")
    }
