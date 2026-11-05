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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Successful migrated workflows; intentional legacy IP failures remain in the assessment. */
class ExplicitContactsIsolationTest {
    @TempDir Path projectDir;

    static Stream<String> versions() {
        return Stream.of(SupportedGradleVersion.MIN.version, GradleVersion.current().getVersion()).distinct();
    }

    static Stream<Arguments> layouts() {
        return versions().flatMap(v -> Stream.of("single", "siblings", "nested", "root-only", "empty", "gap")
                .map(layout -> Arguments.of(v, layout)));
    }

    static Stream<Arguments> languages() {
        return versions().flatMap(v -> Stream.of(false, true).map(kotlin -> Arguments.of(v, kotlin)));
    }

    private static final String SHARED = """
            root|shared@example.com|Root|root-github|root-twitter|root-slack|owner
            root|inherited@example.com|Root inherited|root-inherited||root-inherited|owner
            root|root@example.com|||||
            middle|shared@example.com|Middle||middle-twitter|middle-slack|notify
            middle|inherited@example.com|Middle inherited|||middle-inherited|notify
            middle|middle@example.com|||||notify
            """;
    private static final String LOCAL = """
            local|shared@example.com|Leaf|leaf-github|leaf-twitter|leaf-slack|writer
            local|leaf@example.com|||||
            """;

    // Each project independently reads the shared data, with only local/provider-read inputs.
    private static final String CONVENTION = """
            import nebula.plugin.contacts.BaseContactsPlugin
            import nebula.plugin.contacts.Contact
            import nebula.plugin.contacts.ContactLayer
            import nebula.plugin.contacts.ContactsExtension

            def parse = { text ->
                def layers = [:]
                text.readLines().findAll { !it.empty }.each { row ->
                    def f = row.split('[|]', -1)
                    def c = new Contact(f[1])
                    c.moniker = f[2]; c.github = f[3]; c.twitter = f[4]; c.slack = f[5]
                    if (f[6]) c.roles(f[6].split(','))
                    layers.computeIfAbsent(f[0]) { new LinkedHashMap() }.put(f[1], c)
                }
                layers
            }
            def shared = providers.fileContents(layout.settingsDirectory.file('shared.tsv')).asText.map(parse)
            def extension = extensions.getByType(ContactsExtension)
            if (localLayer == 'local') {
                extension.people.putAll(parse(providers.fileContents(layout.projectDirectory.file('local.tsv')).asText.get()).local)
            } else if (localLayer != 'none') {
                extension.people.putAll(shared.get()[localLayer] ?: [:])
            }
            if (providers.gradleProperty('explicit').get().toBoolean()) {
                def selection = providers.gradleProperty('selectionOverride').orElse(inheritedLayers).get()
                if (selection == '') {
                    extension.inheritFrom([])
                } else {
                    def names = selection.split(',').toList()
                    extension.inheritFrom(shared.map { data -> names.collect { ContactLayer.of(data[it] ?: [:]) } })
                }
            }
            abstract class ContactsReport extends DefaultTask {
                @Input abstract ListProperty<String> getRows()
                @OutputFile abstract RegularFileProperty getDestination()
                @TaskAction void report() { destination.get().asFile.text = rows.get().join('\\n') + '\\n' }
            }
            // A registered but unused task must not be realized while resolving contacts.
            tasks.register('unused') { throw new GradleException('unused task was realized') }
            afterEvaluate {
                def plugin = plugins.getPlugin(BaseContactsPlugin)
                def encode = { c -> [c.email, c.moniker ?: '', c.github, c.twitter, c.slack, c.roles.sort().join(',')].join('|') }
                def first = plugin.allContacts
                assert first.collect(encode) == BaseContactsPlugin.getAllContacts(project).collect(encode)
                ['owner', 'notify', 'writer', 'missing', ''].each { role ->
                    assert plugin.getContacts(role).collect(encode) == BaseContactsPlugin.getContacts(project, role).collect(encode)
                }
                def rows = first.collect(encode)
                def owners = plugin.getContacts('owner')*.email.join(',')
                def notify = plugin.getContacts('notify')*.email.join(',')
                tasks.register('reportContacts', ContactsReport) {
                    it.rows.set(rows)
                    destination.set(layout.buildDirectory.file('contacts.txt'))
                }
                // Configuration-time snapshots into native publishing and lazy archive properties.
                publishing.publications.create('contacts', MavenPublication) {
                    groupId = 'example'; artifactId = 'contacts'; version = '1.0'
                    pom.developers {
                        first.each { c ->
                            developer {
                                email = c.email; name = c.moniker ?: c.email
                                id = c.github ?: c.twitter
                                roles.set(c.roles.toList().sort())
                            }
                        }
                    }
                }
                tasks.named('jar', Jar) {
                    archiveFileName.set('contacts.jar')
                    manifest.attributes('Module-Owner': owners, 'Module-Email': notify)
                }
            }
            """;

