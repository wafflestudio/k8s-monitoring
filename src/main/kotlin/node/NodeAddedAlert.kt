package com.wafflestudio.k8s.node

import com.slack.api.Slack
import com.slack.api.methods.request.files.FilesUploadV2Request
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface NodeAddedAlert {
    suspend fun invoke(node: Node): Boolean
}

@ConditionalOnProperty("slack.token", matchIfMissing = false)
@Component
class SlackNodeAddedAlert(
    @Value("\${slack.token}") token: String,
    @Value("\${slack.default-channel-id:C05FSP4MEVC}") private val defaultChannelId: String,
) : NodeAddedAlert {
    private val client = Slack.getInstance().methodsAsync(token)
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun invoke(node: Node): Boolean {
        val channel = defaultChannelId
        val fileName = "${node.name}.txt"

        return runCatching {
            client.filesUploadV2 { req ->
                req.channel(channel)
                    .uploadFiles(listOf(
                        FilesUploadV2Request.UploadFile.builder()
                            .content(node.addedAlertMessage)
                            .filename(fileName)
                            .title(fileName)
                            .build()
                    ))
                    .initialComment("[Node Added]")
            }
                .await()
                .isOk
        }.getOrElse {
            log.error("Failed to upload node-added alert to Slack (channel: {}, node: {})", channel, node.name, it)
            false
        }
    }
}

@ConditionalOnMissingBean(SlackNodeAddedAlert::class)
@Component
class NoOpNodeAddedAlert : NodeAddedAlert {
    override suspend fun invoke(node: Node): Boolean {
        println(node.addedAlertMessage)
        return true
    }
}

val Node.addedAlertMessage
    get() = buildString {
        appendLine("[Node Added]")
        appendLine("Name: $name")
        appendLine("NodeGroup: $nodeGroup")
        appendLine("AddedAt: ${ZonedDateTime.now(ZoneOffset.ofHours(9))}")
    }
