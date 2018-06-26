package nebula.plugin.contacts

import groovy.transform.AutoClone
import groovy.transform.Canonical
import org.gradle.api.Named

/**
 * Roll-up features needed for Contact
 */
@Canonical
@AutoClone
class Contact implements Named {
    final String email

    /**
     * Aka name, but Named interface won't let us call it that
     */
    String moniker

    /**
     * Github username
     */
    String github

    /**
     * Twitter handle
     */
    String twitter

    Set<String> roles = [] as Set

    Contact(String email) {
        this.email = email
    }

    @Override
    String getName() {
        return email
    }

    void role(String singleRoleName) {
        roles << singleRoleName
    }

    void roles(String... roleNames) {
        roleNames.each {
            role(it)
        }
    }

    // Temporary until we can find a annotation to do this for us
    void moniker(String moniker) {
        if(!moniker)
            return
        this.moniker = moniker
    }

    void github(String github) {
        if(!github)
            return
        this.github = github
    }

    void twitter(String twitter) {
        if(!twitter)
            return
        this.twitter = twitter
    }
}
