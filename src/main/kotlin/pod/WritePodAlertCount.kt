package com.wafflestudio.k8s.pod

import com.wafflestudio.k8s.K8sContext
import kotlinx.coroutines.CoroutineScope
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.awaitExchange

object WritePodAlertCount {
    context(CoroutineScope, K8sContext)
    suspend operator fun invoke(pod: Pod, alertCount: Int): Boolean =
        client.patch()
            .uri("/api/v1/namespaces/${pod.namespace}/pods/${pod.name}")
            .contentType(MediaType.valueOf("application/strategic-merge-patch+json"))
            .bodyValue(
                """
                    { "metadata": { "annotations": { "alertCount": "$alertCount" } } }
                """.trimIndent()
            )
            .awaitExchange { it.statusCode().is2xxSuccessful }
}
