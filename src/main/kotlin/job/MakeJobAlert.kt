package com.wafflestudio.k8s.job

import org.springframework.context.ApplicationContext

object MakeJobAlert {
    context(ApplicationContext)
    suspend operator fun invoke(job: Job) = getBean(JobAlert::class.java).invoke(job)
}
