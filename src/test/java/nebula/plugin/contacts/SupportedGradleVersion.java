package nebula.plugin.contacts;

import org.gradle.api.JavaVersion;
import org.gradle.util.GradleVersion;

enum SupportedGradleVersion {
    // Gradle 9.0 cannot run on Java 25; keep that baseline on the Java 17/21 jobs.
    MIN(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25) ? "9.1.0" : "9.0.0"),
    MAX(GradleVersion.current().getVersion());
    public final String version;

    SupportedGradleVersion(String version) {
        this.version = version;
    }
}
