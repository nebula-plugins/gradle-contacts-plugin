package nebula.plugin.contacts

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

    def 'merge values on consequence calls'() {
        LinkedHashMap<String, Contact> people = new LinkedHashMap<>()
        ContactsExtension extension = new ContactsExtension(people)

        Closure closure = {
            'mickey@disney.com' {
                moniker 'Mickey'
                github 'mmouse'
                slack 'mmouse'
            }
            'mickey@disney.com' {
                moniker 'Mickey Mouse'
                twitter 'mmouse1928'
                slack 'mmouse1928'
            }
        }

        when:
        closure.delegate = extension
        closure.call()

        then:
        people.keySet().size() == 1
        def mickey = people['mickey@disney.com']
        mickey.email == 'mickey@disney.com'
        mickey.moniker == 'Mickey Mouse'
        mickey.github == 'mmouse'
        mickey.twitter == 'mmouse1928'
        mickey.slack == 'mmouse1928'
    }
}
