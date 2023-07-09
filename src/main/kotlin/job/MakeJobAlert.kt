package com.wafflestudio.k8s.job

import org.springframework.context.ApplicationContext

context(ApplicationContext)
class MakeJobAlert(job: Job) {
    init {
        getBean(JobAlert::class.java).invoke(job)
    }
}
