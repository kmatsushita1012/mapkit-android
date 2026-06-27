plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.studiomk.mapkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api(project(":source:mapkit-android-compose"))
    api(project(":source:mapkit-android-webview"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("POM_GROUP_ID").get()
            artifactId = "mapkit"
            version = providers.gradleProperty("VERSION_NAME").get()

            pom {
                name.set("MapKit for Android")
                description.set(providers.gradleProperty("POM_DESCRIPTION").get())
                url.set(providers.gradleProperty("POM_URL").get())
                licenses {
                    license {
                        name.set(providers.gradleProperty("POM_LICENSE_NAME").get())
                        url.set(providers.gradleProperty("POM_LICENSE_URL").get())
                    }
                }
                developers {
                    developer {
                        id.set(providers.gradleProperty("POM_DEVELOPER_ID").get())
                        name.set(providers.gradleProperty("POM_DEVELOPER_NAME").get())
                    }
                }
                scm {
                    url.set(providers.gradleProperty("POM_SCM_URL").get())
                    connection.set(providers.gradleProperty("POM_SCM_CONNECTION").get())
                    developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION").get())
                }
            }
        }
    }

    repositories {
        val sonatypeUser = providers.environmentVariable("OSSRH_USERNAME").orNull
        val sonatypePass = providers.environmentVariable("OSSRH_PASSWORD").orNull
        if (!sonatypeUser.isNullOrBlank() && !sonatypePass.isNullOrBlank()) {
            maven {
                name = "sonatype"
                val isSnapshot = providers.gradleProperty("VERSION_NAME").get().endsWith("SNAPSHOT")
                url = uri(
                    if (isSnapshot) {
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    } else {
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                    }
                )
                credentials {
                    username = sonatypeUser
                    password = sonatypePass
                }
            }
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            named<MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}

signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    isRequired = !signingKey.isNullOrBlank()
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
