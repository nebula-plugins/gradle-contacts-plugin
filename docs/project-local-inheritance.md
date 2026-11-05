# Explicit project-local inheritance

The existing `com.netflix.nebula.contacts` and `com.netflix.nebula.contacts-base` project plugins
support an additive inheritance API. Without an `inheritFrom` call, resolution still walks ancestor
project extensions. That preserves existing ordinary builds. Under strict Isolated Projects,
legacy ancestor access fails with Gradle's cross-project extension diagnostic; isolation never
silently changes ownership to local-only data.

## API and ordering

```groovy
import nebula.plugin.contacts.ContactLayer

contacts {
    inheritFrom([teamLayer, rootLayer]) // List<ContactLayer>, nearest parent first
    // Alternatively: inheritFrom(providerOfLayers)
}
```

`ContactLayer.of(Map<String, Contact>)` takes an ordered, deep snapshot of map keys, contact
fields and role sets. The layer contains only detached values. `getPeople()` returns new contacts,
maps and role sets on every call. No source contact, extension, map, role set or Project is retained.
Use a `LinkedHashMap` to preserve declaration order.

`ContactsExtension.inheritFrom(List<ContactLayer>)` copies the supplied list immediately.
`inheritFrom(Provider<? extends List<ContactLayer>>)` keeps the provider lazy until a contact
accessor resolves it. An absent provider fails at that point; it does not fall back to legacy
inheritance. Subsequent `inheritFrom` calls replace the selection for fresh resolution.

An explicitly empty list selects local-only contacts:

```groovy
contacts { inheritFrom([]) }
```

```kotlin
contacts { inheritFrom(emptyList()) }
```

Omitting the call means legacy mode. An empty layer is allowed; skipped ancestors simply need no
layer. Each layer must contain that ancestor's declarations, rather than its already inherited
result. Select layers explicitly for each project using its own immutable path or convention.

Resolution starts with the receiving project's mutable `people` map, then folds the supplied layers
in list order. First occurrence determines position. Duplicate matching still compares an existing
contact's email to the incoming map key, including the legacy behavior if a mutable email differs
from its original map key. Non-empty ancestor moniker, GitHub and Twitter values override closer
values. Roles form a union. A roleless contact is included in every role filter. Duplicate slack
retains the first occurrence's slack; ancestor slack is deliberately not merged.

The local Groovy dynamic DSL, explicit `contact`/`addPerson` overloads, Action/Closure configuration,
mutable fields and email validation policy are unchanged. Inherited declarations are not revalidated
against the receiving project's `validateEmails` flag, matching the legacy behavior. Validate shared
data when authoring/loading it if required.

## Supplying shared data independently

Apply the existing project plugin locally in every consuming project. Move shared declarations out
of arbitrary mutable root extension state and into an independently readable data file or a locally
applied project convention. Do not read `rootProject.extensions`, a parent extension, or another
project's provider to construct the layers. Detaching after such a read does not make the read isolated.
There is no settings plugin or automatic project participation.

For example, put these values in `owners.properties` beside `settings.gradle`:

```properties
email=owners@example.com
name=Shared owners
roles=owner,notify
```

Each participating **child** can read that file itself. Groovy:

```groovy
import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactLayer

plugins { id 'com.netflix.nebula.contacts' }

def sharedLayers = providers.fileContents(layout.settingsDirectory.file('owners.properties')).asText.map { text ->
    def data = new Properties()
    data.load(new StringReader(text))
    def owner = new Contact(data.getProperty('email'))
    owner.moniker = data.getProperty('name')
    data.getProperty('roles', '').split(',').findAll { it }.each { owner.role(it) }
    [ContactLayer.of([(owner.email): owner])]
}
contacts {
    inheritFrom(sharedLayers)
    contact('module@example.com') { role 'writer' }
}
```

Kotlin:

```kotlin
import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactLayer
import java.io.StringReader
import java.util.Properties

plugins { id("com.netflix.nebula.contacts") }

val sharedLayers = providers.fileContents(layout.settingsDirectory.file("owners.properties")).asText.map { text ->
    val data = Properties().apply { load(StringReader(text)) }
    val owner = Contact(data.getProperty("email"))
    owner.moniker = data.getProperty("name")
    data.getProperty("roles", "").split(',').filter { it.isNotEmpty() }.forEach { owner.role(it) }
    listOf(ContactLayer.of(linkedMapOf(owner.email to owner)))
}
contacts {
    inheritFrom(sharedLayers)
    contact("module@example.com") { role("writer") }
}
```

