/*
 * Copyright 2014 Netflix, Inc.
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

}
