package com.wafflestudio.k8s.pod

import org.springframework.context.ApplicationContext

context(ApplicationContext)
class MakePodAlert(pod: Pod) {
    init {
        getBean(PodAlert::class.java).invoke(pod)
    }
}
