plugins {
    id("java-library")
    id("xyz.jpenilla.run-velocity") version "3.0.2"
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    implementation("org.mongodb:mongodb-driver-sync:5.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    shadowJar {
        relocate("com.mongodb", "com.kobosh.libs.mongodb")
        relocate("org.bson", "com.kobosh.libs.bson")
        relocate("com.google.gson", "com.kobosh.libs.gson")
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    build {
        dependsOn(shadowJar)
    }

    runVelocity {
        velocityVersion("3.5.0-SNAPSHOT")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }
}
