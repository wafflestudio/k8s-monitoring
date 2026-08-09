package com.wafflestudio.k8s

import com.wafflestudio.k8s.discord.DiscordAlertClient
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 실제 Discord 채널로 메시지를 보내는 통합 테스트. 프로젝트 루트의 `.env` 에
 * BOT_TOKEN / CHANNEL_ID 가 있을 때만 돈다.
 */
class DiscordAlertClientIT {
    private val env: Map<String, String> =
        File(".env").takeIf { it.exists() }
            ?.readLines()
            ?.filter { it.contains("=") && !it.trimStart().startsWith("#") }
            ?.associate { line ->
                val (k, v) = line.split("=", limit = 2)
                k.trim() to v.trim().trim('"', '\'')
            }
            ?: emptyMap()

    private fun client(): DiscordAlertClient {
        val token = env["BOT_TOKEN"]
        val channelId = env["CHANNEL_ID"]
        assumeTrue(!token.isNullOrBlank() && !channelId.isNullOrBlank(), ".env 에 BOT_TOKEN/CHANNEL_ID 없음 - 스킵")
        return DiscordAlertClient(token!!, channelId!!)
    }

    // 주의: @Test 메서드는 반환 타입이 Unit 이어야 JUnit 5 가 인식한다.
    // `fun x() = runBlocking { assertThat(..) }` 는 반환 타입이 Unit 이 아니라 조용히 스킵된다.

    @Test
    fun `Pod 알림을 코멘트 + txt 첨부로 보낸다`() {
        runBlocking {
            val sent = client().send(
                comment = "[통합테스트] Pod Failed\nNamespace: default\nPod: test-pod",
                fileName = "default-test-pod.txt",
                content = buildString {
                    appendLine("[Pod Failed]")
                    appendLine("Namespace: default")
                    appendLine("Name: test-pod")
                    appendLine("Phase: Pending")
                    appendLine("한글 메시지도 깨지지 않아야 한다 ✅")
                },
            )

            assertThat(sent).isTrue()
        }
    }

    @Test
    fun `파일명에 슬래시가 있어도 전송된다`() {
        runBlocking {
            val sent = client().send(
                comment = "[통합테스트] 파일명 정규화",
                fileName = "ns/pod name:weird.txt",
                content = "sanitize check",
            )

            assertThat(sent).isTrue()
        }
    }

    @Test
    fun `잘못된 토큰이면 예외를 삼키고 false 를 반환한다`() {
        runBlocking {
            val channelId = env["CHANNEL_ID"]
            assumeTrue(!channelId.isNullOrBlank(), ".env 에 CHANNEL_ID 없음 - 스킵")

            val sent = DiscordAlertClient("invalid-token", channelId!!)
                .send(comment = "보내지면 안 됨", fileName = "x.txt", content = "x")

            assertThat(sent).isFalse()
        }
    }
}
