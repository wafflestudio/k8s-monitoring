package com.wafflestudio.k8s.node

import com.slack.api.Slack
import com.slack.api.methods.request.files.FilesUploadV2Request
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun interface NodeDeletedAlert {
    suspend fun invoke(node: Node): Boolean
}

@ConditionalOnProperty("slack.token", matchIfMissing = false)
@Component
class SlackNodeDeletedAlert(
    @Value("\${slack.token}") token: String,
) : NodeDeletedAlert {
    private val client = Slack.getInstance().methodsAsync(token)

    override suspend fun invoke(node: Node): Boolean {
        val channel = "k8s-알람"
        val fileName = "${node.name}.txt"

        return client.filesUploadV2 { req ->
            req.channel(channel)
                .uploadFiles(listOf(
                    FilesUploadV2Request.UploadFile.builder()
                        .content(node.deletedAlertMessage)
                        .filename(fileName)
                        .title(fileName)
                        .build()
                ))
                .initialComment("[Node Deleted]")
        }
            .await()
            .isOk
    }
}

@ConditionalOnMissingBean(SlackNodeDeletedAlert::class)
@Component
class NoOpNodeDeletedAlert : NodeDeletedAlert {
    override suspend fun invoke(node: Node): Boolean {
        println(node.deletedAlertMessage)
        return true
    }
}

val Node.deletedAlertMessage
    get() = buildString {
        appendLine("[Node Deleted]")
        appendLine("Name: $name")
        appendLine("NodeGroup: $nodeGroup")
        appendLine("DeletedAt: ${ZonedDateTime.now(ZoneOffset.ofHours(9))}")
    }
