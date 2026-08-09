package com.wafflestudio.k8s.discord

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity

/**
 * Discord 봇으로 단일 채널에 알림을 보내는 공용 전송 지점.
 *
 * Slack 구현이 `files.uploadV2` 로 "코멘트 + .txt 첨부" 를 올리던 것과 동작을 맞추기 위해
 * `multipart/form-data` 로 `payload_json` (코멘트) + `files[0]` (본문) 두 파트를 함께 보낸다.
 *
 * 전송 실패는 여기서 완전히 삼키고 `false` 를 반환한다. 워처 루프가 이 예외로 죽으면 안 되고,
 * Pod 알림의 경우 `false` 가 곧 "카운터를 올리지 말고 다음 이벤트에 재시도" 를 뜻하기 때문이다.
 */
@ConditionalOnProperty(name = ["alert.provider"], havingValue = "discord")
@Component
class DiscordAlertClient(
    @Value("\${discord.bot-token}") token: String,
    @Value("\${discord.default-channel-id}") private val channelId: String,
) {
    private val client = WebClient.builder()
        .baseUrl(DISCORD_API_BASE_URL)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bot $token")
        .build()

    private val objectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun send(comment: String, fileName: String, content: String): Boolean {
        val safeFileName = fileName.replace(UNSAFE_FILE_NAME_CHARS, "_")

        val payloadJson = objectMapper.writeValueAsString(
            mapOf(
                "content" to comment.take(MAX_CONTENT_LENGTH),
                "attachments" to listOf(mapOf("id" to 0, "filename" to safeFileName)),
            )
        )

        val body = MultipartBodyBuilder().apply {
            part("payload_json", payloadJson).contentType(MediaType.APPLICATION_JSON)
            part("files[0]", NamedByteArrayResource(content.toByteArray(Charsets.UTF_8), safeFileName))
                .contentType(TEXT_PLAIN_UTF_8)
        }.build()

        return runCatching {
            client.post()
                .uri("/channels/{channelId}/messages", channelId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .awaitBodilessEntity()

            true
        }.getOrElse {
            log.error("Failed to upload alert to Discord (channel: {}, file: {})", channelId, safeFileName, it)
            false
        }
    }

    /** 멀티파트 파트에 파일명을 붙이려면 [ByteArrayResource] 가 filename 을 돌려줘야 한다. */
    private class NamedByteArrayResource(
        bytes: ByteArray,
        private val fileName: String,
    ) : ByteArrayResource(bytes) {
        override fun getFilename(): String = fileName
    }

    companion object {
        private const val DISCORD_API_BASE_URL = "https://discord.com/api/v10"

        /** Discord 메시지 본문(content) 상한. 본문 전체는 첨부파일로 가므로 코멘트만 잘린다. */
        private const val MAX_CONTENT_LENGTH = 2000

        private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
        private val TEXT_PLAIN_UTF_8 = MediaType("text", "plain", Charsets.UTF_8)
    }
}
