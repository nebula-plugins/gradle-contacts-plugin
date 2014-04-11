Gradle Contacts Plugin
===================

Plugin allows the expression of the contacts involved with a project. This data is then made available to other plugins,
to be injected in different outputs, e.g. the developers section of the POM via the nebula-publishing-plugin or the jar
manifest via the gradle-info-plugin. Each contact can be marked with a _role_ that the other plugins will use as a
discriminator. Having no _role_ indicates that the contact should be part of every _role_.

Using
----------

The simplest use is to specify a single point of contact:

```
apply plugin: 'contacts'
contacts 'minnie@disney.com'
```


Muliple people in their simple form:

```
apply plugin: 'contacts'
contacts 'minnie@disney.com', 'mickey@disney.com', 'club@disney.com'
```

A little bit richer, only one user:

```
apply plugin: 'contacts'
contacts {
    'club@disney.com' {
        name 'Mickey Mouse Club'
    }
}
```

Mix of people and teams:

```
apply plugin: 'contacts'
contacts {
    'club@disney.com' {
        name 'Mickey Mouse Club'
    }
    'mickey@disney.com' {
        name 'Mickey Mouse'
    }
    'minnie@disney.com' {
        name 'Minnie Mouse'
        id 'mmouse'
    }
}
```

Using roles to differentiate:


```
apply plugin: 'contacts'
contacts {
    'club@disney.com'
    'bobby@company.com' {
        roles 'notify', 'owner'
    }
    'billy@company.com' {
        role 'techwriter'
    }
    'downstream@netflix.com'
        role 'notify'
    }
}
```

Definition
----------

Each `person` takes a closure and adds a single person. A contact can configure the following fields:

* email - Email address of owner
* name - Full name of owner
* id - Unique id of the owner, should correspond to a github id for an open source project.
* role - See below.

Contacts can have `role`s specified. It can be done via these calls, which can be called multiple times:

* role(String singleRoleName)
* roles(String... roleNames)

Mapping to POMs
---------------
POMs support very rich definitions for developers and contributors (https://maven.apache.org/pom.html#Developers). Though, a quick analysis shows that these are rarely
very complete. This plugin isn't targeting to support all the POM fields, but this could be possible in the future. The
primary goal of this plugin is to identify one or two primary contact of the project, with the expectation that
communication with developers/collaborators would be done via a mailing list and not trolling the POM file.

TBD
---------------
* Merge duplicate calls
* Formalize possible roles via a DSL

Gradle Manifest Contacts Plugin
===================

Plugin allows the expression of the contacts involved with a project. This data is then made available to other plugins,
to be injected in different outputs, e.g. the developers section of the POM via the nebula-publishing-plugin or the jar
manifest via the gradle-info-plugin. Each contact can be marked with a _role_ that the other plugins will use as a
discriminator. Having no _role_ indicates that the contact should be part of every _role_.
