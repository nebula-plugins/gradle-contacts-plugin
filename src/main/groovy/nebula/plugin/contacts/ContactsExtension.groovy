package nebula.plugin.contacts

import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

/**
 * Holds Person for a project.
 * TODO repeat a name and guarantee uniqueness
 */
class ContactsExtension {

    private final String emailPattern = /[_A-Za-z0-9-]+(.[_A-Za-z0-9-]+)*@[A-Za-z0-9]+(.[A-Za-z0-9]+)*(.[A-Za-z]{2,})/

    NamedDomainObjectContainer<Contact> peopleContainer
    final LinkedHashMap<String, Contact> people
    boolean validateEmails

    ContactsExtension(LinkedHashMap<String, Contact> people, boolean validateEmails = false) {
        this.people = people
        this.validateEmails = validateEmails
    }

    /**
     * Suck up all dynamic calls into the people container. This gives us the leeway to have other methods in the extension
     * without requiring another level of indirection.
     * @param name email address
     * @param args an empty array or a single Closure/Action
     * @return Contact
     */
    def methodMissing(String name, args) {
        switch(args.length) {
            case 0:
                return addPerson(name)
            case 1:
                // Let Groovy's method dispatch choose the right overload (Closure vs Action)
                return addPerson(name, args[0])
            default:
                // Handle multiple email addresses: 'mickey@disney.com' 'minnie@disney.com', 'goofy@disney.com'
                def names = (args.toList() + name) as Set
                return names.collect { addPerson(it) }
        }
    }

    def propertyMissing(String name) {
        // Act of asking for a name will create one
        addPerson(name)
    }

    Contact addPerson(String email) {
        if(validateEmails)
            validateEmail(email)
        def person = people.containsKey(email) ? people.get(email) : new Contact(email)
        people.put(email, person) // Redundant if already there, just trying to follow model below
        return person
    }

    /**
     * Add or configure a contact using a Groovy closure (for Groovy DSL).
     * @param email the contact's email address
     * @param closure configuration closure with the Contact as delegate
     * @return the configured Contact
     */
    Contact addPerson(String email, @DelegatesTo(Contact) Closure closure) {
        if(validateEmails)
            validateEmail(email)
        def person = people.containsKey(email) ? BaseContactsPlugin.cloneContact(people.get(email)) : new Contact(email)
        closure.delegate = person
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        people.put(email, person)
        return person
    }

    /**
     * Add or configure a contact using an Action (for Kotlin DSL and explicit Action usage).
     * @param email the contact's email address
     * @param action configuration action
     * @return the configured Contact
     */
    Contact addPerson(String email, Action<Contact> action) {
        if(validateEmails)
            validateEmail(email)
        def person = people.containsKey(email) ? BaseContactsPlugin.cloneContact(people.get(email)) : new Contact(email)
        action.execute(person)
        people.put(email, person)
        return person
    }

    private validateEmail(String email) {
        if(!(email ==~  emailPattern)) {
            throw new ContactsPluginException("$email is not a valid email")
        }
    }

}