The root can keep local declarations and explicitly select no ancestors. A root-only plugin is
still sufficient when only root consumes contacts. Children that previously called an implicitly
inherited root `contacts(...)` closure must now apply the plugin/convention locally.

Provider callbacks must produce detached layers from local/tracked data, without capturing another
project or extension. `providers.fileContents` and `providers.gradleProperty` track changes when
queried during configuration. A provider around an untracked external read does not add tracking.

## Snapshot timing and consumer integration

Layer values snapshot at `ContactLayer.of`, not at the time the layer is later resolved. Provider-backed
layers snapshot whenever their provider constructs them. Changing source contacts after constructing
a fixed layer cannot change it. Static accessors resolve supplied providers afresh; providers may have
their own finalization/caching rules.

`BaseContactsPlugin.getAllContacts()` and `getContacts(String)` still cache together on the first
instance read. They expose the same mutable cached contact objects as before. Static
`getAllContacts(Project)` and `getContacts(Project, String)` resolve afresh and return defensive
clones, including separate role sets. Editing local declarations or replacing layers after an
instance read does not refresh that instance snapshot.

For compatibility, duplicate merges still mutate the receiving project's own first contact before
the result is cloned. That mutation can remain visible to later static reads even after layers are
removed. Detached ancestor entries can be merged during a resolution, but their layer snapshots
and original declarations are never mutated. Legacy cross-project ancestor mutation has no equivalent
in the isolated workflow.

Existing accessor signatures used by publishing/info consumers remain available. Resolve during the
consuming project's configuration, after all intended declarations, then assign value snapshots to
task inputs, POM developers and manifest attributes. Register tasks lazily. Do not capture Project,
extensions or the plugin instance in task actions or deferred execution-time providers. The contacts
plugin creates no tasks and does not resolve unused inheritance providers.

## Verified envelope

The test matrix uses JDK 21 on macOS arm64 with Gradle 9.0.0 and the 9.2.1 wrapper. On these releases
strict isolation uses `-Dorg.gradle.unsafe.isolated-projects=true`, together with
`--configuration-cache --configuration-cache-problems=fail`. Use version-appropriate flags on other
Gradle releases. Rapid fixture edits use `--no-watch-fs`; native file-watcher invalidation is not
part of this verification.

`ExplicitContactsIsolationTest` compares legacy ordinary builds with explicit ordinary builds, then
runs those explicit fixtures under strict isolation. It covers single, sibling, nested three-level,
root-only, empty-layer and skipped-intermediate layouts, exact ordered fields, role filters, native
Maven developer data and JAR ownership attributes. It verifies cold storage, warm reuse, deleted-output
regeneration, shared/local data/role/email changes and layer selection. Kotlin shared-file adoption,
first-read versus fresh access and unused-provider/task laziness are tested separately. Unit tests
cover snapshot detachment, duplicate side effects, validation and missing providers.

`ExternalContactsConsumersTest` exercises published `nebula-publishing-plugin` 23.2.0
(`com.netflix.nebula.maven-developer`) and `gradle-info-plugin` 16.3.0
(`com.netflix.nebula.info-broker`, `info-owners`, `info-jar`) with the contacts implementation under
test. These are actual plugin call sites, separate from the native output fixtures. The focused
combination produces equivalent developer XML and `Module-Owner`/`Module-Email` JAR attributes in
legacy ordinary, explicit ordinary and explicit strict-isolation builds, including cache reuse and
regeneration. No external blocker was observed in this combination. Full publishing/info umbrella
plugins and other versions or collectors are outside this claim.

`ContactsIsolationAssessmentTest` remains a characterization baseline. Methods named `expectedFailure`
deliberately require strict legacy failures; their green JUnit result is not a migrated success.

Run verification with:

```shell
./gradlew test validatePlugins --console=plain
```

The verified run completed 104 tests with zero failures, errors or skips, and `validatePlugins`
passed. Eight assessment cases intentionally require strict legacy isolation failures; the successful
migrated workflows are in the separate explicit-mode and external-consumer suites.

Public/protected declarations in the five existing API classes were compared against compiled HEAD
using `javap -protected`; all constructors, methods, fields and class declarations remain present.
The new API is additive. This repository does not configure a library API compatibility Gradle task.

This is a verified contacts workflow, not a universal compatibility claim. Composite/included builds,
IDE/tooling models, configuration-on-demand, other JDKs/operating systems, later Gradle releases and
arbitrary external plugin callbacks require separate validation. Consumers must migrate their shared
declarations and validate their chosen publishing/info versions before enabling isolation.
