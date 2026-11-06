/*
 * Copyright 2026 Netflix, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package nebula.plugin.contacts

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class LocalContactsSpec extends Specification {
    def 'inheritance defaults to true and only the consuming project controls its resolver'() {
        given:
        def root = ProjectBuilder.builder().build()
        root.plugins.apply(BaseContactsPlugin)
        def rootContacts = root.extensions.getByType(ContactsExtension)
        rootContacts.inheritFromParent.set(false)
        rootContacts.contact('root@example.com')
        def child = ProjectBuilder.builder().withParent(root).build()
        def plugin = child.plugins.apply(BaseContactsPlugin)
        def contacts = child.extensions.getByType(ContactsExtension)
        contacts.contact('child@example.com')

        expect:
        contacts.inheritFromParent.get()
        BaseContactsPlugin.getAllContacts(child)*.email == ['child@example.com', 'root@example.com']

        when:
        contacts.inheritFromParent.set(false)

        then:
        plugin.allContacts*.email == ['child@example.com']
        BaseContactsPlugin.getAllContacts(child)*.email == ['child@example.com']
        BaseContactsPlugin.getContacts(child, 'owner')*.email == ['child@example.com']
    }

    def 'local mode preserves every field role filtering and defensive copies'() {
        given:
        def project = ProjectBuilder.builder().build()
        def plugin = project.plugins.apply(BaseContactsPlugin)
        project.contacts {
            inheritFromParent.set(false)
            contact('owner@example.com') {
                moniker 'Owner'; github 'github'; twitter 'twitter'; slack 'slack'; roles 'owner', 'notify'
            }
            contact('everyone@example.com')
            contact('writer@example.com') { role 'writer' }
        }
        def extension = project.extensions.getByType(ContactsExtension)

        expect:
        plugin.getContacts('owner')*.email == ['owner@example.com', 'everyone@example.com']
        plugin.getContacts('missing')*.email == ['everyone@example.com']
        plugin.allContacts.first().with { [email, moniker, github, twitter, slack, roles.sort()] } ==
            ['owner@example.com', 'Owner', 'github', 'twitter', 'slack', ['notify', 'owner']]

        when:
        def fresh = BaseContactsPlugin.getAllContacts(project)
        fresh.first().with { email = 'copy@example.com'; moniker = github = twitter = slack = 'copy'; roles.clear() }
        fresh.clear()
        plugin.allContacts.first().roles.add('cached-only')

        then:
        extension.people['owner@example.com'].with { [email, moniker, github, twitter, slack, roles.sort()] } ==
            ['owner@example.com', 'Owner', 'github', 'twitter', 'slack', ['notify', 'owner']]
        !BaseContactsPlugin.getAllContacts(project).first().roles.contains('cached-only')
    }

    def 'instance access caches the first flag choice and static access resolves the current choice'() {
        given:
        def root = ProjectBuilder.builder().build()
        root.plugins.apply(BaseContactsPlugin)
        root.extensions.getByType(ContactsExtension).contact('root@example.com')
        def child = ProjectBuilder.builder().withParent(root).build()
        def plugin = child.plugins.apply(BaseContactsPlugin)
        def contacts = child.extensions.getByType(ContactsExtension)
        def inherit = child.objects.property(Boolean).convention(false)
        def reads = 0
        contacts.inheritFromParent.convention(child.providers.provider { reads++; inherit.get() })
        contacts.contact('local@example.com')

        expect:
        reads == 0

        when:
        def first = plugin.allContacts

        then:
        reads > 0
        first*.email == ['local@example.com']

        when:
        contacts.contact('late@example.com')
        inherit.set(true)
        def readsBeforeCachedAccess = reads

        then:
        plugin.allContacts.is(first)
        plugin.allContacts*.email == ['local@example.com']
        reads == readsBeforeCachedAccess
        BaseContactsPlugin.getAllContacts(child)*.email == ['local@example.com', 'late@example.com', 'root@example.com']
    }

    def 'explicit values override a provider convention and unset restores the convention'() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply(BaseContactsPlugin)
        def contacts = project.extensions.getByType(ContactsExtension)
        def reads = 0
        contacts.inheritFromParent.convention(project.providers.provider { reads++; true })
        contacts.inheritFromParent.set(false)

        expect:
        !contacts.inheritFromParent.get()
        reads == 0

        when:
        contacts.inheritFromParent.unset()

        then:
        contacts.inheritFromParent.get()
        reads > 0
    }

    def 'original constructors retain legacy resolution without Gradle services'() {
        given:
        def root = ProjectBuilder.builder().build()
        root.plugins.apply(BaseContactsPlugin)
        root.extensions.getByType(ContactsExtension).contact('root@example.com')
        def child = ProjectBuilder.builder().withParent(root).build()
        def people = new LinkedHashMap<String, Contact>()
        def contacts = validate == null ? new ContactsExtension(people) : new ContactsExtension(people, validate)
        child.extensions.add('contacts', contacts)
        contacts.contact('child@example.com')

        expect:
        contacts.people.is(people)
        contacts.validateEmails == (validate ?: false)
        BaseContactsPlugin.getAllContacts(child)*.email == ['child@example.com', 'root@example.com']

        where:
        validate << [null, true]
    }

    def 'local folding retains observable duplicate merge effects and closest slack'() {
        given:
        def project = ProjectBuilder.builder().build()
        def plugin = project.plugins.apply(BaseContactsPlugin)
        def contacts = project.extensions.getByType(ContactsExtension)
        contacts.inheritFromParent.set(false)
        def first = contacts.contact('original-key@example.com') {
            moniker 'First'; slack 'first'; role 'writer'
        }
        first.email = 'duplicate@example.com'
        contacts.contact('duplicate@example.com') {
            moniker 'Second'; github 'second'; slack 'second'; role 'owner'
        }

        when:
        def resolved = plugin.allContacts

        then:
        resolved*.email == ['duplicate@example.com']
        resolved.first().with { [moniker, github, slack, roles.sort()] } == ['Second', 'second', 'first', ['owner', 'writer']]
        first.moniker == 'Second'
        first.roles == ['writer', 'owner'] as Set
        !resolved.first().is(first)
    }

    def 'validation and configuring overloads retain local DSL behavior'() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply(BaseContactsPlugin)
        project.contacts {
            inheritFromParent.set(false)
            validateEmails = true
            contact('owner@example.com') { moniker 'Owner'; slack 'first' }
            contact('owner@example.com') { github 'owner'; slack 'second' }
        }
        def contacts = project.extensions.getByType(ContactsExtension)

        expect:
        contacts.people['owner@example.com'].with { [moniker, github, slack] } == ['Owner', 'owner', 'second']

        when:
        contacts.contact('invalid email')

        then:
        def invalid = thrown(ContactsPluginException)
        invalid.message == 'invalid email is not a valid email'
    }
}
