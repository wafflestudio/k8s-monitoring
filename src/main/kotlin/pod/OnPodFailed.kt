package com.wafflestudio.k8s.pod

import com.fasterxml.jackson.databind.JsonNode
import com.wafflestudio.k8s.K8sContext
import com.wafflestudio.k8s.pod.Pod.ContainerStatus
import com.wafflestudio.k8s.pod.Pod.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.bodyToFlow

object OnPodFailed {
    private val logger = LoggerFactory.getLogger(javaClass)

    context(CoroutineScope, K8sContext)
    operator fun invoke(block: suspend (failedPod: Pod) -> Unit) {
        fun isFailedPod(pod: Pod): Boolean {
            return pod.type == Type.MODIFIED && pod.containerStatuses.any { it is ContainerStatus.Waiting && it.failed }
        }

        launch {
            while (true) {
                runCatching {
                    client.get().uri("/api/v1/pods?watch=1").retrieve().bodyToFlow<JsonNode>()
                }.getOrElse {
                    logger.error("fetch failed pod error", it)
                    emptyFlow()
                }
                    .filter { it.get("type")?.asText() == "MODIFIED" }
                    .mapNotNull(Pod::fromOrNull)
                    .filter(::isFailedPod)
                    .collect { block.invoke(it) }

                delay(50)
            }
        }
    }
}
