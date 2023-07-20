package com.wafflestudio.k8s

import com.wafflestudio.k8s.job.MakeJobAlert
import com.wafflestudio.k8s.job.OnCronJobFailed
import com.wafflestudio.k8s.node.MakeNodAddedAlert
import com.wafflestudio.k8s.node.MakeNodeDeletedAlert
import com.wafflestudio.k8s.node.OnNodeChanged
import com.wafflestudio.k8s.pod.GetPodAlertCount
import com.wafflestudio.k8s.pod.MakePodAlert
import com.wafflestudio.k8s.pod.OnPodFailed
import com.wafflestudio.k8s.pod.WritePodAlertCount
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
            OnCronJobFailed { job -> MakeJobAlert(job) }

            OnPodFailed { pod ->
                val alertCount = GetPodAlertCount(pod)

                if (alertCount < 3 && MakePodAlert(pod)) {
                    WritePodAlertCount(
                        pod = pod,
                        alertCount = alertCount + 1
                    )
                }
            }

            OnNodeChanged { node ->
                when {
                    node.isAdded -> MakeNodAddedAlert(node)
                    node.isDeleted -> MakeNodeDeletedAlert(node)
                }
            }
        }
    }
}
