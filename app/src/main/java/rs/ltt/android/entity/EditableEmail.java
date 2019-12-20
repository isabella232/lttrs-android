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

import androidx.room.Relation;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EditableEmail {

    public String id;

    public String subject;

    @Relation(entity = EmailEmailAddressEntity.class, parentColumn = "id", entityColumn = "emailId", projection = {"email", "name", "type"})
    public List<EmailAddress> emailAddresses;

    @Relation(parentColumn = "id", entityColumn = "emailId")
    public List<EmailBodyPartEntity> bodyPartEntities;

    @Relation(parentColumn = "id", entityColumn = "emailId")
    public List<EmailBodyValueEntity> bodyValueEntities;

    @Relation(entity = EmailInReplyToEntity.class, parentColumn = "id", entityColumn = "emailId", projection = {"id"})
    public List<String> inReplyTo;


    public String getText() {
        final ArrayList<EmailBodyPartEntity> textBody = new ArrayList<>();
        for (EmailBodyPartEntity entity : bodyPartEntities) {
            if (entity.bodyPartType == EmailBodyPartType.TEXT_BODY) {
                textBody.add(entity);
            }
        }
        Collections.sort(textBody, (o1, o2) -> o1.position.compareTo(o2.position));
        EmailBodyPartEntity first = Iterables.getFirst(textBody, null);
        Map<String, EmailBodyValueEntity> map = Maps.uniqueIndex(bodyValueEntities, value -> value.partId);
        EmailBodyValueEntity value = map.get(first.partId);
        return value.value;
    }

    public Collection<rs.ltt.jmap.common.entity.EmailAddress> getTo() {
        return Collections2.transform(Collections2.filter(emailAddresses, input -> input != null && input.type == EmailAddressType.TO), new Function<EmailAddress, rs.ltt.jmap.common.entity.EmailAddress>() {
            @NullableDecl
            @Override
            public rs.ltt.jmap.common.entity.EmailAddress apply(@NullableDecl EmailAddress input) {
                return input == null ? null : rs.ltt.jmap.common.entity.EmailAddress.builder().email(input.email).name(input.name).build();
            }
        });
    }

}
