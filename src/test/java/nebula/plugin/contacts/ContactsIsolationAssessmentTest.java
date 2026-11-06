package nebula.plugin.contacts;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assessment of the existing implementation, not a declaration of IP compatibility.
 * Methods named expectedFailure deliberately require a failing, strict IP build.
 */
class ContactsIsolationAssessmentTest {
    @TempDir
    Path projectDir;

    enum Mode { CONFIGURATION_CACHE, ISOLATED_PROJECTS }

    static Stream<Arguments> versionsAndModes() {
        return versions().flatMap(version -> Stream.of(Mode.values()).map(mode -> Arguments.of(version, mode)));
    }

    private static Stream<String> versions() {
        // Exercise the minimum supported by this test JVM and the actual wrapper.
        return Stream.of(SupportedGradleVersion.MIN.version, GradleVersion.current().getVersion()).distinct();
    }

    static Stream<Arguments> inheritedScenarios() {
        return versionsAndModes().flatMap(args -> Stream.of("app", "team:leaf", "gap:leaf")
                .map(path -> Arguments.of(args.get()[0], args.get()[1], path)));
    }

    // Fixture task has only value inputs: never execute plugin accessors or capture Project in an action.
    private static final String REPORT_TASK = """
            abstract class ContactsReport extends DefaultTask {
                @Input abstract ListProperty<String> getRows()
                @OutputFile abstract RegularFileProperty getReportFile()
                @TaskAction void report() {
                    def text = rows.get().join('\\n') + '\\n'
                    reportFile.get().asFile.text = text
                    logger.lifecycle(text)
                }
            }
            def encode = { c ->
                [c.email, c.moniker ?: '', c.github, c.twitter, c.slack, c.roles.sort().join(',')].join('|')
            }
            """;

    private static final String REGISTER_REPORT = """
            tasks.register('reportContacts', ContactsReport) {
                rows.set(snapshot)
                reportFile.set(layout.buildDirectory.file('contacts.txt'))
            }
            """;

    @ParameterizedTest(name = "single project {0} {1}")
    @MethodSource("versionsAndModes")
    void singleProjectResolutionPublishingReuseAndFileInvalidation(String version, Mode mode) throws IOException {
        write("settings.gradle", "rootProject.name = 'contacts-assessment'\n");
        write("contact-name.txt", "Initial owner");
        write("build.gradle", """
                plugins {
                    id 'com.netflix.nebula.contacts'
                    id 'maven-publish'
                }
                """ + REPORT_TASK + """
                def ownerName = providers.fileContents(layout.projectDirectory.file('contact-name.txt')).asText.get()
                contacts 'everyone@example.com', 'owner@example.com'
                contacts {
                    validateEmails = true
                    'owner@example.com' {
                        moniker ownerName
                        github 'owner'
                        twitter 'owner-twitter'
                        slack 'owner-slack'
                        roles 'owner', 'notify'
                    }
                    contact('writer@example.com', { c -> c.role('writer') } as Action)
                }
                def plugin = plugins.getPlugin(nebula.plugin.contacts.BaseContactsPlugin)
                // The instance API snapshots on first access; the static API resolves each time.
                def cached = plugin.getAllContacts()
                extensions.getByType(nebula.plugin.contacts.ContactsExtension).contact('late@example.com')
                assert plugin.getAllContacts().size() == 3
                assert plugin.getContacts('owner')*.email == ['everyone@example.com', 'owner@example.com']
                assert nebula.plugin.contacts.BaseContactsPlugin.getContacts(project, 'writer')*.email ==
                    ['everyone@example.com', 'writer@example.com', 'late@example.com']
                def snapshot = nebula.plugin.contacts.BaseContactsPlugin.getAllContacts(project).collect(encode)
                cached.find { it.email == 'owner@example.com' }.roles.add('snapshot-only')
                assert !extensions.getByType(nebula.plugin.contacts.ContactsExtension).people['owner@example.com'].roles.contains('snapshot-only')
                def publishedContacts = nebula.plugin.contacts.BaseContactsPlugin.getAllContacts(project)
                publishing {
                    publications {
                        contactsPublication(MavenPublication) {
                            groupId = 'example'
                            artifactId = 'contacts'
                            version = '1.0'
                            pom {
                                developers {
                                    publishedContacts.each { c ->
                                        developer {
                                            email = c.email
                                            name = c.moniker ?: c.email
                                            roles.set(c.roles.toList())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """ + REGISTER_REPORT);

        var tasks = new String[]{"reportContacts", "generatePomFileForContactsPublicationPublication"};
        stored(run(version, mode, false, tasks));
        assertSingleProjectOutput("Initial owner");
        reused(run(version, mode, false, tasks));
        assertSingleProjectOutput("Initial owner");
        write("contact-name.txt", "Changed owner");
        var changed = run(version, mode, false, tasks);
        stored(changed);
        assertThat(changed.getOutput()).contains("contact-name.txt").doesNotContain("Reusing configuration cache.");
        assertSingleProjectOutput("Changed owner");
        reused(run(version, mode, false, tasks));
        assertSingleProjectOutput("Changed owner");
    }

