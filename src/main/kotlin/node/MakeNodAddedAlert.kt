package com.wafflestudio.k8s.node

import org.springframework.context.ApplicationContext

object MakeNodAddedAlert {
    context(ApplicationContext)
    suspend operator fun invoke(node: Node) = getBean(NodeAddedAlert::class.java).invoke(node)
}
