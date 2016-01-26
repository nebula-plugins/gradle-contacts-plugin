Gradle Contacts Plugin
===================
[![Build Status](https://travis-ci.org/nebula-plugins/gradle-contacts-plugin.svg?branch=master)](https://travis-ci.org/nebula-plugins/gradle-contacts-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/gradle-contacts-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/gradle-contacts-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/gradle-contacts-plugin?utm_source=badgeutm_medium=badgeutm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-contacts-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)


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

    apply plugin: 'contacts'
    contacts 'minnie@disney.com', 'mickey@disney.com', 'club@disney.com'

A little bit richer, only one user:

    apply plugin: 'contacts'
    contacts {
        'club@disney.com' {
            name 'Mickey Mouse Club'
        }
    }

Mix of people and teams:

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

Using roles to differentiate:

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


Definition
----------

Each `person` takes a closure and adds a single person. A contact can configure the following fields:

* email - Email address of owner
* name - Full name of owner
* github - Github username
* twitter - Twitter handle
* role - See below.

Contacts can have `role`s specified. It can be done via these calls, which can be called multiple times:

* role(String singleRoleName)
* roles(String... roleNames)

Base Plugin
---------------
Technically the _contacts-base_ (nebula.plugin.contacts.BaseContactsPlugin) plugin is what provides the ability to specify
contacts. The _contacts_ plugin just applies the _contacts-base_, _contacts-manifest_, and _contacts-pom_ plugins. The learn
 about the other plugins read below.

TBD
---------------
* Merge duplicate calls
* Formalize possible roles via a DSL

Gradle Manifest Contacts Plugin
===================

Plugin takes the contacts and stuffs them to the gradle-info-plugin, this makes them available as manifest values (for
the .jar, .rpm, a properties file). We'll publish to tags, Module-Owner and Module-Email. Module-Owner is a common separated
list of all contacts that have the "owner" role (or no role). Module-Email is a common separated list of
contacts that want to be notified when changes are made to this module, they have to have the role of "notify"
(or no role). We're assuming that multiple owners are allowed, and we can just comma separate them.

    buildscript {
        repositories { jcenter() }
        dependencies { classpath 'com.netflix.nebula:gradle-contacts-plugin:1.9.+' }
        dependencies { classpath 'com.netflix.nebula:nebula-publishing-plugin:1.9.+' }
    }
    apply plugin: 'contacts-base'
    apply plugin: 'contacts-manifest'
    apply plugin: 'info'
    contacts {
        'bobby@company.com' {
            roles 'owner'
        }
        'billy@company.com' { }
        'downstream@netflix.com' {
            role 'notify'
        }
    }

Would produce the following values in build/manifest/info.properties:

    Module-Owner=bobby@company.com,billy@company.com
    Module-Email=billy@company.com,downstream@netflix.com



Gradle POM Contacts Plugin
===================

Plugin takes all the contacts and adds them to the developers section of the POM if using the nebula-publishing-plugin.

    apply plugin: 'contacts-base'
    apply plugin: 'contacts-pom'
    apply plugin: 'nebula-maven-publishing'

    contacts {
        'bobby@company.com' {
            roles 'owner'
        }
        'billy@company.com' {
            moniker 'Billy Bob'
        }
        'downstream@netflix.com' {
            role 'notify'
        }
    }

Would produce a section in the POM like this:

      <developers>
        <developer>
          <id>bob1978</id>
          <email>bobby@company.com</email>
          <roles>
            <role>owner</role>
          </roles>
        </developer>
        <developer>
          <name>Billy Bob</name>
          <email>billy@company.com</email>
        </developer>
        <developer>
          <email>downstream@netflix.com</email>
          <roles>
            <role>notify</role>
          </roles>
        </developer>
      </developers>

Mapping to POMs
---------------
POMs support very rich definitions for developers and contributors (https://maven.apache.org/pom.html#Developers). Though,
a quick analysis shows that these are rarely very complete. This plugin isn't targeting to support all the POM fields,
but this could be possible in the future. The primary goal of this plugin is to identify one or two primary contact of
the project, with the expectation that communication with developers/collaborators would be done via a mailing list and
not trolling the POM file.
