plugins {
    `java-library`
}

sourceSets {
    main {
        java.srcDir("src")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20")
    testImplementation("junit:junit:4.13.2")
}