    @ParameterizedTest(name = "legacy vs explicit ordinary vs explicit strict: {0} {1}")
    @MethodSource("layouts")
    void equivalentLayoutsAndRegeneration(String version, String topology) throws IOException {
        var legacy = projectDir.resolve("legacy");
        var explicit = projectDir.resolve("explicit");
        var projects = fixture(legacy, topology, false);
        fixture(explicit, topology, true);
        stored(run(legacy, version, false, projects));
        var expected = outputs(legacy, projects);
        stored(run(explicit, version, false, projects));
        assertThat(outputs(explicit, projects)).isEqualTo(expected);
        // Use the exact migrated fixture with strict isolation; no warn/ignore mode.
        stored(run(explicit, version, true, projects));
        assertThat(outputs(explicit, projects)).isEqualTo(expected);
        reused(run(explicit, version, true, projects));
        assertThat(outputs(explicit, projects)).isEqualTo(expected);
        deleteOutputs(explicit, projects);
        reused(run(explicit, version, true, projects));
        assertThat(outputs(explicit, projects)).isEqualTo(expected);
        if (topology.equals("nested")) {
            assertThat(Files.readAllLines(explicit.resolve("team/leaf/build/contacts.txt"))).containsExactly(
                    "shared@example.com|Root|root-github|root-twitter|leaf-slack|notify,owner,writer",
                    "leaf@example.com|||||",
                    "inherited@example.com|Root inherited|root-inherited||middle-inherited|notify,owner",
                    "middle@example.com|||||notify", "root@example.com|||||");
        }
    }

    @ParameterizedTest(name = "shared/local data roles email and selection invalidation: {0}")
    @MethodSource("versions")
    void tracksAllContactInputs(String version) throws IOException {
        var projects = fixture(projectDir, "siblings", true);
        stored(run(projectDir, version, true, projects));
        var initial = outputs(projectDir, projects);
        reused(run(projectDir, version, true, projects));
        var shared = SHARED;
        for (var replacement : List.of(
                Map.entry("Root|root-github", "Changed root|changed-github"),
                Map.entry("root-slack|owner", "root-slack|notify"),
                Map.entry("root@example.com", "renamed-root@example.com"))) {
            var before = outputs(projectDir, projects);
            shared = shared.replace(replacement.getKey(), replacement.getValue());
            write(projectDir, "shared.tsv", shared);
            stored(run(projectDir, version, true, projects));
            var after = outputs(projectDir, projects);
            assertThat(after).isNotEqualTo(before);
            assertThat(after.get("app/contacts")).isNotEqualTo(before.get("app/contacts"));
            assertThat(after.get("sibling/contacts")).isNotEqualTo(before.get("sibling/contacts"));
            assertThat(after.get("app/contacts")).contains("Changed root|changed-github");
            reused(run(projectDir, version, true, projects));
            assertThat(outputs(projectDir, projects)).isEqualTo(after);
        }
        var sibling = Files.readString(projectDir.resolve("sibling/build/contacts.txt"));
        var local = LOCAL;
        for (var replacement : List.of(Map.entry("leaf-slack", "changed-slack"),
                Map.entry("|writer", "|writer,reviewer"), Map.entry("leaf@example.com", "new-leaf@example.com"))) {
            var before = outputs(projectDir, projects);
            local = local.replace(replacement.getKey(), replacement.getValue());
            write(projectDir, "app/local.tsv", local);
            stored(run(projectDir, version, true, projects));
            assertThat(outputs(projectDir, projects).get("app/contacts")).isNotEqualTo(before.get("app/contacts"));
            assertThat(Files.readString(projectDir.resolve("app/build/contacts.txt"))).contains("changed-slack");
            assertThat(Files.readString(projectDir.resolve("sibling/build/contacts.txt"))).isEqualTo(sibling);
            reused(run(projectDir, version, true, projects));
        }
        assertThat(outputs(projectDir, projects)).isNotEqualTo(initial);
        stored(run(projectDir, version, true, projects, "-PselectionOverride="));
        assertThat(Files.readAllLines(projectDir.resolve("app/build/contacts.txt"))).containsExactly(
                "shared@example.com|Leaf|leaf-github|leaf-twitter|changed-slack|reviewer,writer", "new-leaf@example.com|||||");
        reused(run(projectDir, version, true, projects, "-PselectionOverride="));
    }

