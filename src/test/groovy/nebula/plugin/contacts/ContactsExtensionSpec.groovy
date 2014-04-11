package nebula.plugin.contacts

import org.gradle.api.NamedDomainObjectContainer
import spock.lang.Specification

class ContactsExtensionSpec extends Specification {
    def 'create dynamic via email'() {
        LinkedHashMap<String, Contact> people = Mock()
        ContactsExtension extension = new ContactsExtension(people)
        def closure = {}

        when:
        extension.'mickey@disney.com'

        then:
        1 * people.put('mickey@disney.com', { it.email == 'mickey@disney.com' } )

        when:
        extension.'minnie@disney.com' closure

        then:
        1 * people.put('minnie@disney.com', _ as Contact)
    }

    def 'configure from closure'() {
        LinkedHashMap<String,Contact> people = Mock()
        ContactsExtension extension = new ContactsExtension(people)
        Closure closure = {
            'mickey@disney.com' {}
        }

        when:
        closure.delegate = extension
        closure.call()

        then:
        1 * people.put('mickey@disney.com', _)

    }

    def 'create multiple people'() {
        LinkedHashMap<String,Contact> people = Mock()
        ContactsExtension extension = new ContactsExtension(people)

        when:
        extension.'mickey@disney.com' 'minnie@disney.com', 'goofy@disney.com' // Odd calling syntax, but in a closure it'll be cleaner

        then:
        1 * people.put('mickey@disney.com', _)
        1 * people.put('minnie@disney.com', _)
        1 * people.put('goofy@disney.com', _)
    }
}