    private void assertSingleProjectOutput(String owner) throws IOException {
        assertThat(Files.readAllLines(projectDir.resolve("build/contacts.txt"))).containsExactly(
                "everyone@example.com|||||", "owner@example.com|" + owner + "|owner|owner-twitter|owner-slack|notify,owner",
                "writer@example.com|||||writer", "late@example.com|||||");
        assertThat(Files.readString(projectDir.resolve("build/publications/contactsPublication/pom-default.xml")))
                .contains("<email>owner@example.com</email>", "<name>" + owner + "</name>", "<role>owner</role>")
                .doesNotContain("snapshot-only");
    }

    @ParameterizedTest(name = "local extensions in siblings and nested projects {0} {1}")
    @MethodSource("versionsAndModes")
    void localExtensionsWorkWithoutInheritedResolution(String version, Mode mode) throws IOException {
        write("settings.gradle", "rootProject.name = 'contacts-assessment'\ninclude 'app', 'team:leaf'\n");
        for (var path : List.of("", "app/", "team/", "team/leaf/")) {
            write(path + "build.gradle", """
                    plugins { id 'com.netflix.nebula.contacts-base' }
                    """ + REPORT_TASK + """
                    contacts { contact('local@example.com') { moniker 'Local' } }
                    def snapshot = extensions.getByType(nebula.plugin.contacts.ContactsExtension).people.values().collect(encode)
                    """ + REGISTER_REPORT);
        }
        var tasks = new String[]{":reportContacts", ":app:reportContacts", ":team:reportContacts", ":team:leaf:reportContacts"};
        stored(run(version, mode, false, tasks));
        reused(run(version, mode, false, tasks));
        for (var path : List.of("", "app/", "team/", "team/leaf/")) {
            assertThat(Files.readString(projectDir.resolve(path + "build/contacts.txt")))
                    .isEqualTo("local@example.com|Local||||\n");
        }
        var leafBuild = projectDir.resolve("team/leaf/build.gradle");
        Files.writeString(leafBuild, Files.readString(leafBuild).replace("moniker 'Local'", "moniker 'Changed local'"));
        var changed = run(version, mode, false, tasks);
        stored(changed);
        assertThat(changed.getOutput()).contains("team/leaf/build.gradle").doesNotContain("Reusing configuration cache.");
        reused(run(version, mode, false, tasks));
        assertThat(Files.readString(projectDir.resolve("team/leaf/build/contacts.txt")))
                .isEqualTo("local@example.com|Changed local||||\n");
        assertThat(Files.readString(projectDir.resolve("app/build/contacts.txt")))
                .isEqualTo("local@example.com|Local||||\n");
    }

