package com.wafflestudio.k8s

import org.springframework.boot.cloud.CloudPlatform
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.web.reactive.function.client.WebClient
import java.io.File

data class K8sContext(
    val client: WebClient,
) {

    companion object {
        private const val KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST"
        private const val KUBERNETES_SERVICE_PORT = "KUBERNETES_SERVICE_PORT"
        private const val KUBERNETES_API_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token"

        context(ApplicationContext)
        fun get(): K8sContext? {
            val environment = getBean(Environment::class.java)

            if (CloudPlatform.getActive(environment) != CloudPlatform.KUBERNETES) return null
            val k8sApiHost = environment.getProperty(KUBERNETES_SERVICE_HOST) ?: return null
            val k8sApiPort = environment.getProperty(KUBERNETES_SERVICE_PORT) ?: return null
            val k8sApiToken = File(KUBERNETES_API_TOKEN_PATH).takeIf { it.exists() }?.readText() ?: return null

            return K8sContext(
                client = WebClient.builder()
                    .insecure()
                    .baseUrl("https://$k8sApiHost:$k8sApiPort")
                    .defaultHeader("Authorization", "Bearer $k8sApiToken")
                    .build()
            )
        }
    }
}
