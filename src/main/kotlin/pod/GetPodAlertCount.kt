package com.wafflestudio.k8s.pod

import com.fasterxml.jackson.databind.JsonNode
import com.wafflestudio.k8s.K8sContext
import kotlinx.coroutines.CoroutineScope
import org.springframework.web.reactive.function.client.awaitBody

object GetPodAlertCount {
    context(CoroutineScope, K8sContext)
    suspend operator fun invoke(pod: Pod): Int =
        client.get()
            .uri("/api/v1/namespaces/${pod.namespace}/pods/${pod.name}")
            .retrieve()
            .awaitBody<JsonNode>()
            .get("metadata")?.get("annotations")?.get("alertCount")?.asInt() ?: 0
}