    @ParameterizedTest(name = "root-only application {0} {1}")
    @MethodSource("versionsAndModes")
    void rootOnlyApplicationWorksWhenOnlyRootResolvesContacts(String version, Mode mode) throws IOException {
        write("settings.gradle", "rootProject.name = 'contacts-assessment'\ninclude 'app'\n");
        write("build.gradle", """
                plugins { id 'com.netflix.nebula.contacts' }
                """ + REPORT_TASK + """
                contacts 'root@example.com'
                def snapshot = nebula.plugin.contacts.BaseContactsPlugin.getAllContacts(project).collect(encode)
                """ + REGISTER_REPORT);
        write("app/build.gradle", "assert extensions.findByType(nebula.plugin.contacts.ContactsExtension) == null\n");
        stored(run(version, mode, false, ":reportContacts", ":app:help"));
        reused(run(version, mode, false, ":reportContacts", ":app:help"));
        assertThat(Files.readString(projectDir.resolve("build/contacts.txt"))).isEqualTo("root@example.com|||||\n");
    }

    @ParameterizedTest(name = "expectedFailure: inherited resolution {0} {1} {2}")
    @MethodSource("inheritedScenarios")
    void expectedFailureInheritedResolution(String version, Mode mode, String path) throws IOException {
        write("settings.gradle", "rootProject.name = 'contacts-assessment'\ninclude '" + path + "'\n");
        write("root-name.txt", "Root");
        write("build.gradle", """
                plugins { id 'com.netflix.nebula.contacts' }
                def rootName = providers.fileContents(layout.projectDirectory.file('root-name.txt')).asText.get()
                contacts {
                    contact('shared@example.com') {
                        moniker rootName
                        github 'root-github'
                        twitter 'root-twitter'
                        slack 'root-slack'
                        role 'owner'
                    }
                    contact('root@example.com')
                }
                """);
        if (path.equals("team:leaf")) {
            write("team/build.gradle", """
                    plugins { id 'com.netflix.nebula.contacts-base' }
                    contacts {
                        contact('shared@example.com') { moniker 'Team'; github 'team-github'; role 'notify' }
                        contact('team@example.com')
                    }
                    """);
        } else if (path.equals("gap:leaf")) {
            write("gap/build.gradle", "// Deliberately does not apply contacts.\n");
        }
        var directory = path.replace(':', '/') + "/";
        write(directory + "build.gradle", """
                plugins { id 'com.netflix.nebula.contacts' }
                """ + REPORT_TASK + """
                contacts {
                    contact('shared@example.com') {
                        moniker 'Leaf'; github 'leaf-github'; twitter 'leaf-twitter'; slack 'leaf-slack'; role 'writer'
                    }
                    contact('leaf@example.com')
                }
                // Same-project lifecycle callback, matching existing consumer-style tests.
                afterEvaluate {
                    def plugin = plugins.getPlugin(nebula.plugin.contacts.BaseContactsPlugin)
                    def instanceRows = plugin.getAllContacts().collect(encode)
                    def snapshot = nebula.plugin.contacts.BaseContactsPlugin.getAllContacts(project).collect(encode)
                    assert instanceRows == snapshot
                    assert plugin.getContacts('owner')*.email ==
                        nebula.plugin.contacts.BaseContactsPlugin.getContacts(project, 'owner')*.email
                    // Existing merge mutates the local duplicate before returning cloned contacts.
                    assert extensions.getByType(nebula.plugin.contacts.ContactsExtension).people['shared@example.com'].moniker ==
                        plugin.getAllContacts().first().moniker
                    """ + REGISTER_REPORT + """
                }
                """);
        var task = ":" + path + ":reportContacts";
        if (mode == Mode.ISOLATED_PROJECTS) {
            // Characterization only: strict failure must name the plugin's actual boundary violation.
            inheritedFailure(run(version, mode, true, task), path);
            inheritedFailure(run(version, mode, true, task), path);
        } else {
            stored(run(version, mode, false, task));
            assertInheritedOutput(directory, path, "Root");
            reused(run(version, mode, false, task));
            assertInheritedOutput(directory, path, "Root");
            write("root-name.txt", "Updated root");
            var changed = run(version, mode, false, task);
            stored(changed);
            assertThat(changed.getOutput()).contains("root-name.txt").doesNotContain("Reusing configuration cache.");
            assertInheritedOutput(directory, path, "Updated root");
            reused(run(version, mode, false, task));
            assertInheritedOutput(directory, path, "Updated root");
        }
    }

