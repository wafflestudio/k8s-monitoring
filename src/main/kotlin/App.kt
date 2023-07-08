package com.wafflestudio.k8s

import com.wafflestudio.k8s.job.MakeAlert
import com.wafflestudio.k8s.job.OnCronJobFailed
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class App

fun main(): Unit = runBlocking {
    val app = runApplication<App>()

    with(app) {
        val k8sContext = K8sContext.get() ?: return@with

        with(k8sContext) {
            OnCronJobFailed { job ->
                MakeAlert(job)
            }
        }
    }
}
