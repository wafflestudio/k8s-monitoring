package com.wafflestudio.k8s.job

import com.slack.api.Slack
import com.slack.api.methods.request.files.FilesUploadV2Request
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface JobAlert {
    suspend fun invoke(job: Job): Boolean
}

@ConditionalOnProperty("slack.token", matchIfMissing = false)
@Component
class SlackJobAlert(
    @Value("\${slack.token}") token: String,
) : JobAlert {
    private val client = Slack.getInstance().methodsAsync(token)
    private val namespaceToChannel = emptyMap<String, String>()

    override suspend fun invoke(job: Job): Boolean {
        val channel = namespaceToChannel[job.namespace] ?: "k8s-알람"
        val fileName = "${job.namespace}-${job.cronJobName}.txt"

        return client.filesUploadV2 { req ->
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
    }
}

@ConditionalOnMissingBean(SlackJobAlert::class)
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
