plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    kotlinOptions.jvmTarget = "17"
}
tasks.withType(JavaCompile::class.java) {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

dependencies {
    implementation("com.guardsquare:proguard-gradle:7.3.2")
}