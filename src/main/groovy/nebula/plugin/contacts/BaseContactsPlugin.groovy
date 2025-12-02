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
    // Store project reference for backwards compatibility with instance method API
    // This is set once during apply() and never modified (effectively immutable)
    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        def people = new LinkedHashMap<String, Contact>()

        // Create and install the extension object
        def extension = project.extensions.create('contacts', ContactsExtension, people)

        // Helper for adding contacts without calling in a closure
        project.ext.contacts = { String... args ->
            args.collect {
                extension.addPerson(it)
            }
        }
    }

    /**
     * Get the contacts extension for this plugin's project.
     * @return the ContactsExtension
     * @deprecated Access the extension directly via project.extensions.getByType(ContactsExtension) instead.
     * This getter will be removed in a future version.
     */
    @Deprecated
    ContactsExtension getExtension() {
        return project.extensions.getByType(ContactsExtension)
    }

    /**
     * Get the project this plugin was applied to.
     * @return the Project
     * @deprecated Direct access to the project field is discouraged. Use static methods like
     * getAllContacts(Project) and getContacts(Project, String) instead. This getter will be removed in a future version.
     */
    @Deprecated
    Project getProject() {
        return project
    }

    private static List<Contact> resolveContacts(Project project) {
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

        return contacts.collect { Contact original ->
            return cloneContact(original)
        }
    }

    /**
     * Clone a Contact object manually
     * @param original the original Contact to clone
     * @return cloned Contact
     */
    static Contact cloneContact(Contact original) {
        Contact cloned = new Contact(original.email)
        cloned.moniker = original.moniker
        cloned.github = original.github
        cloned.twitter = original.twitter
        cloned.slack = original.slack
        cloned.roles = new HashSet<>(original.roles)
        return cloned
    }

    /**
     * Return Contacts (clones) which match a role, or for users that don't have a role
     * @param role the role to filter by
     * @return matching contacts
     */
    List<Contact> getContacts(String role) {
        return getContacts(project, role)
    }

    /**
     * Return all Contacts (clones) no matter the role
     * @return all contacts
     */
    List<Contact> getAllContacts() {
        return getAllContacts(project)
    }

    /**
     * Static method to get Contacts matching a role from any project.
     * Prefer this over the instance method for better testability.
     * @param project the project to resolve contacts from
     * @param role the role to filter by
     * @return matching contacts
     */
    static List<Contact> getContacts(Project project, String role) {
        // Objects are already cloned before we see it.
        return resolveContacts(project).findAll { Contact contact ->
            contact.roles.isEmpty() || contact.roles.contains(role)
        }
    }

    /**
     * Static method to get all Contacts from any project.
     * Prefer this over the instance method for better testability.
     * @param project the project to resolve contacts from
     * @return all contacts
     */
    static List<Contact> getAllContacts(Project project) {
        // Objects are already cloned before we see it.
        return resolveContacts(project)
    }

    private static List<Contact> addToContacts(List<Contact> contacts, Map<String, Contact> people) {
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
