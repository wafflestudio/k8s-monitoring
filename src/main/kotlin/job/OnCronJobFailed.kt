package com.wafflestudio.k8s.job

import com.fasterxml.jackson.databind.JsonNode
import com.wafflestudio.k8s.K8sContext
import com.wafflestudio.k8s.job.Job.Status
import com.wafflestudio.k8s.job.Job.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.bodyToFlow

object OnCronJobFailed {
    private val logger = LoggerFactory.getLogger(javaClass)

    context(CoroutineScope, K8sContext)
    operator fun invoke(block: suspend (failedCronJob: Job) -> Unit) {
        fun isFailedCronJob(job: Job): Boolean {
            return job.cronJobName != null && job.type == Type.MODIFIED && job.status == Status.FAILED
        }

        launch {
            while (true) {
                runCatching {
                    client.get().uri("/apis/batch/v1/jobs?watch=1").retrieve().bodyToFlow<JsonNode>()
                }.getOrElse {
                    logger.error("fetch failed job error", it)
                    emptyFlow()
                }
                    .mapNotNull(Job::fromOrNull)
                    .filter(::isFailedCronJob)
                    .collect { block.invoke(it) }

                delay(50)
            }
        }
    }
}
