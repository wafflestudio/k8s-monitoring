package com.wafflestudio.k8s.job

import com.slack.api.Slack
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface JobAlert {
    fun invoke(job: Job)

    val Job.message
        get() = buildString {
            appendLine("[Job Failed]")
            appendLine("Namespace: $namespace")
            appendLine("CronJob: $cronJobName")
            appendLine("Job: $name")
            appendLine("Status: $status")
            appendLine("StartTime: ${ZonedDateTime.ofInstant(startTime, ZoneOffset.ofHours(9))}")
        }

    @ConditionalOnProperty("slack.token", matchIfMissing = false)
    @Component
    class SlackJobAlert(
        @Value("\${slack.token}") token: String,
    ) : JobAlert {
        private val client = Slack.getInstance().methodsAsync(token)
        private val namespaceToChannel = emptyMap<String, String>()

        override fun invoke(job: Job) {
            val channel = namespaceToChannel[job.namespace] ?: "k8s-알람"

            client.filesUpload { builder ->
                builder.apply {
                    filetype("text")
                    title("${job.namespace}-${job.cronJobName}.txt")
                    channels(listOf(channel))
                    content(job.message)
                    initialComment("[Job Failed]\nNamespace: ${job.namespace}\nCronJob: ${job.cronJobName}")
                }
            }
        }
    }

    @ConditionalOnMissingBean(SlackJobAlert::class)
    @Component
    class NoOpJobAlert : JobAlert {
        override fun invoke(job: Job) {
            println(job.message)
        }
    }
}
