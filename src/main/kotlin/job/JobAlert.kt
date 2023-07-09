package com.wafflestudio.k8s.job

import com.slack.api.Slack
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

        return client.filesUpload { builder ->
            builder.apply {
                filetype("text")
                title("${job.namespace}-${job.cronJobName}.txt")
                channels(listOf(channel))
                content(job.alertMessage)
                initialComment("[Job Failed]\nNamespace: ${job.namespace}\nCronJob: ${job.cronJobName}")
            }
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
