/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.contacts;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalContactsConsumersTest {
    @TempDir Path projectDir;

    static Stream<String> versions() {
        return Stream.of(SupportedGradleVersion.MIN.version, GradleVersion.current().getVersion()).distinct();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void publishingAndInfoOwnership(String version) throws IOException {
        write("settings.gradle", "rootProject.name = 'external-contacts'\ninclude 'app'\n");
        write("build.gradle", """
                buildscript { dependencies { classpath files(%s) } }
                apply plugin: 'com.netflix.nebula.contacts'
                contacts {
                    contact('owner@example.com') {
                        moniker 'Shared owner'; github 'owner-github'; role 'owner'
                    }
                }
                """.formatted(pluginClasspath()));
        var script = """
                buildscript {
                    repositories { mavenCentral() }
                    dependencies {
                        classpath files(%s)
                        classpath('com.netflix.nebula:nebula-publishing-plugin:23.2.0') {
                            exclude group: 'com.netflix.nebula', module: 'gradle-contacts-plugin'
                        }
                        classpath('com.netflix.nebula:gradle-info-plugin:16.3.0') {
                            exclude group: 'com.netflix.nebula', module: 'gradle-contacts-plugin'
                        }
                    }
                }
                apply plugin: 'com.netflix.nebula.contacts'
                apply plugin: 'java'
                apply plugin: 'com.netflix.nebula.maven-developer'
                apply plugin: 'com.netflix.nebula.info-broker'
                apply plugin: 'com.netflix.nebula.info-owners'
                apply plugin: 'com.netflix.nebula.info-jar'
                contacts {
                    inheritFromParent.set(false)
                    contact('notify@example.com') { role 'notify' }
                    contact('everyone@example.com')
                    if (!inheritFromParent.get()) {
                        contact('owner@example.com') {
                            moniker 'Shared owner'; github 'owner-github'; role 'owner'
                        }
                    }
                }
                publishing.publications.create('contacts', MavenPublication) {
                    groupId = 'example'; artifactId = 'contacts'; version = '1.0'
                }
                tasks.named('jar') { archiveFileName.set('contacts.jar') }
                """.formatted(pluginClasspath());
        // Compare the real integrations against their ordinary legacy inherited output first.
        write("app/build.gradle", script.replace("inheritFromParent.set(false)", "inheritFromParent.set(true)"));
        cached(version, false, false);
        assertOwnership();
        var legacyPom = Files.readString(projectDir.resolve("app/build/publications/contacts/pom-default.xml"));
        cached(version, false, true);
        write("app/build.gradle", script);
        for (var isolated : List.of(false, true)) {
            cached(version, isolated, false);
            assertOwnership();
            assertThat(Files.readString(projectDir.resolve("app/build/publications/contacts/pom-default.xml"))).isEqualTo(legacyPom);
            cached(version, isolated, true);
            Files.delete(projectDir.resolve("app/build/libs/contacts.jar"));
            Files.delete(projectDir.resolve("app/build/publications/contacts/pom-default.xml"));
            cached(version, isolated, true);
            assertOwnership();
            assertThat(Files.readString(projectDir.resolve("app/build/publications/contacts/pom-default.xml"))).isEqualTo(legacyPom);
        }
    }

    private void cached(String version, boolean isolated, boolean reused) {
        var result = run(version, List.of(":app:jar", ":app:generatePomFileForContactsPublication"),
                "--configuration-cache", "--configuration-cache-problems=fail",
                "-Dorg.gradle.unsafe.isolated-projects=" + isolated).build();
        assertThat(result.getOutput()).contains(reused ? "Configuration cache entry reused." : "Configuration cache entry stored.");
    }

    private void assertOwnership() throws IOException {
        assertThat(Files.readString(projectDir.resolve("app/build/publications/contacts/pom-default.xml")))
                .contains("<email>owner@example.com</email>", "<name>Shared owner</name>", "<id>owner-github</id>",
                        "<role>owner</role>", "<email>notify@example.com</email>", "<email>everyone@example.com</email>");
        try (var jar = new JarFile(projectDir.resolve("app/build/libs/contacts.jar").toFile())) {
            var attributes = jar.getManifest().getMainAttributes();
            assertThat(attributes.getValue("Module-Owner")).isEqualTo("everyone@example.com,owner@example.com");
            assertThat(attributes.getValue("Module-Email")).isEqualTo("notify@example.com,everyone@example.com");
        }
    }

    private GradleRunner run(String version, List<String> tasks, String... flags) {
        var args = new ArrayList<>(tasks);
        args.addAll(List.of(flags));
        args.addAll(List.of("--console=plain", "--stacktrace", "--no-watch-fs"));
        return GradleRunner.create().withProjectDir(projectDir.toFile())
                .withGradleVersion(version).withArguments(args);
    }

    private String pluginClasspath() throws IOException {
        var metadata = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream("plugin-under-test-metadata.properties")) {
            metadata.load(stream);
        }
        return Arrays.stream(metadata.getProperty("implementation-classpath").split(File.pathSeparator))
                .map(path -> "'" + path.replace("\\", "\\\\").replace("'", "\\'") + "'")
                .reduce((left, right) -> left + "," + right).orElseThrow();
    }

    private void write(String path, String contents) throws IOException {
        var file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
