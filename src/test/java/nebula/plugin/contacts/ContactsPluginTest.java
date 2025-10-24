package nebula.plugin.contacts;

import nebula.test.dsl.BuildscriptLanguage;
import nebula.test.dsl.TestProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;

import static nebula.test.dsl.TestKitAssertions.assertThat;

class ContactsPluginTest {
    @TempDir
    File projectDir;

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion.class)
    void test_kotlin(SupportedGradleVersion gradleVersion) {
        final TestProjectBuilder builder = TestProjectBuilder.testProject(projectDir);
        builder.rootProject().plugins().id("com.netflix.nebula.contacts");
        builder.rootProject()
                .rawBuildScript(
                        //language=kotlin
                        """
                                contacts {
                                    addPerson("example@example.com") {
                                        moniker = "Nebula-Plugins maintainers"
                                        github = "nebula-plugins"
                                        role("owner")
                                    }
                                }
                                """);
        final var runner = builder.build(BuildscriptLanguage.KOTLIN);


        BuildResult result = runner.run(GradleRunner.create().withGradleVersion(gradleVersion.version), "build");
        assertThat(result)
                .hasNoDeprecationWarnings()
                .hasNoMutableStateWarnings();
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion.class)
    void test_groovy(SupportedGradleVersion gradleVersion) {
        final TestProjectBuilder builder = TestProjectBuilder.testProject(projectDir);
        builder.rootProject().plugins().id("com.netflix.nebula.contacts");
        builder.rootProject()
                .rawBuildScript(
                        //language=groovy
                        """
                                contacts {
                                    "example@example.com" {
                                        moniker = "Nebula-Plugins maintainers"
                                        github = "nebula-plugins"
                                        role("owner")
                                    }
                                }
                                """);
        final var runner = builder.build(BuildscriptLanguage.GROOVY);


        BuildResult result = runner.run(GradleRunner.create().withGradleVersion(gradleVersion.version), "build");
        assertThat(result)
                .hasNoDeprecationWarnings()
                .hasNoMutableStateWarnings();
    }
}
