buildscript {
    // Minimum safe versions of libraries the Android Gradle Plugin and its Lint tool pull in;
    // AGP's own picks carry published advisories (Dependabot alerts). Build-time only: nothing
    // here is packaged into the app. Constraints only ever raise a version, so drop an entry once
    // AGP itself depends on the fixed version or newer. The plugins below resolve onto this
    // classpath, and the Lint tool's configuration reuses the same list (see the end of the file).
    dependencies {
        constraints {
            // GHSA-xxph-c9ww-hj94 (high): unbounded allocation deserializing compact collections.
            classpath("com.google.guava:guava:33.7.2-jre")
            // GHSA-j288-q9x7-2f5v (moderate): uncontrolled recursion on long inputs.
            classpath("org.apache.commons:commons-lang3:3.18.0")
            // GHSA-7r82-7xv7-xcpj (moderate): request URI handling.
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
            // GHSA-3677-xxcr-wjqv (high): DoS via compressed JWE content.
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            // GHSA-9pwp-9qqc-pr26 (critical), GHSA-qp49-qgx5-5m26 (high), GHSA-wg6q-6289-32hp
            // (moderate). The Bouncy Castle modules move together to stay binary-compatible.
            classpath("org.bouncycastle:bcprov-jdk18on:1.85")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.85")
            classpath("org.bouncycastle:bcutil-jdk18on:1.85")
            // GHSA-2363-cqg2-863c (high): XML external entity injection.
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// AGP runs Lint from a separate configuration in each Android module; give it the same floor.
val buildToolingFloor = buildscript.configurations.getByName("classpath").dependencyConstraints
    .map { "${it.group}:${it.name}:${it.version}" }
subprojects {
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        val lintTool = name
        buildToolingFloor.forEach { project.dependencies.constraints.add(lintTool, it) }
    }
}
