package com.wafflestudio.k8s

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

// k8s API SSL certificate problem: unable to get local issuer certificate
fun WebClient.Builder.insecure(): WebClient.Builder {
    val noVerificationSslContext = SslContextBuilder
        .forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()

    val noVerificationSslHttpClient = HttpClient
        .create()
        .secure { it.sslContext(noVerificationSslContext) }

    return clientConnector(ReactorClientHttpConnector(noVerificationSslHttpClient))
}
