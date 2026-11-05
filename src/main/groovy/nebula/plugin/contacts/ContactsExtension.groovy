package nebula.plugin.contacts

import groovy.lang.DelegatesTo
import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Holds contacts for a project.
 */
class ContactsExtension {

    private final String emailPattern = /[_A-Za-z0-9-]+(.[_A-Za-z0-9-]+)*@[A-Za-z0-9]+(.[A-Za-z0-9]+)*(.[A-Za-z]{2,})/

    final LinkedHashMap<String, Contact> people
    boolean validateEmails

    private final Property<Boolean> parentInheritance

    ContactsExtension(LinkedHashMap<String, Contact> people, boolean validateEmails = false) {
        this.people = people
        this.validateEmails = validateEmails
        this.parentInheritance = null
    }

    /**
     * Creates an extension with provider-backed inheritance configuration.
     * The original constructors retain legacy inheritance without requiring Gradle services.
     */
    ContactsExtension(LinkedHashMap<String, Contact> people, boolean validateEmails, ObjectFactory objects) {
        this.people = people
        this.validateEmails = validateEmails
        this.parentInheritance = objects.property(Boolean).convention(true)
    }

    /**
     * Whether accessors include ancestor project contacts. Defaults to legacy inheritance.
     * Disable when a locally applied convention supplies this project's complete contacts.
     * Requires creation by the plugin or the constructor accepting ObjectFactory.
     */
    Property<Boolean> getInheritFromParent() {
        if (parentInheritance == null) {
            throw new IllegalStateException('Configuring inheritFromParent requires the ContactsExtension constructor accepting ObjectFactory')
        }
        return parentInheritance
    }

    @PackageScope
    boolean shouldInheritFromParent() {
        return parentInheritance == null || parentInheritance.get()
    }

    /**
     * Add a contact without configuration.
     * @param email the contact's email address
     * @return the Contact
     */
    Contact contact(String email) {
        return addPerson(email)
    }

    /**
     * Add or configure a contact using a Groovy closure.
     * @param email the contact's email address
     * @param closure configuration closure
     * @return the configured Contact
     */
    Contact contact(String email, @DelegatesTo(Contact) Closure closure) {
        return addPerson(email, closure)
    }

    /**
     * Add or configure a contact using an Action.
     * @param email the contact's email address
     * @param action configuration action
     * @return the configured Contact
     */
    Contact contact(String email, Action<Contact> action) {
        return addPerson(email, action)
    }

    def methodMissing(String name, args) {
        switch(args.length) {
            case 0:
                return addPerson(name)
            case 1:
                return addPerson(name, args[0])
            default:
                def names = (args.toList() + name) as Set
                return names.collect { addPerson(it) }
        }
    }

    def propertyMissing(String name) {
        addPerson(name)
    }

    Contact addPerson(String email) {
        if(validateEmails)
            validateEmail(email)
        def person = people.containsKey(email) ? people.get(email) : new Contact(email)
        people.put(email, person)
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