    @ParameterizedTest(name = "Kotlin local shared-data adoption: {0}")
    @MethodSource("versions")
    void kotlinSharedDataAndSnapshotTiming(String version) throws IOException {
        write(projectDir, "settings.gradle.kts", "rootProject.name = \"kotlin-contacts\"\ninclude(\"app\")\n");
        write(projectDir, "build.gradle.kts", "");
        write(projectDir, "owner.txt", "First");
        write(projectDir, "app/build.gradle.kts", """
                import nebula.plugin.contacts.BaseContactsPlugin
                import nebula.plugin.contacts.Contact
                import nebula.plugin.contacts.ContactLayer
                plugins { id("com.netflix.nebula.contacts") }
                val layers = providers.fileContents(layout.settingsDirectory.file("owner.txt")).asText.map { name ->
                    val owner = Contact("owner@example.com")
                    owner.moniker = name
                    owner.role("owner")
                    listOf(ContactLayer.of(linkedMapOf(owner.email to owner)))
                }
                contacts {
                    inheritFrom(layers)
                    contact("local@example.com") { role("notify") }
                }
                val plugin = plugins.getPlugin(BaseContactsPlugin::class.java)
                val first = plugin.allContacts
                contacts.contact("late@example.com")
                check(first.map { it.email } == listOf("local@example.com", "owner@example.com"))
                check(plugin.allContacts === first)
                val fresh = BaseContactsPlugin.getAllContacts(project)
                check(fresh.map { it.email } == listOf("local@example.com", "late@example.com", "owner@example.com"))
                abstract class Report : DefaultTask() {
                    @get:Input abstract val rows: ListProperty<String>
                    @get:OutputFile abstract val destination: RegularFileProperty
                    @TaskAction fun report() { destination.get().asFile.writeText(rows.get().joinToString("\\n") + "\\n") }
                }
                val snapshot = fresh.map { it.email + "|" + (it.moniker ?: "") }
                tasks.register<Report>("reportContacts") {
                    rows.set(snapshot)
                    destination.set(layout.buildDirectory.file("contacts.txt"))
                }
                """);
        for (var isolated : List.of(false, true)) {
            stored(kotlinRun(version, isolated));
            reused(kotlinRun(version, isolated));
            Files.delete(projectDir.resolve("app/build/contacts.txt"));
            reused(kotlinRun(version, isolated));
            assertThat(Files.readAllLines(projectDir.resolve("app/build/contacts.txt"))).containsExactly(
                    "local@example.com|", "late@example.com|", "owner@example.com|First");
        }
        write(projectDir, "owner.txt", "Changed");
        stored(kotlinRun(version, true));
        assertThat(Files.readString(projectDir.resolve("app/build/contacts.txt"))).contains("owner@example.com|Changed");
        reused(kotlinRun(version, true));
    }

    private BuildResult kotlinRun(String version, boolean isolated) {
        return runner(projectDir, version, isolated, List.of(":app:reportContacts")).build();
    }

    @ParameterizedTest(name = "unused inheritance stays lazy under strict isolation: {0}")
    @MethodSource("versions")
    void unusedInheritanceIsNotResolved(String version) throws IOException {
        write(projectDir, "settings.gradle", "rootProject.name = 'lazy-contacts'\ninclude 'app'\n");
        write(projectDir, "build.gradle", "");
        write(projectDir, "app/build.gradle", """
                plugins { id 'com.netflix.nebula.contacts' }
                def unqueried = providers.provider { throw new GradleException('inheritance was resolved') }
                contacts {
                    inheritFrom(unqueried)
                }
                tasks.register('unused') { throw new GradleException('unused task was realized') }
                """);
        stored(runner(projectDir, version, true, List.of(":app:help")).build());
        reused(runner(projectDir, version, true, List.of(":app:help")).build());
    }

    @ParameterizedTest(name = "validation rejects invalid local declarations: {0}, Kotlin={1}")
    @MethodSource("languages")
    void validationFailuresAreUnchanged(String version, boolean kotlin) throws IOException {
        var suffix = kotlin ? ".gradle.kts" : ".gradle";
        write(projectDir, "settings" + suffix, kotlin ? "rootProject.name = \"validation\"\n" : "rootProject.name = 'validation'\n");
        write(projectDir, "build" + suffix, kotlin ? """
                plugins { id("com.netflix.nebula.contacts") }
                contacts {
                    inheritFrom(emptyList())
                    validateEmails = true
                    contact("invalid local")
                }
                """ : """
                plugins { id 'com.netflix.nebula.contacts' }
                contacts {
                    inheritFrom([])
                    validateEmails = true
                    contact('invalid local')
                }
                """);
        for (var isolated : List.of(false, true)) {
            var failed = runner(projectDir, version, isolated, List.of("help")).buildAndFail();
            assertThat(failed.getOutput()).contains("invalid local is not a valid email")
                    .doesNotContain("cannot access", "Project.extensions");
        }
    }

