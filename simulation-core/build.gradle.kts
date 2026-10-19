plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":scenario-format"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
