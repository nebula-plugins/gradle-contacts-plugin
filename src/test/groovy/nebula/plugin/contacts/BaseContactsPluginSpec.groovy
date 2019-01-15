/*
 * Copyright 2014-2019 Netflix, Inc.
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

import nebula.test.PluginProjectSpec
import org.gradle.testfixtures.ProjectBuilder

class BaseContactsPluginSpec extends PluginProjectSpec {
    @Override
    String getPluginName() {
        'nebula.contacts'
    }

    def 'extension accessible'() {

        when:
        def plugin = project.plugins.apply(BaseContactsPlugin)

        project.contacts {
            'club@disney.com' {}
        }

        then:
        plugin.extension.people.size() == 1
        plugin.extension.people.values().any { it.email == 'club@disney.com' }

        when:
        project.contacts 'minnie@disney.com', 'mickey@disney.com'

        then:
        plugin.extension.people.size() == 3
        plugin.extension.people.values().any { it.email == 'minnie@disney.com' }
        plugin.extension.people.values().any { it.email == 'mickey@disney.com' }

    }

    def 'Plugin does not fail with invalid emails if validation is disabled - not configured'() {

        when:
        def plugin = project.plugins.apply(BaseContactsPlugin)

        project.contacts {
            'not an email' {}
        }

        then:
        plugin.extension.people.size() == 1
        plugin.extension.people.values().any { it.email == 'not an email' }

        when:
        project.contacts 'minnie@disney.com', 'mickey@disney.com'

        then:
        plugin.extension.people.size() == 3
        plugin.extension.people.values().any { it.email == 'minnie@disney.com' }
        plugin.extension.people.values().any { it.email == 'mickey@disney.com' }
    }

    def 'Plugin does not fail with invalid emails if validation is disabled'() {

        when:
        def plugin = project.plugins.apply(BaseContactsPlugin)

        project.contacts {
            validateEmails = false
            'not an email' {}
        }

        then:
        plugin.extension.people.size() == 1
        plugin.extension.people.values().any { it.email == 'not an email' }

        when:
        project.contacts 'minnie@disney.com', 'mickey@disney.com'

        then:
        plugin.extension.people.size() == 3
        plugin.extension.people.values().any { it.email == 'minnie@disney.com' }
        plugin.extension.people.values().any { it.email == 'mickey@disney.com' }
    }

    def 'plugin fails if email validation is enabled and not valid email'() {

        when:
        def plugin = project.plugins.apply(BaseContactsPlugin)

        project.contacts {
            validateEmails = true
            'not an email' {}
        }

        then:
        ContactsPluginException e = thrown(ContactsPluginException)
        e.message == 'not an email is not a valid email'
    }

    def 'collects all levels of multiproject'() {
        def sub = ProjectBuilder.builder().withName('sub').withProjectDir(new File(projectDir, 'sub')).withParent(project).build()
        project.subprojects.add(sub)

        when:
        project.plugins.apply(BaseContactsPlugin)
        project.contacts 'mickey@disney.com'
        def apply = sub.plugins.apply(BaseContactsPlugin)
        sub.contacts 'minnie@disney.com'
        def allContacts = apply.getAllContacts()

        then:
        allContacts.any { it.name == 'mickey@disney.com' }
        allContacts.any { it.name == 'minnie@disney.com' }
    }

    def 'filter by role'() {
        when:
        def apply = project.plugins.apply(BaseContactsPlugin)
        project.contacts {
            'mickey@disney.com' {
            }
            'minnie@disney.com' {
                role 'host'
            }
            'daffy@disney.com' {
                role 'guest'
            }
            'goofy@disney.com' {
                role 'guest'
            }
        }

        then:
        apply.getAllContacts().size() == 4
        (apply.getContacts('guest') as Set).size() == 3
        (apply.getContacts('host') as Set).size() == 2
        (apply.getContacts('') as Set).size() == 1
    }

    def 'collects all levels of multiproject - merges existing contacts'() {
        def sub = ProjectBuilder.builder().withName('sub').withProjectDir(new File(projectDir, 'sub')).withParent(project).build()
        project.subprojects.add(sub)

        when:
        project.plugins.apply(BaseContactsPlugin)
        project.contacts {
            'mickey@disney.com' {
            }
            'goofy@disney.com' {
                github 'goofy'
            }
        }
        def apply = sub.plugins.apply(BaseContactsPlugin)
        sub.contacts {
            'mickey@disney.com' {
                role 'guest'
                github 'mickey'
                twitter 'mickey'
            }
            'goofy@disney.com' {
                role 'guest'
                twitter 'goofy'
            }
        }

        and:
        List<Contact> contacts = apply.getAllContacts()

        then:
        contacts.size() == 2
        (apply.getContacts('guest') as Set).size() == 2
        contacts.find { it.github == 'mickey' && it.twitter == 'mickey' && it.email == 'mickey@disney.com' }
        contacts.find { it.github == 'goofy' && it.twitter == 'goofy' && it.email == 'goofy@disney.com' }
    }
}