    private List<String> fixture(Path dir, String topology, boolean explicit) throws IOException {
        var projects = switch (topology) {
            case "single", "root-only" -> List.of("");
            case "siblings" -> List.of("", "app", "sibling");
            case "nested", "empty" -> List.of("", "team", "team/leaf");
            case "gap" -> List.of("", "gap/leaf");
            default -> throw new IllegalArgumentException(topology);
        };
        var includes = topology.equals("root-only") ? "include 'app'\n" : projects.stream().filter(p -> !p.isEmpty())
                .map(p -> "include '" + p.replace('/', ':') + "'\n").reduce("", String::concat);
        write(dir, "settings.gradle", "rootProject.name = 'contacts-fixture'\n" + includes);
        write(dir, "gradle.properties", "explicit=" + explicit + "\n");
        write(dir, "shared.tsv", topology.equals("empty") ? SHARED.replaceAll("(?m)^middle.*\\n", "") : SHARED);
        if (topology.equals("root-only")) write(dir, "app/build.gradle", "// Does not consume contacts.\n");
        if (topology.equals("gap")) write(dir, "gap/build.gradle", "// Deliberately skipped participant.\n");
        for (var path : projects) {
            var middle = path.equals("team");
            var local = path.isEmpty() ? "root" : middle ? (topology.equals("empty") ? "none" : "middle") : "local";
            var selection = path.isEmpty() ? "" : path.equals("team/leaf") ? "middle,root" : "root";
            var prefix = path.isEmpty() ? "" : path + "/";
            write(dir, prefix + "local.tsv", LOCAL.replace("leaf@example.com", path.equals("sibling") ? "sibling@example.com" : "leaf@example.com"));
            write(dir, prefix + "build.gradle", """
                    plugins {
                        id 'com.netflix.nebula.contacts-base'
                        id 'java'
                        id 'maven-publish'
                    }
                    """ + "def localLayer = '" + local + "'\ndef inheritedLayers = '" + selection + "'\n" + CONVENTION);
        }
        return projects;
    }

    private BuildResult run(Path dir, String version, boolean isolated, List<String> projects, String... extra) {
        var tasks = new ArrayList<String>();
        for (var path : projects) {
            var prefix = path.isEmpty() ? ":" : ":" + path.replace('/', ':') + ":";
            tasks.addAll(List.of(prefix + "reportContacts", prefix + "jar", prefix + "generatePomFileForContactsPublication"));
        }
        tasks.addAll(List.of(extra));
        return runner(dir, version, isolated, tasks).build();
    }

    private GradleRunner runner(Path dir, String version, boolean isolated, List<String> tasks) {
        var arguments = new ArrayList<>(tasks);
        arguments.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace",
                "--console=plain", "--no-watch-fs", "-Dorg.gradle.unsafe.isolated-projects=" + isolated));
        return GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath()
                .withGradleVersion(version).withArguments(arguments);
    }

    private Map<String, String> outputs(Path dir, List<String> projects) throws IOException {
        var result = new LinkedHashMap<String, String>();
        for (var project : projects) {
            var build = dir.resolve(project).resolve("build");
            result.put(project + "/contacts", Files.readString(build.resolve("contacts.txt")));
            result.put(project + "/pom", Files.readString(build.resolve("publications/contacts/pom-default.xml")));
            try (var jar = new JarFile(build.resolve("libs/contacts.jar").toFile())) {
                var attributes = jar.getManifest().getMainAttributes();
                result.put(project + "/owner", attributes.getValue("Module-Owner"));
                result.put(project + "/notify", attributes.getValue("Module-Email"));
            }
        }
        return result;
    }

    private void deleteOutputs(Path dir, List<String> projects) throws IOException {
        for (var project : projects) {
            var build = dir.resolve(project).resolve("build");
            for (var output : List.of("contacts.txt", "publications/contacts/pom-default.xml", "libs/contacts.jar")) {
                Files.delete(build.resolve(output));
            }
        }
    }

    private static void stored(BuildResult result) {
        assertThat(result.getOutput()).contains("Configuration cache entry stored.")
                .doesNotContain("Configuration cache problems found", "cannot access");
    }

    private static void reused(BuildResult result) {
        assertThat(result.getOutput()).contains("Reusing configuration cache.", "Configuration cache entry reused.");
    }

    private static void write(Path dir, String path, String contents) throws IOException {
        var file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
