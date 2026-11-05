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
package nebula.plugin.contacts

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ExplicitInheritanceSpec extends Specification {
    private static List rows(List<Contact> contacts) {
        contacts.collect { [it.email, it.moniker, it.github, it.twitter, it.slack, it.roles.sort()] }
    }

    def 'explicit layers preserve legacy order fields roles and local duplicate mutation'() {
        given:
        def root = ProjectBuilder.builder().build()
        def middle = ProjectBuilder.builder().withParent(root).build()
        def leaf = ProjectBuilder.builder().withParent(middle).build()
        [root, middle, leaf].each { it.plugins.apply(BaseContactsPlugin) }
        root.contacts {
            contact('shared@example.com') { moniker 'Root'; github 'root'; twitter 'root'; slack 'root'; role 'owner' }
            contact('inherited@example.com') { moniker 'Root only'; github 'root-only'; slack 'root-only'; role 'owner' }
            contact('root@example.com')
        }
        middle.contacts {
            contact('shared@example.com') { moniker ''; github ''; twitter 'middle'; slack 'middle'; role 'notify' }
            contact('inherited@example.com') { moniker 'Middle'; slack 'middle-only'; role 'notify' }
            contact('middle@example.com') { role 'notify' }
        }
        leaf.contacts {
            contact('shared@example.com') { moniker 'Leaf'; github 'leaf'; twitter 'leaf'; slack 'leaf'; role 'writer' }
            contact('leaf@example.com')
        }
        // Snapshot declarations before the legacy fold can mutate ancestor duplicates.
        def layers = [ContactLayer.of(middle.extensions.getByType(ContactsExtension).people), ContactLayer.of(root.extensions.getByType(ContactsExtension).people)]
        def explicit = ProjectBuilder.builder().withName('explicit').withParent(root).build()
        def plugin = explicit.plugins.apply(BaseContactsPlugin)
        leaf.extensions.getByType(ContactsExtension).people.each { key, value -> explicit.extensions.getByType(ContactsExtension).people[key] = BaseContactsPlugin.cloneContact(value) }
        explicit.extensions.getByType(ContactsExtension).inheritFrom(layers)

        when:
        def legacy = BaseContactsPlugin.getAllContacts(leaf)
        def actual = plugin.allContacts

        then:
        rows(actual) == rows(legacy)
        actual*.email == ['shared@example.com', 'leaf@example.com', 'inherited@example.com', 'middle@example.com', 'root@example.com']
        rows(actual)[0] == ['shared@example.com', 'Root', 'root', 'root', 'leaf', ['notify', 'owner', 'writer']]
        rows(actual)[2] == ['inherited@example.com', 'Root only', 'root-only', '', 'middle-only', ['notify', 'owner']]
        plugin.getContacts('owner')*.email == ['shared@example.com', 'leaf@example.com', 'inherited@example.com', 'root@example.com']
        plugin.getContacts('unknown')*.email == ['leaf@example.com', 'root@example.com']
        rows(BaseContactsPlugin.getContacts(explicit, 'notify')) == rows(plugin.getContacts('notify'))
        explicit.extensions.getByType(ContactsExtension).people['shared@example.com'].moniker == 'Root'
        explicit.extensions.getByType(ContactsExtension).people['shared@example.com'].roles == ['writer', 'notify', 'owner'] as Set
        layers[0].people['inherited@example.com'].moniker == 'Middle'
    }

    def 'layer snapshots detach maps contacts roles and all returned fields'() {
        given:
        def contact = new Contact('original@example.com')
        contact.moniker = 'Original'
        contact.github = 'github'
        contact.twitter = 'twitter'
        contact.slack = 'slack'
        contact.role('owner')
        def map = [key: contact]
        def layer = ContactLayer.of(map)

        when:
        contact.email = 'changed@example.com'
        contact.moniker = 'Changed'
        contact.github = contact.twitter = contact.slack = 'changed'
        contact.roles.clear()
        map.clear()
        def first = layer.people
        first.key.email = 'mutated@example.com'
        first.key.roles.add('mutated')
        first.clear()

        then:
        layer.people.keySet().toList() == ['key']
        rows(layer.people.values().toList()) == [['original@example.com', 'Original', 'github', 'twitter', 'slack', ['owner']]]
    }

    def 'explicit empty inheritance differs from an unmigrated child'() {
        given:
        def root = ProjectBuilder.builder().build()
        root.plugins.apply(BaseContactsPlugin)
        root.extensions.getByType(ContactsExtension).contact('root@example.com')
        def child = ProjectBuilder.builder().withParent(root).build()
        child.plugins.apply(BaseContactsPlugin)

        expect:
        !child.extensions.getByType(ContactsExtension).explicitInheritance
        BaseContactsPlugin.getAllContacts(child)*.email == ['root@example.com']

        when:
        child.extensions.getByType(ContactsExtension).inheritFrom([])

        then:
        child.extensions.getByType(ContactsExtension).explicitInheritance
        BaseContactsPlugin.getAllContacts(child).empty
    }

    def 'providers stay lazy and instance snapshots differ from fresh static reads'() {
        given:
        def project = ProjectBuilder.builder().build()
        def plugin = project.plugins.apply(BaseContactsPlugin)
        def ancestor = new Contact('ancestor@example.com')
        ancestor.moniker = 'First'
        def reads = 0
        project.extensions.getByType(ContactsExtension).contact('local@example.com')
        project.extensions.getByType(ContactsExtension).inheritFrom(project.providers.provider {
            reads++
            [ContactLayer.of([(ancestor.email): ancestor])]
        })

        expect:
        reads == 0
        project.extensions.getByType(ContactsExtension).explicitInheritance

        when:
        def cached = plugin.allContacts
        ancestor.moniker = 'Second'
        ancestor.role('owner')
        project.extensions.getByType(ContactsExtension).contact('late@example.com')
        def fresh = BaseContactsPlugin.getAllContacts(project)

        then:
        reads == 2
        cached*.email == ['local@example.com', 'ancestor@example.com']
        cached.last().moniker == 'First'
        cached.last().roles.empty
        fresh*.email == ['local@example.com', 'late@example.com', 'ancestor@example.com']
        fresh.last().moniker == 'Second'
        plugin.allContacts.is(cached)
        reads == 2

        when:
        fresh.first().role('copy-only')
        fresh.last().moniker = 'Copy'
        cached.last().role('cached-only')

        then:
        project.extensions.getByType(ContactsExtension).people['local@example.com'].roles.empty
        ancestor.moniker == 'Second'
        !BaseContactsPlugin.getAllContacts(project).last().roles.contains('cached-only')
        plugin.getContacts('cached-only').last().roles.contains('cached-only')
    }

    def 'fixed list selection snapshots on entry and static reads keep local merge effects'() {
        given:
        def project = ProjectBuilder.builder().build()
        def plugin = project.plugins.apply(BaseContactsPlugin)
        project.extensions.getByType(ContactsExtension).contact('duplicate@example.com') { moniker 'Local'; role 'writer' }
        def ancestor = new Contact('duplicate@example.com')
        ancestor.moniker = 'Ancestor'
        ancestor.role('owner')
        def list = [ContactLayer.of([(ancestor.email): ancestor])]
        project.extensions.getByType(ContactsExtension).inheritFrom(list)
        list.clear()

        when:
        def cached = plugin.allContacts
        project.extensions.getByType(ContactsExtension).inheritFrom([])

        then:
        project.extensions.getByType(ContactsExtension).people['duplicate@example.com'].moniker == 'Ancestor'
        rows(BaseContactsPlugin.getAllContacts(project)) == rows(cached)

        when:
        project.extensions.getByType(ContactsExtension).contact('duplicate@example.com') { moniker 'Later'; role 'later' }

        then:
        plugin.allContacts.first().moniker == 'Ancestor'
        BaseContactsPlugin.getAllContacts(project).first().moniker == 'Later'
        !cached.first().roles.contains('later')
    }

    def 'an absent explicit provider fails rather than dropping inherited ownership'() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply(BaseContactsPlugin)
        project.extensions.getByType(ContactsExtension).inheritFrom(project.providers.provider { null })

        when:
        BaseContactsPlugin.getAllContacts(project)

        then:
        thrown(IllegalStateException)
    }

    def 'email validation stays local and inherited declarations retain their original validation policy'() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply(BaseContactsPlugin)
        project.extensions.getByType(ContactsExtension).validateEmails = true
        def invalid = new Contact('not an email')
        project.extensions.getByType(ContactsExtension).inheritFrom([ContactLayer.of(['not an email': invalid])])

        expect:
        BaseContactsPlugin.getAllContacts(project)*.email == ['not an email']

        when:
        project.extensions.getByType(ContactsExtension).contact('invalid local')

        then:
        def e = thrown(ContactsPluginException)
        e.message == 'invalid local is not a valid email'
    }
}
