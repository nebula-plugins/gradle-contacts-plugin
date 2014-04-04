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

import nebula.test.IntegrationSpec

class ContactsPluginLauncherSpec extends IntegrationSpec {

    def pomLocation = 'build/publications/mavenJava/pom-default.xml'

    def 'look in pom'() {

        buildFile << """
            buildscript {
                repositories { jcenter() }
                dependencies { classpath 'com.netflix.nebula:nebula-publishing-plugin:1.9.6' }
            }
            ${applyPlugin(ContactsPlugin)}

            apply plugin: 'nebula-publishing'
            contacts {
                owner {
                    email 'mickey@disney.com'
                    name 'Mickey Mouse'
                }
            }
            """.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForMavenJavaPublication')

        then: 'pom exists'
        fileExists(pomLocation)
        def pom = new XmlSlurper().parse( file(pomLocation) )

        then: 'developer section is filled in'
        def devs = pom.developers.developer
        devs.size() == 1
        def dev = devs[0]
        dev.email.text() == 'mickey@disney.com'
        dev.name.text() == 'Mickey Mouse'

    }
}
