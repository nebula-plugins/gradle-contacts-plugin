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
 * Provides contacts extension and accessor methods to retrieve contacts by role.
 */
class BaseContactsPlugin implements Plugin<Project> {
    private Project project

    @Lazy
    private volatile List<Contact> cachedResolvedContacts = { resolveContactsInternal(project) }()

    @Override
    void apply(Project project) {
        this.project = project

        def people = new LinkedHashMap<String, Contact>()
        def extension = project.extensions.create('contacts', ContactsExtension, people)

        project.ext.contacts = { String... args ->
            args.collect {
                extension.addPerson(it)
            }
        }
    }

    /**
     * @deprecated Use project.extensions.getByType(ContactsExtension) instead.
     */
    @Deprecated
    ContactsExtension getExtension() {
        return project.extensions.getByType(ContactsExtension)
    }

    /**
     * @deprecated Use static methods getAllContacts(Project) and getContacts(Project, String) instead.
     */
    @Deprecated
    Project getProject() {
        return project
    }

    private static List<Contact> resolveContacts(Project project) {
        return resolveContactsInternal(project)
    }

    private static List<Contact> resolveContactsInternal(Project project) {
        Project thisProject = project
        List<Contact> contacts = []
        while (thisProject != null) {
            ContactsExtension contactsPath = thisProject.extensions.findByType(ContactsExtension)
            if (contactsPath) {
                contacts = addToContacts(contacts, contactsPath.people)
            }
            thisProject = thisProject.parent
        }

        return contacts.collect { Contact original ->
            return cloneContact(original)
        }
    }

    static Contact cloneContact(Contact original) {
        Contact cloned = new Contact(original.email)
        cloned.moniker = original.moniker
        cloned.github = original.github
        cloned.twitter = original.twitter
        cloned.slack = original.slack
        cloned.roles = new HashSet<>(original.roles)
        return cloned
    }

    List<Contact> getContacts(String role) {
        return cachedResolvedContacts.findAll { Contact contact ->
            contact.roles.isEmpty() || contact.roles.contains(role)
        }
    }

    List<Contact> getAllContacts() {
        return cachedResolvedContacts
    }

    static List<Contact> getContacts(Project project, String role) {
        return resolveContacts(project).findAll { Contact contact ->
            contact.roles.isEmpty() || contact.roles.contains(role)
        }
    }

    static List<Contact> getAllContacts(Project project) {
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
