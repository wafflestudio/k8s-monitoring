package com.wafflestudio.k8s.node

import com.fasterxml.jackson.databind.JsonNode
import com.wafflestudio.k8s.K8sContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.bodyToFlow

object OnNodeChanged {
    private val logger = LoggerFactory.getLogger(javaClass)

    context(CoroutineScope, K8sContext)
    operator fun invoke(block: suspend (changedNode: Node) -> Unit) {
        launch {
            while (true) {
                runCatching {
                    client.get().uri("/api/v1/nodes?watch=1").retrieve().bodyToFlow<JsonNode>()
                }.getOrElse {
                    logger.error("fetch failed job error", it)
                    emptyFlow()
                }
                    .mapNotNull(Node::fromOrNull)
                    .collect { block.invoke(it) }

                delay(50)
            }
        }
    }
}
