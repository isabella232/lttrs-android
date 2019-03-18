package rs.ltt.android.database.dao;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import rs.ltt.android.entity.EntityStateEntity;
import rs.ltt.android.entity.EntityType;
import rs.ltt.android.entity.MailboxEntity;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.jmap.common.entity.Mailbox;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.cache.Update;

@Dao
public abstract class MailboxDao extends AbstractEntityDao<Mailbox> {

    @Insert
    protected abstract void insert(List<MailboxEntity> mailboxEntities);

    @androidx.room.Update
    protected abstract void update(List<MailboxEntity> mailboxEntities);

    @Query("select * from mailbox where role is not null")
    public abstract List<MailboxEntity> getSpecialMailboxes();

    @Query("select id,parentId,name,sortOrder,unreadThreads,totalThreads,role from mailbox")
    public abstract LiveData<List<MailboxOverviewItem>> getMailboxes();

    @Query("select id,parentId,name,sortOrder,unreadThreads,totalThreads,role from mailbox where role=:role limit 1")
    public abstract LiveData<MailboxOverviewItem> getMailboxOverviewItem(Role role);

    @Query("select id,parentid,name,sortOrder,unreadThreads,totalThreads,role from mailbox where id=:id")
    public abstract LiveData<MailboxOverviewItem> getMailboxOverviewItem(String id);

    @Query("update mailbox set totalEmails=:value where id=:id")
    public abstract void updateTotalEmails(String id, Integer value);

    @Query("update mailbox set unreadEmails=:value where id=:id")
    public abstract void updateUnreadEmails(String id, Integer value);

    @Query("update mailbox set totalThreads=:value where id=:id")
    public abstract void updateTotalThreads(String id, Integer value);

    @Query("update mailbox set unreadThreads=:value where id=:id")
    public abstract void updateUnreadThreads(String id, Integer value);

    @Query("delete from mailbox where id=:id")
    public abstract void delete(String id);

    @Transaction
    public void set(List<MailboxEntity> mailboxEntities, EntityStateEntity entityStateEntity) {
        //TODO delete old
        insert(mailboxEntities);
        insert(entityStateEntity);
    }

    @Transaction
    public void update(final Update<Mailbox> update, final String[] updatedProperties) {
        List<MailboxEntity> createdEntities = new ArrayList<>();
        for (Mailbox mailbox : update.getCreated()) {
            createdEntities.add(MailboxEntity.of(mailbox));
        }
        insert(createdEntities);
        if (updatedProperties == null) {
            List<MailboxEntity> updatedEntities = new ArrayList<>();
            for (Mailbox mailbox : update.getUpdated()) {
                updatedEntities.add(MailboxEntity.of(mailbox));
            }
            update(updatedEntities);
        } else {
            for (Mailbox mailbox : update.getUpdated()) {
                for (String property : updatedProperties) {
                    switch (property) {
                        case "totalEmails":
                            updateTotalEmails(mailbox.getId(), mailbox.getTotalEmails());
                            break;
                        case "unreadEmails":
                            updateUnreadEmails(mailbox.getId(), mailbox.getUnreadEmails());
                            break;
                        case "totalThreads":
                            updateTotalThreads(mailbox.getId(), mailbox.getTotalThreads());
                            break;
                        case "unreadThreads":
                            updateUnreadThreads(mailbox.getId(), mailbox.getUnreadThreads());
                            break;
                        default:
                            throw new IllegalArgumentException("Unable to update property '" + property + "'");
                    }
                }
            }
        }
        for(String id : update.getDestroyed()) {
            delete(id);
        }
        throwOnUpdateConflict(EntityType.MAILBOX, update.getOldTypedState(), update.getNewTypedState());
    }
}
