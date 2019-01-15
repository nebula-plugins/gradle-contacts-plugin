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

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Provide extension onto a project, to configure contacts. Also provide accessor methods to get contacts given a role.
 */
class BaseContactsPlugin implements Plugin<Project> {
    ContactsExtension extension
    Project project

    @Override
    void apply(Project project) {
        this.project = project

        def people = new LinkedHashMap<String, Contact>()

        // Create and install the extension object
        extension = project.extensions.create('contacts', ContactsExtension, people)

        // Helper for adding contacts without calling in a closure
        project.ext.contacts = { String... args ->
            args.collect {
                extension.addPerson(it)
            }
        }
    }

    private List<Contact> resolveContacts() {
        Project thisProject = project
        List<Contact> contacts = []
        // TODO Probably should reverse the order so that root project contacts come first
        while (thisProject != null) {
            ContactsExtension contactsPath = thisProject.extensions.findByType(ContactsExtension)
            if (contactsPath) {
                // Ergo he exists as a developer
                contacts = addToContacts(contacts, contactsPath.people)
            }
            thisProject = thisProject.parent // Root Project will have a null parent
        }

        return contacts.collect {
            it.clone()
        }
    }

    /**
     * Return Contacts (clones) which match a role, or for users that don't have a role
     * @param role
     * @return matching contacts
     */
    List<Contact> getContacts(String role) {
        // Objects are already cloned before we see it.
        return resolveContacts().findAll { Contact contact ->
            contact.roles.isEmpty() || contact.roles.contains(role)
        }
    }

    /**
     * Return all Contacts (clones) no matter to role
     * @param role
     * @return matching contacts
     */
    List<Contact> getAllContacts() {
        // Objects are already cloned before we see it.
        return resolveContacts()
    }

    private List<Contact> addToContacts(List<Contact> contacts, Map<String, Contact> people) {
        people.each { email, contact ->
            Contact existingContact = contacts.find { it.email == email }
            if(existingContact) {
                existingContact.moniker(contact.moniker)
                existingContact.twitter(contact.twitter)
                existingContact.github(contact.github)
                existingContact.roles(contact.getRoles() as String[])
            } else {
                contacts += contact
            }
        }
        return contacts
    }
}