    private void assertInheritedOutput(String directory, String path, String rootName) throws IOException {
        var rows = new ArrayList<>(List.of("shared@example.com|" + rootName + "|root-github|root-twitter|leaf-slack|"
                + (path.equals("team:leaf") ? "notify,owner,writer" : "owner,writer"), "leaf@example.com|||||"));
        if (path.equals("team:leaf")) {
            rows.add("team@example.com|||||");
        }
        rows.add("root@example.com|||||");
        assertThat(Files.readAllLines(projectDir.resolve(directory + "build/contacts.txt"))).isEqualTo(rows);
    }

    private void inheritedFailure(BuildResult result, String path) {
        assertThat(result.getOutput())
                .contains("Project ':" + path + "' cannot access 'Project.extensions' functionality on another project")
                .contains("BaseContactsPlugin", "Configuration cache entry discarded")
                .doesNotContain("Reusing configuration cache.");
    }

    @ParameterizedTest(name = "expectedFailure: implicit parent DSL lookup {0} {1}")
    @MethodSource("versionsAndModes")
    void expectedFailureImplicitParentContactsLookup(String version, Mode mode) throws IOException {
        write("settings.gradle", "rootProject.name = 'contacts-assessment'\ninclude 'app'\n");
        write("build.gradle", """
                plugins { id 'com.netflix.nebula.contacts' }
                contacts 'root@example.com'
                """);
        write("app/build.gradle", REPORT_TASK + """
                // Legacy root-only use: both calls implicitly look up the parent's extra-property closure.
                contacts 'child@example.com'
                def snapshot = contacts('root@example.com', 'child@example.com').collect(encode)
                """ + REGISTER_REPORT);
        if (mode == Mode.ISOLATED_PROJECTS) {
            var result = run(version, mode, true, ":app:reportContacts");
            assertThat(result.getOutput()).contains("Project ':app' cannot dynamically look up a method in the parent project ':'")
                    .contains("Configuration cache entry discarded")
                    .doesNotContain("Reusing configuration cache.");
        } else {
            stored(run(version, mode, false, ":app:reportContacts"));
            reused(run(version, mode, false, ":app:reportContacts"));
            assertThat(Files.readAllLines(projectDir.resolve("app/build/contacts.txt")))
                    .containsExactly("root@example.com|||||", "child@example.com|||||");
        }
    }

    private BuildResult run(String version, Mode mode, boolean expectFailure, String... tasks) throws IOException {
        // Regenerate observable artifacts even on cache reuse; a stale file cannot satisfy the assertions.
        for (var output : List.of("build/contacts.txt", "app/build/contacts.txt", "team/build/contacts.txt",
                "team/leaf/build/contacts.txt", "gap/leaf/build/contacts.txt",
                "build/publications/contactsPublication/pom-default.xml")) {
            Files.deleteIfExists(projectDir.resolve(output));
        }
        var arguments = new ArrayList<>(List.of(tasks));
        // Avoid native file-watcher timing when fixtures edit and immediately rebuild inputs.
        arguments.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace", "--console=plain", "--no-watch-fs"));
        // Both assessed releases use the experimental property, not the newer --isolated-projects flag.
        arguments.add("-Dorg.gradle.unsafe.isolated-projects=" + (mode == Mode.ISOLATED_PROJECTS));
        var runner = GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath()
                .withGradleVersion(version).withArguments(arguments);
        return expectFailure ? runner.buildAndFail() : runner.build();
    }

    private static void stored(BuildResult result) {
        assertThat(result.getOutput()).contains("Configuration cache entry stored.")
                .doesNotContain("Configuration cache problems found", "cannot access");
        assertThat(result.getTasks()).anySatisfy(task -> assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS));
    }

    private static void reused(BuildResult result) {
        assertThat(result.getOutput()).contains("Reusing configuration cache.", "Configuration cache entry reused.");
        assertThat(result.getTasks()).anySatisfy(task -> assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS));
    }

    private void write(String relativePath, String contents) throws IOException {
        var file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
