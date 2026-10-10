/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.camel.examples.restopenapi;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

@Singleton
@Named("userService")
@Requires(env = "example-rest-openapi")
public class UserServiceImpl implements UserService {

    private final Map<Integer, User> users = new TreeMap<>();

    public UserServiceImpl() {
        users.put(1, new User(1, "John Coltrane"));
        users.put(2, new User(2, "Miles Davis"));
        users.put(3, new User(3, "Sonny Rollins"));
    }

    @Override
    public User findUser(Integer id) {
        return users.get(id);
    }

    @Override
    public Collection<User> findUsers() {
        return users.values();
    }

    @Override
    public void updateUser(User user) {
        users.put(user.getId(), user);
    }

}
