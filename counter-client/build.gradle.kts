import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.io.FileInputStream
import java.util.Properties

plugins {
    // Apply the shared build logic from buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    `maven-publish`
    signing
}

val groupName = "games.august"
val artifactName = "counter-client"
val versionName = "0.0.10"
group = groupName
version = versionName

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.ktorClient)
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.kotlinTestJunit)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.googleTruth)
}

tasks.test {
    useJUnitPlatform()
}

java {
    withJavadocJar()
    withSourcesJar()
}

val privateProperties = Properties()
val privatePropertiesFile = rootProject.file("private.properties")
if (privatePropertiesFile.exists()) {
    privateProperties.load(FileInputStream(privatePropertiesFile))
} else {
    privateProperties.setProperty(
        "sonatypeUsername",
        System.getenv("SONATYPE_USERNAME") ?: "MISSING",
    )
    privateProperties.setProperty(
        "sonatypePassword",
        System.getenv("SONATYPE_PASSWORD") ?: "MISSING",
    )

    privateProperties.setProperty(
        "signingKeyId",
        System.getenv("SIGNING_KEY_ID") ?: "MISSING",
    )
    privateProperties.setProperty(
        "signingKeyPassword",
        System.getenv("SIGNING_KEY_PASSWORD") ?: "MISSING",
    )
    privateProperties.setProperty(
        "signingKeyLocation",
        System.getenv("SIGNING_KEY_LOCATION") ?: "MISSING",
    )
}

extraProperties["signing.keyId"] = privateProperties["signingKeyId"]
extraProperties["signing.password"] = privateProperties["signingKeyPassword"]
extraProperties["signing.secretKeyRingFile"] = privateProperties["signingKeyLocation"]

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = groupName
            artifactId = artifactName
            version = versionName

            pom {
                name.set(artifactName)
                description.set("A Kotlin library for integrating with the August Games Counter service")
                url.set("https://github.com/August-Games/counter-client-kt")

                licenses {
                    license {
                        name.set("counter-client-kt License")
                        url.set("https://github.com/August-Games/counter-client-kt/blob/main/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("dylan")
                        name.set("Dylan")
                        email.set("dylan@august.games")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/August-Games/counter-client-kt.git")
                    developerConnection.set("scm:git:ssh://github.com:August-Games/counter-client-kt.git")
                    url.set("https://github.com/August-Games/counter-client-kt")
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            url =
                uri(
                    if (versionName.endsWith("SNAPSHOT")) {
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    } else {
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                    },
                )
            credentials {
                username = privateProperties["sonatypeUsername"] as String? ?: System.getenv("SONATYPE_USERNAME")
                password = privateProperties["sonatypePassword"] as String? ?: System.getenv("SONATYPE_PASSWORD")
            }
        }
        mavenLocal()
    }

    signing {
        sign(publishing.publications)
    }
}
