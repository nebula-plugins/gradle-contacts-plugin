Gradle Owner Plugin
===================

Plugin allows the expression of the owners of a project. It then projects this metadata into other plugins, e.g.
the developers section of the POM via the nebula-publishing-plugin or the jar manifest via the gradle-info-plugin.

Using
----------

```
apply plugin: 'owners'
owner {
    email 'mickey@disney.com'
    name 'Mickey Mouse'
}

owner {
    email 'minnie@disney.com'
    name 'Minnie Mouse'
    id 'mmouse'
}
```

Definition
----------

Each call to `owner` takes a closure and adds a single owner. An owner can configure the following fields:

* email - Email address of owner
* name - Full name of owner
* id - Unique id of the owner, should correspond to a github id for an open source project.

Mapping to POMs
---------------
POMs support very rich definitions for developers and contributors (https://maven.apache.org/pom.html#Developers). Though, a quick analysis shows that these are rarely
very complete. This plugin isn't targeting to support all the POM fields, but this could be possible in the future. The
primary goal of this plugin is to identify one or two primary contact of the project, with the expectation that
communcation with developers/collaborators would be done via a mailing list and not trolling the POM file.