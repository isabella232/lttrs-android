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

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import com.google.common.base.Objects;
import com.google.common.collect.Collections2;

import java.util.Collection;
import java.util.List;

import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRoleAndName;
import rs.ltt.jmap.common.entity.Role;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = "mailbox_overwrite",
        primaryKeys = {"threadId", "name", "role"},
        foreignKeys = @ForeignKey(entity = ThreadEntity.class,
                parentColumns = {"threadId"},
                childColumns = {"threadId"},
                onDelete = CASCADE
        )
)
public class MailboxOverwriteEntity {

    private static final String EMPTY_STRING = "";

    @NonNull
    public String threadId;
    @NonNull
    public String name;
    @NonNull
    public String role;
    public boolean value;

    public static Collection<MailboxOverwriteEntity> of(Collection<String> threadIds, @NonNull Role role, boolean value) {
        return Collections2.transform(threadIds, threadId -> of(threadId, role, value));
    }

    public static MailboxOverwriteEntity of(String threadId, @NonNull Role role, boolean value) {
        MailboxOverwriteEntity entity = new MailboxOverwriteEntity();
        entity.threadId = threadId;
        entity.role = role.toString();
        entity.name = EMPTY_STRING;
        entity.value = value;
        return entity;
    }

    public static Collection<MailboxOverwriteEntity> of(Collection<String> threadIds, @NonNull String label, boolean value) {
        return Collections2.transform(threadIds, threadId -> of(threadId, label, value));
    }

    public static MailboxOverwriteEntity of(String threadId, @NonNull String label, boolean value) {
        MailboxOverwriteEntity entity = new MailboxOverwriteEntity();
        entity.threadId = threadId;
        entity.role = EMPTY_STRING;
        entity.name = label;
        entity.value = value;
        return entity;
    }


    public static boolean hasOverwrite(Collection<MailboxOverwriteEntity> overwriteEntities, Role role) {
        MailboxOverwriteEntity mailboxOverwriteEntity = find(overwriteEntities, role);
        if (mailboxOverwriteEntity != null) {
            return mailboxOverwriteEntity.value;
        }
        return false;
    }

    public static MailboxOverwriteEntity find(Collection<MailboxOverwriteEntity> overwriteEntities, Role role) {
        for (MailboxOverwriteEntity overwriteEntity : overwriteEntities) {
            if (role.toString().equals(overwriteEntity.role)) {
                return overwriteEntity;
            }
        }
        return null;
    }

    public static Boolean getOverwrite(final List<MailboxOverwriteEntity> mailboxOverwrites, IdentifiableMailboxWithRoleAndName mailbox) {
        final Role role = mailbox.getRole();
        final String name = mailbox.getName();
        for (final MailboxOverwriteEntity overwrite : mailboxOverwrites) {
            if (role != null) {
                if (role.toString().equals(overwrite.role)) {
                    return overwrite.value;
                }
            } else if (name != null) {
                if (name.equals(overwrite.name)) {
                    return overwrite.value;
                }
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailboxOverwriteEntity entity = (MailboxOverwriteEntity) o;
        return value == entity.value &&
                Objects.equal(threadId, entity.threadId) &&
                Objects.equal(name, entity.name) &&
                Objects.equal(role, entity.role);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(threadId, name, role, value);
    }
}
