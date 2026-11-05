/*
 * Copyright 2026 Netflix, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package nebula.plugin.contacts;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalContactsIsolationTest {
    @TempDir Path projectDir;

    static Stream<String> versions() {
        return Stream.of(SupportedGradleVersion.MIN.version, GradleVersion.current().getVersion()).distinct();
    }

    static Stream<Arguments> layouts() {
        return versions().flatMap(v -> Stream.of("single", "siblings", "nested", "root-only", "empty", "gap")
                .map(layout -> Arguments.of(v, layout)));
    }

    private static final String SHARED = """
            email=shared@example.com
            name=Shared owner
            github=shared-github
            twitter=shared-twitter
            slack=shared-slack
            roles=owner,notify
            """;

    // Complete ownership is declared locally. There is no ancestor selection or roll-up API.
    private static final String DECLARATIONS = """
            def data = new Properties()
            data.load(new StringReader(providers.fileContents(layout.settingsDirectory.file('owners.properties')).asText.get()))
            def localName = providers.fileContents(layout.projectDirectory.file('local.txt')).asText.get()
            def inherit = providers.gradleProperty('contacts.inheritFromParent').map { it.toBoolean() }.orElse(false)
            contacts {
                inheritFromParent.convention(inherit)
                contact(data.getProperty('email')) {
                    moniker data.getProperty('name')
                    github data.getProperty('github')
                    twitter data.getProperty('twitter')
                    slack data.getProperty('slack')
                    roles(data.getProperty('roles').split(','))
                }
                contact('everyone@example.com')
                contact('module@example.com') { moniker localName; role 'writer' }
            }
            """;

    private static final String REPORT = """
            abstract class ContactsReport extends DefaultTask {
                @Input abstract ListProperty<String> getRows()
                @OutputFile abstract RegularFileProperty getDestination()
                @TaskAction void report() { destination.get().asFile.text = rows.get().join('\\n') + '\\n' }
            }
            tasks.register('unused') { throw new GradleException('unused task was realized') }
            def plugin = plugins.getPlugin('com.netflix.nebula.contacts-base')
            def snapshot = plugin.allContacts.collect { c ->
                [c.email, c.moniker ?: '', c.github, c.twitter, c.slack, c.roles.sort().join(',')].join('|')
            }
            tasks.register('reportContacts', ContactsReport) {
                rows.set(snapshot)
                destination.set(layout.buildDirectory.file('contacts.txt'))
            }
            """;

    @ParameterizedTest(name = "complete local ownership: {0} {1}")
    @MethodSource("layouts")
    void localOwnershipLayouts(String version, String topology) throws IOException {
        var projects = switch (topology) {
            case "single", "root-only" -> List.of("");
            case "siblings" -> List.of("", "app", "sibling");
            case "nested", "empty" -> List.of("", "team", "team/leaf");
            case "gap" -> List.of("", "gap/leaf");
            default -> throw new IllegalArgumentException(topology);
        };
        var includes = topology.equals("root-only") ? "include 'app'\n" : projects.stream().filter(p -> !p.isEmpty())
                .map(p -> "include '" + p.replace('/', ':') + "'\n").reduce("", String::concat);
        write("settings.gradle", "rootProject.name = 'local-contacts'\n" + includes);
        write("owners.properties", SHARED);
        if (topology.equals("root-only")) write("app/build.gradle", "");
        if (topology.equals("gap")) write("gap/build.gradle", "");
        for (var project : projects) {
            var prefix = project.isEmpty() ? "" : project + "/";
            write(prefix + "local.txt", "Module");
            write(prefix + "build.gradle", "plugins { id 'com.netflix.nebula.contacts' }\n" +
                    (topology.equals("empty") && project.equals("team")
                            ? "contacts { inheritFromParent.set(false) }\n" : DECLARATIONS) + REPORT);
        }
        stored(run(version, false, true, projects));
        var expected = outputs(projects);
        stored(run(version, true, true, projects));
        assertThat(outputs(projects)).isEqualTo(expected);
        reused(run(version, true, true, projects));
        deleteOutputs(projects);
        reused(run(version, true, true, projects));
        assertThat(outputs(projects)).isEqualTo(expected);
        for (var project : projects) {
            assertThat(expected.get(project)).isEqualTo(topology.equals("empty") && project.equals("team") ? "\n" :
                    "shared@example.com|Shared owner|shared-github|shared-twitter|shared-slack|notify,owner\n" +
                    "everyone@example.com|||||\nmodule@example.com|Module||||writer\n");
        }
    }

    @ParameterizedTest(name = "real convention plugin, Groovy/Kotlin siblings and nested consumers: {0}")
    @MethodSource("versions")
    void conventionPluginSuppliesIndependentLocalOwnership(String version) throws IOException {
        write("settings.gradle", "rootProject.name = 'convention-contacts'\ninclude 'app', 'sibling', 'team:leaf', 'gap:leaf'\n");
        write("buildSrc/settings.gradle", "rootProject.name = 'ownership-build-logic'\n");
        write("buildSrc/build.gradle", "plugins { id 'groovy-gradle-plugin' }\ndependencies { implementation files(" + pluginClasspath() + ") }\n");
        write("buildSrc/src/main/groovy/ownership-conventions.gradle",
                "plugins { id 'com.netflix.nebula.contacts' }\n" + DECLARATIONS);
        write("gradle.properties", "contacts.inheritFromParent=false\n");
        write("owners.properties", SHARED);
        write("gap/build.gradle", "// No plugin in this intermediate project.\n");
        var projects = List.of("", "app", "sibling", "team", "team/leaf", "gap/leaf");
        for (var project : projects) {
            var prefix = project.isEmpty() ? "" : project + "/";
            write(prefix + "local.txt", "Module");
            write(prefix + "build.gradle", "plugins { id 'ownership-conventions' }\n" + REPORT);
        }
        // Exercise Kotlin adoption of the same convention plugin, not a separate ownership definition.
        Files.move(projectDir.resolve("sibling/build.gradle"), projectDir.resolve("sibling/groovy-example.txt"));
        write("sibling/build.gradle.kts", """
                import nebula.plugin.contacts.BaseContactsPlugin
                plugins { id("ownership-conventions") }
                abstract class ContactsReport : DefaultTask() {
                    @get:Input abstract val rows: ListProperty<String>
                    @get:OutputFile abstract val destination: RegularFileProperty
                    @TaskAction fun report() { destination.get().asFile.writeText(rows.get().joinToString("\\n") + "\\n") }
                }
                val snapshot = plugins.getPlugin(BaseContactsPlugin::class.java).allContacts.map {
                    listOf(it.email, it.moniker ?: "", it.github, it.twitter, it.slack, it.roles.sorted().joinToString(",")).joinToString("|")
                }
                tasks.register<ContactsReport>("reportContacts") {
                    rows.set(snapshot)
                    destination.set(layout.buildDirectory.file("contacts.txt"))
                }
                """);
        stored(run(version, false, false, projects));
        var ordinary = outputs(projects);
        stored(run(version, true, false, projects));
        assertThat(outputs(projects)).isEqualTo(ordinary);
        reused(run(version, true, false, projects));
        deleteOutputs(projects);
        reused(run(version, true, false, projects));
        assertThat(outputs(projects)).isEqualTo(ordinary);
        var shared = SHARED;
        for (var change : List.of(Map.entry("Shared owner", "Changed shared owner"),
                Map.entry("roles=owner,notify", "roles=notify,reviewer"), Map.entry("shared@example.com", "renamed@example.com"))) {
            var before = outputs(projects);
            shared = shared.replace(change.getKey(), change.getValue());
            write("owners.properties", shared);
            stored(run(version, true, false, projects));
            for (var project : projects) assertThat(outputs(projects).get(project)).isNotEqualTo(before.get(project));
            reused(run(version, true, false, projects));
        }
        var sibling = outputs(projects).get("sibling");
        write("app/local.txt", "Changed module");
        stored(run(version, true, false, projects));
        assertThat(outputs(projects).get("app")).contains("module@example.com|Changed module|");
        assertThat(outputs(projects).get("sibling")).isEqualTo(sibling);
        reused(run(version, true, false, projects));
        // A tracked provider input changes resolution and invalidates the same configured task graph.
        write("gradle.properties", "contacts.inheritFromParent=true\n");
        var providerFailure = runner(version, true, false, reportTasks(projects)).buildAndFail();
        assertThat(providerFailure.getOutput()).contains("cannot access 'Project.extensions' functionality on another project")
                .doesNotContain("Reusing configuration cache.");
        // An explicit project-local value also overrides the convention's provider value.
        write("gradle.properties", "contacts.inheritFromParent=false\n");
        write("app/build.gradle", "plugins { id 'ownership-conventions' }\ncontacts { inheritFromParent.set(true) }\n" + REPORT);
        var failed = runner(version, true, false, reportTasks(projects)).buildAndFail();
        assertThat(failed.getOutput()).contains("cannot access 'Project.extensions' functionality on another project")
                .doesNotContain("Reusing configuration cache.");
    }

    @ParameterizedTest(name = "Kotlin flag validation and cached instance versus fresh static access: {0}")
    @MethodSource("versions")
    void kotlinFlagAndAccessorSnapshots(String version) throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"kotlin-local\"\ninclude(\"app\")\n");
        write("build.gradle.kts", "");
        write("app/build.gradle.kts", """
                import nebula.plugin.contacts.BaseContactsPlugin
                plugins { id("com.netflix.nebula.contacts") }
                contacts {
                    inheritFromParent.set(providers.gradleProperty("contacts.inheritFromParent").map { it.toBoolean() }.orElse(false))
                    validateEmails = true
                    contact("owner@example.com") { role("owner") }
                }
                check(!contacts.inheritFromParent.get())
                val plugin = plugins.getPlugin(BaseContactsPlugin::class.java)
                check(plugin.allContacts.map { it.email } == listOf("owner@example.com"))
                contacts.contact("late@example.com")
                check(plugin.allContacts.size == 1)
                check(BaseContactsPlugin.getAllContacts(project).map { it.email } == listOf("owner@example.com", "late@example.com"))
                """);
        stored(runner(version, true, true, List.of(":app:help")).build());
        reused(runner(version, true, true, List.of(":app:help")).build());
        var file = projectDir.resolve("app/build.gradle.kts");
        Files.writeString(file, Files.readString(file).replace("owner@example.com", "invalid email"));
        assertThat(runner(version, true, true, List.of(":app:help")).buildAndFail().getOutput())
                .contains("invalid email is not a valid email").doesNotContain("cannot access");
    }

    private BuildResult run(String version, boolean isolated, boolean injected, List<String> projects) {
        return runner(version, isolated, injected, reportTasks(projects)).build();
    }

    private List<String> reportTasks(List<String> projects) {
        return projects.stream()
                .map(path -> (path.isEmpty() ? ":" : ":" + path.replace('/', ':') + ":") + "reportContacts").toList();
    }

    private GradleRunner runner(String version, boolean isolated, boolean injected, List<String> tasks) {
        var args = new ArrayList<>(tasks);
        args.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--console=plain",
                "--stacktrace", "--no-watch-fs", "-Dorg.gradle.unsafe.isolated-projects=" + isolated));
        var runner = GradleRunner.create().withProjectDir(projectDir.toFile()).withGradleVersion(version).withArguments(args);
        return injected ? runner.withPluginClasspath() : runner;
    }

    private Map<String, String> outputs(List<String> projects) throws IOException {
        var result = new LinkedHashMap<String, String>();
        for (var project : projects) result.put(project, Files.readString(projectDir.resolve(project).resolve("build/contacts.txt")));
        return result;
    }

    private void deleteOutputs(List<String> projects) throws IOException {
        for (var project : projects) Files.delete(projectDir.resolve(project).resolve("build/contacts.txt"));
    }

    private String pluginClasspath() throws IOException {
        var metadata = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream("plugin-under-test-metadata.properties")) {
            metadata.load(stream);
        }
        return Arrays.stream(metadata.getProperty("implementation-classpath").split(File.pathSeparator))
                .map(path -> "'" + path.replace("\\", "\\\\").replace("'", "\\'") + "'")
                .reduce((a, b) -> a + "," + b).orElseThrow();
    }

    private void write(String path, String contents) throws IOException {
        var file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }

    private static void stored(BuildResult result) {
        assertThat(result.getOutput()).contains("Configuration cache entry stored.").doesNotContain("cannot access");
    }

    private static void reused(BuildResult result) {
        assertThat(result.getOutput()).contains("Reusing configuration cache.", "Configuration cache entry reused.");
    }
}
