package com.wafflestudio.k8s.pod

import org.springframework.context.ApplicationContext

object MakePodAlert {
    context(ApplicationContext)
    suspend operator fun invoke(pod: Pod) = getBean(PodAlert::class.java).invoke(pod)
}
