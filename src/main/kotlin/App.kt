package com.wafflestudio.k8s

import com.wafflestudio.k8s.job.MakeJobAlert
import com.wafflestudio.k8s.job.OnCronJobFailed
import com.wafflestudio.k8s.pod.MakePodAlert
import com.wafflestudio.k8s.pod.OnPodFailed
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
                MakeJobAlert(job)
            }

            OnPodFailed { pod ->
                MakePodAlert(pod)
            }
        }
    }
}
