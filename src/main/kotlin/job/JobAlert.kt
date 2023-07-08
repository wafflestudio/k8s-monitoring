package com.wafflestudio.k8s.job

import com.slack.api.Slack
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface JobAlert {
    fun invoke(job: Job)

    val Job.message
        get() = """
            [잡 실패]
            Namespace:$namespace
            CronJob:$cronJobName
            Job:$name
            Status:$status
            StartTime:${ZonedDateTime.ofInstant(startTime, ZoneOffset.ofHours(9))}
        """.trimIndent()

    @ConditionalOnProperty("slack.token", matchIfMissing = false)
    @Component
    class SlackJobAlert(
        @Value("\${slack.token}") token: String,
    ) : JobAlert {
        private val client = Slack.getInstance().methodsAsync(token)
        private val namespaceToChannel = mapOf(
            "snutt-dev" to "truffle-alert-snutt-backend-dev",
            "snutt-prod" to "truffle-alert-snutt-backend"
        )

        override fun invoke(job: Job) {
            val channel = namespaceToChannel[job.namespace] ?: "배치-알람"

            client.chatPostMessage { it.channel(channel).text(job.message) }
        }
    }

    @ConditionalOnMissingBean(SlackJobAlert::class)
    @Component
    class NoOpJobAlert : JobAlert {
        private val logger = LoggerFactory.getLogger(javaClass)

        override fun invoke(job: Job) {
            logger.info(job.message)
        }
    }
}
