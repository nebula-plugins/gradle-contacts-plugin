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

import nebula.plugin.publishing.NebulaPublishingPlugin
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Take the contacts and apply them to the POM file
 */
class PomDevelopersPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        NebulaPublishingPlugin plugin
        def pomConfig = {
            inceptionYear '2014'

            developers {
                developer {
                    id 'quidryan'
                    name 'Justin Ryan'
                    email 'jryan@netflix.com'
                    roles{
                        role 'Developer'
                    }
                    timezone '-8'
                }
            }
        }

        project.plugins.withType(NebulaBaseMavenPublishingPlugin) {
            withMavenPublication {
                pom.withXml {
                    asNode().children().last() + pomConfig
                }
            }
        }
        // TODO Inherit from root project or be able to apply to the root project
    }
}
