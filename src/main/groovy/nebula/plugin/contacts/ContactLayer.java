/*
 * Copyright 2026 Netflix, Inc.
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
package nebula.plugin.contacts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An ordered, detached snapshot of one ancestor's contact declarations.
 * Construct from independently loaded data or a locally applied convention, not
 * by reading another project's extension. No mutable source state is retained.
 */
public final class ContactLayer implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<Entry> entries;

    private ContactLayer(Map<String, Contact> people) {
        var snapshots = new ArrayList<Entry>();
        people.forEach((key, contact) -> snapshots.add(new Entry(key, contact)));
        entries = Collections.unmodifiableList(snapshots);
    }

    /** Snapshots map order, map keys, all contact fields and roles at this call. */
    public static ContactLayer of(Map<String, Contact> people) {
        return new ContactLayer(people);
    }

    /** Returns fresh contacts and role sets; mutations cannot change this layer. */
    public LinkedHashMap<String, Contact> getPeople() {
        var people = new LinkedHashMap<String, Contact>();
        for (var entry : entries) {
            var contact = new Contact(entry.email);
            contact.setMoniker(entry.moniker);
            contact.setGithub(entry.github);
            contact.setTwitter(entry.twitter);
            contact.setSlack(entry.slack);
            contact.setRoles(new LinkedHashSet<>(entry.roles));
            people.put(entry.key, contact);
        }
        return people;
    }

    private static final class Entry implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String key;
        private final String email;
        private final String moniker;
        private final String github;
        private final String twitter;
        private final String slack;
        private final Set<String> roles;

        private Entry(String key, Contact contact) {
            this.key = key;
            email = contact.getEmail();
            moniker = contact.getMoniker();
            github = contact.getGithub();
            twitter = contact.getTwitter();
            slack = contact.getSlack();
            roles = Collections.unmodifiableSet(new LinkedHashSet<>(contact.getRoles()));
        }
    }
}
