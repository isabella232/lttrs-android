/*
 * Copyright 2019 Daniel Gultsch
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

package rs.ltt.android.entity;

import okhttp3.HttpUrl;

public class AccountWithCredentials {

    public Long id;
    public String accountId;
    public String username;
    public String password;
    public HttpUrl sessionResource;

    public AccountWithCredentials(Long id, String accountId, String username, String password, HttpUrl sessionResource) {
        this.id = id;
        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.sessionResource = sessionResource;
    }
}