Gradle Owner Plugin
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
contacts {
    team {
        distributionList 'EngineeringTools@netflix.com'
    }
}
```

```
apply plugin: 'owners'
contacts {
    team {
         distributionList 'disney'
         role 'owner'
    }
    person {
         id mmouse
    }
    person {
         name 'tech writer'
         role 'writer'
    }
}

// Simple

// On release example
contacts {
    team {
        distributionList 'EngineeringTools@netflix.com'
    }
    team {
        distributionList 'downstream@netflix.com'
        role 'notify'
    }
}

// Funny Example
contacts {
    team {
        name ''
        distributionList ''
    }

    owner {
        email 'mickey@disney.com'
        name 'Mickey Mouse'
    }

    owner {
        email 'minnie@disney.com'
        name 'Minnie Mouse'
        id 'mmouse'
    }
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