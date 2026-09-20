plugins { application }
repositories { mavenCentral() }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
application { mainClass.set("com.swyp.Server") }
dependencies {
    implementation("com.google.firebase:firebase-admin:9.4.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
