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
        rootContacts.inheritFromParent = false
        rootContacts.contact('root@example.com')
        def child = ProjectBuilder.builder().withParent(root).build()
        def plugin = child.plugins.apply(BaseContactsPlugin)
        def contacts = child.extensions.getByType(ContactsExtension)
        contacts.contact('child@example.com')

        expect:
        contacts.inheritFromParent
        BaseContactsPlugin.getAllContacts(child)*.email == ['child@example.com', 'root@example.com']

        when:
        contacts.inheritFromParent = false

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
            inheritFromParent = false
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
        contacts.inheritFromParent = false
        contacts.contact('local@example.com')
        def first = plugin.allContacts

        when:
        contacts.contact('late@example.com')
        contacts.inheritFromParent = true

        then:
        plugin.allContacts.is(first)
        plugin.allContacts*.email == ['local@example.com']
        BaseContactsPlugin.getAllContacts(child)*.email == ['local@example.com', 'late@example.com', 'root@example.com']
    }

    def 'local folding retains observable duplicate merge effects and closest slack'() {
        given:
        def project = ProjectBuilder.builder().build()
        def plugin = project.plugins.apply(BaseContactsPlugin)
        def contacts = project.extensions.getByType(ContactsExtension)
        contacts.inheritFromParent = false
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
            inheritFromParent = false
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
