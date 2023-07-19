package com.wafflestudio.k8s.node

import org.springframework.context.ApplicationContext

object MakeNodeDeletedAlert {
    context(ApplicationContext)
    suspend operator fun invoke(node: Node) = getBean(NodeDeletedAlert::class.java).invoke(node)
}
