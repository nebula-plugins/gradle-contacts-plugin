package nebula.plugin.contacts

import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectList
import org.gradle.api.internal.DefaultNamedDomainObjectCollection
import org.gradle.util.ConfigureUtil

/**
 * Holds Person for a project.
 * TODO repeat a name and guarantee uniqueness
 */
class ContactsExtension {
    final LinkedHashMap<String, Contact> people

    ContactsExtension(LinkedHashMap<String, Contact> people) {
        this.people = people
    }

    /**
     * Suck up all dynamic calls into the people container. This gives us the leeway to have other methods in the extension
     * without requiring another level of indirection.
     * @param name email address
     * @param args an empty array or a single Closure
     * @return Contact
     */
    def methodMissing(String name, args) {
        switch(args.length) {
            case 0:
                def added = addPerson(name)
                return added
            case 1:
                if (args[0] instanceof Closure) {
                    def added = addPerson(name, args[0])
                    return added
                }
                // Fall through
            default:
                def names = (args.toList() + name) as Set
                def people = names.collect {
                    addPerson(it)
                }
                return people
        }
    }

    def propertyMissing(String name) {
        // Act of asking for a name will create one
        addPerson(name)
    }

    def addPerson(String email) {
        // TODO Validate email address
        def person = people.containsKey(email) ? people.get(email) : new Contact(email)
        people.put(email, person) // Redundant if already there, just trying to follow model below
        return person
    }

    def addPerson(String email, Closure closure) {
        // TODO Validate email address
        def person = people.containsKey(email) ? people.get(email).clone() : new Contact(email)
        ConfigureUtil.configure(closure, person)
        people.put(email, person)
        return person
    }

}
