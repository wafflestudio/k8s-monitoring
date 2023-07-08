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
