import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.1.1"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.spring") version "1.8.22"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

    implementation("com.slack.api:slack-api-client:1.28.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xcontext-receivers")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

task("dockerBuild") {
    dependsOn("bootJar")

    doLast {
        val dir = project.mkdir(File(project.buildDir, "tmp"))
        val dockerFile = File(dir, "Dockerfile")

        dockerFile.writeText(project.file("Dockerfile").inputStream().reader().readText())

        project.copy {
            from(project.tasks.getByName<Jar>("bootJar").archiveFile) { rename { "app.jar" } }
            into(dir)
        }

        project.exec {
            workingDir(dir)
            commandLine("aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 405906814034.dkr.ecr.ap-northeast-2.amazonaws.com")
            commandLine(
                "docker",
                "build",
                "-t",
                "405906814034.dkr.ecr.ap-northeast-2.amazonaws.com/k8s-monitoring:${project.version}",
                "."
            )
        }
    }
}

task("dockerPush") {
    dependsOn("dockerBuild")

    doLast {
        project.exec {
            commandLine(
                "docker",
                "push",
                "405906814034.dkr.ecr.ap-northeast-2.amazonaws.com/k8s-monitoring:${project.version}"
            )
        }
    }
}

val ocirRegistry = "yny.ocir.io"
val ocirNamespace = "ax1dvc8vmenm"
val ocirUsername = "$ocirNamespace/members/snutt-deployer"
val ocirRepository = "k8s-monitoring"
val ocirImageTag = "$ocirRegistry/$ocirNamespace/$ocirRepository:${project.version}"

task("ocirBuild") {
    dependsOn("bootJar")

    doLast {
        val authToken = System.getenv("OCIR_AUTH_TOKEN") ?: error("OCIR_AUTH_TOKEN env variable is required")

        val dir = project.mkdir(File(project.buildDir, "tmp"))
        val dockerFile = File(dir, "Dockerfile")

        dockerFile.writeText(project.file("Dockerfile").inputStream().reader().readText())

        project.copy {
            from(project.tasks.getByName<Jar>("bootJar").archiveFile) { rename { "app.jar" } }
            into(dir)
        }

        project.exec {
            commandLine("docker", "login", ocirRegistry, "-u", ocirUsername, "-p", authToken)
        }

        project.exec {
            workingDir(dir)
            commandLine(
                "docker", "build",
                "--platform", "linux/arm64",
                "--provenance=false",
                "-t", ocirImageTag,
                "."
            )
        }
    }
}

task("ocirPush") {
    dependsOn("ocirBuild")

    doLast {
        project.exec {
            commandLine("docker", "push", ocirImageTag)
        }
    }
}
