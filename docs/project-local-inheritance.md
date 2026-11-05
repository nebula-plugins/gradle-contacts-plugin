# Project-local contacts

Use `contacts.inheritFromParent.set(false)` when a locally applied convention supplies a project's
complete contacts. This `Property<Boolean>` has a convention of `true`, preserving legacy inheritance for existing builds.
It does not switch automatically when Isolated Projects is enabled.

Applying the plugin independently in each project is necessary but does not itself stop the legacy
resolver from reading ancestor extensions. The flag controls that lookup in both static and instance
accessors. Only the consuming project's flag matters; setting it on the root does not opt children in.

## Define ownership once in a convention

Put the shared declarations in `gradle/ownership.gradle`, rather than only in the root project's
extension:

```groovy
contacts {
    inheritFromParent.set(false)
    contact('owners@example.com') {
        moniker 'Team owners'
        roles 'owner', 'notify'
    }
}
```

Each project applies the existing plugin and that convention locally:

```groovy
plugins { id 'com.netflix.nebula.contacts' }
apply from: layout.settingsDirectory.file('gradle/ownership.gradle')

contacts { contact('module@example.com') { role 'writer' } }
```

Kotlin can apply the same convention file:

```kotlin
plugins { id("com.netflix.nebula.contacts") }
apply(from = layout.settingsDirectory.file("gradle/ownership.gradle"))

contacts { contact("module@example.com") { role("writer") } }
```

A precompiled project convention plugin can apply `com.netflix.nebula.contacts` and contain the same
`contacts` block. Consumers then apply that convention's plugin ID. This is exercised by the real
`buildSrc` convention-plugin fixture in `LocalContactsIsolationTest`.

A convention can also supply a lazy default from a tracked Gradle property, in either DSL:

```groovy
def inherit = providers.gradleProperty('contacts.inheritFromParent').map { it.toBoolean() }.orElse(false)
contacts {
    inheritFromParent.convention(inherit)
}
```

```kotlin
contacts {
    inheritFromParent.convention(
        providers.gradleProperty("contacts.inheritFromParent").map { it.toBoolean() }.orElse(false)
    )
}
```

The convention opts consumers into local ownership by default; a consumer can override it with
`inheritFromParent.set(...)`, including another provider. The plugin reads the property only when
contacts are resolved and does not finalize it on read.
In Groovy, define the provider outside `contacts {}`: the existing dynamic contact DSL treats
unknown property names inside that block as contact declarations.

The declaration is written once, but the convention runs separately against each consumer's own
extension. Each project gets its own mutable contacts and role sets. No ancestor's Project,
extension, contacts or maps are read or shared. There is no new contact declaration DSL, ancestor
value model, settings plugin or automatic project participation.

For data-driven ownership, each project can independently read the same file using
`providers.fileContents(layout.settingsDirectory.file(...)).asText`, or tracked Gradle properties,
then configure its own contacts with the existing DSL. A shared file path does not require access
to another Project. Provider reads used during configuration track changes; merely wrapping an
untracked external read in a provider does not add tracking.

## Compatibility and migration responsibilities

With the default `true`, ancestor traversal, contact ordering, duplicate precedence, non-empty
ancestor overrides, role union/filtering, roleless inclusion and the existing slack merge behavior
are unchanged. Existing plugin IDs, constructors, methods, mutable DSL and email validation remain
available. Unmigrated parent lookup under strict isolation still fails with Gradle's cross-project
extension diagnostic; the plugin never silently discards inherited ownership.

The plugin constructs the property through Gradle's public `ObjectFactory`. The existing standalone
`ContactsExtension(people)` and `ContactsExtension(people, validateEmails)` constructors still support
their original DSL and legacy resolution without Gradle services. Standalone callers adopting the
new property must use `ContactsExtension(people, validateEmails, objects)`; reading the new property
on an instance made with an original constructor reports that requirement.

With `false`, accessors fold and clone only the current project's `people` map. They do not visit
parent extensions. Local declaration order, configuring overloads, role filtering, email validation,
defensive copies and observable local duplicate-merge effects are preserved.

Disabling inheritance deliberately stops the old hierarchy roll-up. The convention must declare
the complete ownership required for each project. Arbitrary declarations left only in the root or
an intermediate project will not appear in a child. If the old hierarchy used conflicting defaults,
the convention/data must specify the intended final fields, role union and ordering explicitly;
the plugin does not translate those hierarchy rules into local declarations. Compare generated
ownership metadata before enabling isolation.

Root-only application remains supported when only root consumes contacts. Children that previously
used an implicitly inherited root `contacts(...)` closure must apply the plugin/convention locally.
Both `com.netflix.nebula.contacts` and `com.netflix.nebula.contacts-base` support the flag.

## Accessor timing and publishing

Instance `getAllContacts()` and `getContacts(String)` share a first-read cache and still expose the
mutable cached result. Changing declarations or the flag after that read does not refresh it.
Static `getAllContacts(Project)` and `getContacts(Project, String)` resolve afresh using the current
property value, then return cloned contacts with separate role sets. Provider wiring does not defer
Project access to task execution; these accessors must still run during configuration.

The plugin creates no tasks or eager task realization. Consumers must resolve contacts during their
own configuration after the intended declarations, then snapshot values into task inputs, POM
developers or manifest attributes. Do not capture Project, extensions or plugin instances in task
actions or providers queried during execution.

## Verified envelope

Tests use Gradle 9.0.0 and the 9.2.1 wrapper, JDK 21 on macOS arm64. On those releases, strict
isolation uses `-Dorg.gradle.unsafe.isolated-projects=true` with
`--configuration-cache --configuration-cache-problems=fail`. Rapid fixture edits use `--no-watch-fs`.
Native file-watcher invalidation, other versions/platforms, composite builds, IDE/tooling models and
arbitrary external callbacks are outside this verification.

- `LocalContactsSpec` covers the default flag, per-consumer selection, exact fields, roles, validation,
  defensive copies, mutable email/map-key duplicate effects, lazy provider conventions/overrides,
  original constructors and cached-instance versus fresh-static reads.
- `LocalContactsIsolationTest` covers local-mode ordinary/strict equivalence for single, sibling,
  nested three-level, root-only, empty and skipped-intermediate layouts. A real precompiled convention
  plugin is applied independently in root, siblings and nested consumers, including a Kotlin build.
  It checks cold storage, warm reuse, deleted-output regeneration, shared name/role/email changes,
  local input invalidation without sibling changes, tracked inheritance-provider changes, and rejection
  after opting back into parent lookup through either a provider or an explicit override.
- `ExternalContactsConsumersTest` compares legacy inherited ownership with equivalent complete local
  declarations using published publishing 23.2.0 and info 16.3.0 plugins. POM developer XML and JAR
  `Module-Owner`/`Module-Email` match in ordinary builds and strict isolation, including warm reuse
  and deleted-output regeneration. This focused combination does not establish support for all
  publishing/info umbrella plugins, collectors or versions.
- `ContactsIsolationAssessmentTest` remains unchanged as the legacy baseline. Eight cases intentionally
  require strict isolation failures; a green expected-failure test is not a migrated success.

Run `./gradlew test validatePlugins --console=plain`. Public/protected declarations in the five
existing API classes are compared against compiled base sources using `javap -protected`; the
provider-backed property is additive relative to the released API. This repository does not configure a
library API compatibility Gradle task.
