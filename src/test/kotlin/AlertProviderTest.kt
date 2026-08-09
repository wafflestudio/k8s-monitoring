package com.wafflestudio.k8s

import com.wafflestudio.k8s.job.DiscordJobAlert
import com.wafflestudio.k8s.job.JobAlert
import com.wafflestudio.k8s.job.NoOpJobAlert
import com.wafflestudio.k8s.job.SlackJobAlert
import com.wafflestudio.k8s.node.DiscordNodeAddedAlert
import com.wafflestudio.k8s.node.DiscordNodeDeletedAlert
import com.wafflestudio.k8s.node.NoOpNodeAddedAlert
import com.wafflestudio.k8s.node.NoOpNodeDeletedAlert
import com.wafflestudio.k8s.node.NodeAddedAlert
import com.wafflestudio.k8s.node.NodeDeletedAlert
import com.wafflestudio.k8s.node.SlackNodeAddedAlert
import com.wafflestudio.k8s.node.SlackNodeDeletedAlert
import com.wafflestudio.k8s.pod.DiscordPodAlert
import com.wafflestudio.k8s.pod.NoOpPodAlert
import com.wafflestudio.k8s.pod.PodAlert
import com.wafflestudio.k8s.pod.SlackPodAlert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * MakePodAlert 등이 `getBean(PodAlert::class.java)` 로 구현체를 꺼내기 때문에,
 * 인터페이스당 빈이 **정확히 하나** 여야 한다. 둘 이상이면 알림을 보내는 순간
 * NoUniqueBeanDefinitionException 이 터져 watch 루프가 죽는다.
 *
 * alert.provider 값별로 그 불변식이 지켜지는지 검증한다.
 */
class AlertProviderTest {
    @Configuration
    @ComponentScan(
        basePackages = [
            "com.wafflestudio.k8s.discord",
            "com.wafflestudio.k8s.pod",
            "com.wafflestudio.k8s.job",
            "com.wafflestudio.k8s.node",
        ]
    )
    class ScanConfig

    // PropertyPlaceholderAutoConfiguration 이 있어야 @Value 의 `${...}` 가 실제 부트 앱과 동일하게
    // strict 해석된다. 없으면 미해결 placeholder 가 조용히 원문 그대로 주입된다.
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration::class.java))
        .withUserConfiguration(ScanConfig::class.java)

    @Test
    fun `provider 가 없으면 NoOp 구현체만 등록된다`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(PodAlert::class.java)
            assertThat(context).hasSingleBean(JobAlert::class.java)
            assertThat(context).hasSingleBean(NodeAddedAlert::class.java)
            assertThat(context).hasSingleBean(NodeDeletedAlert::class.java)

            assertThat(context.getBean(PodAlert::class.java)).isInstanceOf(NoOpPodAlert::class.java)
            assertThat(context.getBean(JobAlert::class.java)).isInstanceOf(NoOpJobAlert::class.java)
            assertThat(context.getBean(NodeAddedAlert::class.java)).isInstanceOf(NoOpNodeAddedAlert::class.java)
            assertThat(context.getBean(NodeDeletedAlert::class.java)).isInstanceOf(NoOpNodeDeletedAlert::class.java)
        }
    }

    @Test
    fun `provider 가 discord 면 Discord 구현체만 등록된다`() {
        runner
            .withPropertyValues(
                "alert.provider=discord",
                "discord.bot-token=test-token",
                "discord.default-channel-id=123456789",
            )
            .run { context ->
                assertThat(context).hasSingleBean(PodAlert::class.java)
                assertThat(context).hasSingleBean(JobAlert::class.java)
                assertThat(context).hasSingleBean(NodeAddedAlert::class.java)
                assertThat(context).hasSingleBean(NodeDeletedAlert::class.java)

                assertThat(context.getBean(PodAlert::class.java)).isInstanceOf(DiscordPodAlert::class.java)
                assertThat(context.getBean(JobAlert::class.java)).isInstanceOf(DiscordJobAlert::class.java)
                assertThat(context.getBean(NodeAddedAlert::class.java)).isInstanceOf(DiscordNodeAddedAlert::class.java)
                assertThat(context.getBean(NodeDeletedAlert::class.java)).isInstanceOf(DiscordNodeDeletedAlert::class.java)
            }
    }

    @Test
    fun `provider 가 slack 이면 Slack 구현체만 등록된다`() {
        runner
            .withPropertyValues(
                "alert.provider=slack",
                "slack.token=test-token",
            )
            .run { context ->
                assertThat(context).hasSingleBean(PodAlert::class.java)
                assertThat(context).hasSingleBean(JobAlert::class.java)
                assertThat(context).hasSingleBean(NodeAddedAlert::class.java)
                assertThat(context).hasSingleBean(NodeDeletedAlert::class.java)

                assertThat(context.getBean(PodAlert::class.java)).isInstanceOf(SlackPodAlert::class.java)
                assertThat(context.getBean(JobAlert::class.java)).isInstanceOf(SlackJobAlert::class.java)
                assertThat(context.getBean(NodeAddedAlert::class.java)).isInstanceOf(SlackNodeAddedAlert::class.java)
                assertThat(context.getBean(NodeDeletedAlert::class.java)).isInstanceOf(SlackNodeDeletedAlert::class.java)
            }
    }

    @Test
    fun `provider 가 discord 인데 채널 ID 가 없으면 기동에 실패한다`() {
        runner
            .withPropertyValues(
                "alert.provider=discord",
                "discord.bot-token=test-token",
            )
            .run { context -> assertThat(context).hasFailed() }
    }
}
