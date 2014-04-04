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
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Apply all the relevant contact plugins.
 * TBD How do plugins define which "role" they use?
 */
class ContactsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        NebulaPublishingPlugin plugin

        // TODO Inherit from root project or be able to apply to the root project
    }
}
