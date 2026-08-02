package ni.shikatu.re_extera.hooks.messagescontroller;

import androidx.collection.LongSparseArray;
import de.robv.android.xposed.XC_MethodHook;
import java.util.ArrayList;
import java.util.Iterator;
import ni.shikatu.re_extera.Main;
import ni.shikatu.re_extera.db.ReExteraDb;
import ni.shikatu.re_extera.utils.AccountUtils;
import ni.shikatu.re_extera.utils.InternalUtils;
import ni.shikatu.re_extera.utils.MessageUtils;
import ni.shikatu.re_extera.utils.ShadowbanCache;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

public class ProcessUpdates extends XC_MethodHook {
    private final ReExteraDb redb = ReExteraDb.get();
    private static final java.util.concurrent.atomic.AtomicBoolean isStatusUpdateQueued = new java.util.concurrent.atomic.AtomicBoolean(false);
    public static final java.util.Set<Integer> serverDeletedMessageIds = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());

    public void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
        int currentAccount = AccountUtils.getCurrentAccount(param.thisObject);
        TLRPC.Updates updates = (TLRPC.Updates) param.args[0];
        ArrayList<TLRPC.Update> filtered = new ArrayList<>();
        LongSparseArray<ArrayList<Integer>> channelDeleted = new LongSparseArray<>();
        if (updates instanceof org.telegram.tgnet.TLRPC.TL_updateShortMessage) {
            org.telegram.tgnet.TLRPC.TL_updateShortMessage shortMsg = (org.telegram.tgnet.TLRPC.TL_updateShortMessage) updates;
            if (ni.shikatu.re_extera.settings.Settings.getSaveLastOnline() && shortMsg.user_id > 0 && shortMsg.date > 0) {
                ReExteraDb.get().saveLastOnlineAsync(shortMsg.user_id, shortMsg.date);
                triggerUIUpdate(currentAccount);
            }
        } else if (updates instanceof org.telegram.tgnet.TLRPC.TL_updateShortChatMessage) {
            org.telegram.tgnet.TLRPC.TL_updateShortChatMessage shortChatMsg = (org.telegram.tgnet.TLRPC.TL_updateShortChatMessage) updates;
            if (ni.shikatu.re_extera.settings.Settings.getSaveLastOnline() && shortChatMsg.from_id > 0 && shortChatMsg.date > 0) {
                ReExteraDb.get().saveLastOnlineAsync(shortChatMsg.from_id, shortChatMsg.date);
                triggerUIUpdate(currentAccount);
            }
        } else if (updates.update != null) {
            if (!processSingleUpdate(updates.update, channelDeleted, currentAccount, null)) {
                param.setResult((Object) null);
                return;
            }
        } else if (updates.updates != null) {
            android.util.SparseArray<Long> midToDid = new android.util.SparseArray<>();
            for (TLRPC.Update update : updates.updates) {
                if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateNewMessage) {
                    org.telegram.tgnet.tl.TL_update.TL_updateNewMessage nm = (org.telegram.tgnet.tl.TL_update.TL_updateNewMessage) update;
                    if (nm.message != null && !nm.message.out) {
                        midToDid.put(nm.message.id, MessageUtils.getDialogIdFromMessage(nm.message));
                    }
                } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage) {
                    org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage ncm = (org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage) update;
                    if (ncm.message != null && !ncm.message.out) {
                        midToDid.put(ncm.message.id, MessageUtils.getDialogIdFromMessage(ncm.message));
                    }
                }
            }
            for (TLRPC.Update update : updates.updates) {
                if (processSingleUpdate(update, channelDeleted, currentAccount, midToDid)) {
                    filtered.add(update);
                }
            }
            updates.updates = filtered;
        }
        flushChannelDeleted(channelDeleted, currentAccount);
        param.args[0] = updates;
    }

    private void flushChannelDeleted(LongSparseArray<ArrayList<Integer>> channelDeleted, final int currentAccount) {
        for (int i = 0; i < channelDeleted.size(); i++) {
            final long did = channelDeleted.keyAt(i);
            final ArrayList<Integer> ids = (ArrayList) channelDeleted.valueAt(i);
            if (ids != null && !ids.isEmpty()) {
                serverDeletedMessageIds.addAll(ids);
                this.redb.batchPutDeletedMessagesAsync(did, ids);
                this.redb.postToDbThread(new Runnable() {
                    @Override
                    public void run() {
                        MessageUtils.forceUpdateViews(currentAccount, did, ids);
                    }
                });
            }
        }
    }

    private void triggerUIUpdate(int currentAccount) {
        final int currentAccountFinal = currentAccount;
        if (isStatusUpdateQueued.compareAndSet(false, true)) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    isStatusUpdateQueued.set(false);
                    org.telegram.messenger.NotificationCenter.getInstance(currentAccountFinal).postNotificationName(
                            org.telegram.messenger.NotificationCenter.updateInterfaces,
                            Integer.valueOf(org.telegram.messenger.MessagesController.UPDATE_MASK_STATUS)
                    );
                }
            }, 1000);
        }
    }

    private boolean processSingleUpdate(TLRPC.Update update, LongSparseArray<ArrayList<Integer>> channelDeleted, int currentAccount, android.util.SparseArray<Long> midToDid) {
        boolean keep = true;
        if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateEditMessage) {
            org.telegram.tgnet.tl.TL_update.TL_updateEditMessage edit = (org.telegram.tgnet.tl.TL_update.TL_updateEditMessage) update;
            processEditedMessage(edit.message, currentAccount);
        } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateEditChannelMessage) {
            org.telegram.tgnet.tl.TL_update.TL_updateEditChannelMessage edit2 = (org.telegram.tgnet.tl.TL_update.TL_updateEditChannelMessage) update;
            processEditedMessage(edit2.message, currentAccount);
        } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateDeleteMessages) {
            org.telegram.tgnet.tl.TL_update.TL_updateDeleteMessages del = (org.telegram.tgnet.tl.TL_update.TL_updateDeleteMessages) update;
            processDeleteMessages(del, currentAccount, midToDid);
        } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateDeleteChannelMessages) {
            org.telegram.tgnet.tl.TL_update.TL_updateDeleteChannelMessages del2 = (org.telegram.tgnet.tl.TL_update.TL_updateDeleteChannelMessages) update;
            processDeleteChannelMessages(del2, channelDeleted, currentAccount);
        } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateNewMessage) {
            org.telegram.tgnet.tl.TL_update.TL_updateNewMessage newMsg = (org.telegram.tgnet.tl.TL_update.TL_updateNewMessage) update;
            keep = !shadowbanFilterHideDialog(newMsg.message);
        } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage) {
            org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage newMsg2 = (org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage) update;
            keep = !shadowbanFilterHideInGroups(newMsg2.message);
        }
        
        if (!keep) {
            return false;
        }

        if (ni.shikatu.re_extera.settings.Settings.getSaveReadDate()) {
            if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateReadHistoryOutbox) {
                org.telegram.tgnet.tl.TL_update.TL_updateReadHistoryOutbox outbox = (org.telegram.tgnet.tl.TL_update.TL_updateReadHistoryOutbox) update;
                long did = org.telegram.messenger.DialogObject.getPeerDialogId(outbox.peer);
                ReExteraDb.get().saveReadEventAsync(did, outbox.max_id);
            } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateReadChannelOutbox) {
                org.telegram.tgnet.tl.TL_update.TL_updateReadChannelOutbox outbox2 = (org.telegram.tgnet.tl.TL_update.TL_updateReadChannelOutbox) update;
                long did = -outbox2.channel_id;
                ReExteraDb.get().saveReadEventAsync(did, outbox2.max_id);
            }
        }
        if (ni.shikatu.re_extera.settings.Settings.getSaveLastOnline()) {
            if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateUserStatus) {
                org.telegram.tgnet.tl.TL_update.TL_updateUserStatus statusUpdate = (org.telegram.tgnet.tl.TL_update.TL_updateUserStatus) update;
                if (statusUpdate.status instanceof TLRPC.TL_userStatusOffline) {
                    ReExteraDb.get().saveLastOnlineAsync(statusUpdate.user_id, ((TLRPC.TL_userStatusOffline) statusUpdate.status).expires);
                } else if (statusUpdate.status instanceof TLRPC.TL_userStatusOnline) {
                    ReExteraDb.get().saveLastOnlineAsync(statusUpdate.user_id, (int) (System.currentTimeMillis() / 1000L));
                }
            } else {
                long onlineUserId = 0;
                int onlineDate = 0;
                if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateNewMessage) {
                    org.telegram.tgnet.tl.TL_update.TL_updateNewMessage newMsg = (org.telegram.tgnet.tl.TL_update.TL_updateNewMessage) update;
                    if (newMsg.message != null && !(newMsg.message instanceof TLRPC.TL_messageEmpty)) {
                        onlineUserId = getFromId(newMsg.message);
                        onlineDate = newMsg.message.date;
                    }
                } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage) {
                    org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage newMsg2 = (org.telegram.tgnet.tl.TL_update.TL_updateNewChannelMessage) update;
                    if (newMsg2.message != null && !(newMsg2.message instanceof TLRPC.TL_messageEmpty)) {
                        onlineUserId = getFromId(newMsg2.message);
                        onlineDate = newMsg2.message.date;
                    }
                } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateEditMessage) {
                    org.telegram.tgnet.tl.TL_update.TL_updateEditMessage editMsg = (org.telegram.tgnet.tl.TL_update.TL_updateEditMessage) update;
                    if (editMsg.message != null && !(editMsg.message instanceof TLRPC.TL_messageEmpty)) {
                        onlineUserId = getFromId(editMsg.message);
                        onlineDate = editMsg.message.edit_date != 0 ? editMsg.message.edit_date : editMsg.message.date;
                    }
                } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateUserTyping) {
                    org.telegram.tgnet.tl.TL_update.TL_updateUserTyping typing = (org.telegram.tgnet.tl.TL_update.TL_updateUserTyping) update;
                    onlineUserId = typing.user_id;
                    onlineDate = (int) (System.currentTimeMillis() / 1000L);
                } else if (update instanceof org.telegram.tgnet.tl.TL_update.TL_updateChatUserTyping) {
                    org.telegram.tgnet.tl.TL_update.TL_updateChatUserTyping typing = (org.telegram.tgnet.tl.TL_update.TL_updateChatUserTyping) update;
                    onlineUserId = typing.from_id != null ? typing.from_id.user_id : 0;
                    onlineDate = (int) (System.currentTimeMillis() / 1000L);
                }
                
                if (onlineUserId > 0 && onlineDate > 0) {
                    ReExteraDb.get().saveLastOnlineAsync(onlineUserId, onlineDate);
                    triggerUIUpdate(currentAccount);
                }
            }
        }
        return true;
    }

    private boolean shadowbanFilterHideDialog(TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        long fromId = getFromId(message);
        if (fromId <= 0 || !ShadowbanCache.shouldHideDialog(fromId)) {
            return false;
        }
        Main.log("ProcessUpdates: filtered new message from shadowbanned user %d", Long.valueOf(fromId));
        return true;
    }

    private boolean shadowbanFilterHideInGroups(TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        long fromId = getFromId(message);
        if (fromId <= 0 || !ShadowbanCache.shouldHideInGroups(fromId)) {
            return false;
        }
        Main.log("ProcessUpdates: filtered channel message from shadowbanned user %d", Long.valueOf(fromId));
        return true;
    }

    private static long getFromId(TLRPC.Message message) {
        if (message.from_id instanceof TLRPC.TL_peerUser) {
            return message.from_id.user_id;
        }
        return 0L;
    }

    private void processEditedMessage(TLRPC.Message message, int currentAccount) {
        long did = MessageUtils.getDialogIdFromMessage(message);
        if (!ni.shikatu.re_extera.settings.Settings.getSaveBotChats()) {
            org.telegram.tgnet.TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(did);
            if (user != null && user.bot) {
                return;
            }
        }
        MessageObject oldObj = MessageUtils.getMessage(currentAccount, did, message.id);
        if (oldObj != null && !oldObj.isOut()) {
            if (!this.redb.messageHasSavedEdits(did, message.id)) {
                this.redb.saveOriginalMessageAsync(did, message.id, oldObj.messageOwner);
            }
            this.redb.saveNewVersionMessageAsync(did, message.id, message);
        }
    }

    private void processDeleteMessages(org.telegram.tgnet.tl.TL_update.TL_updateDeleteMessages update, final int currentAccount, android.util.SparseArray<Long> midToDid) {
        if (update.messages == null) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(currentAccount);
        LongSparseArray<ArrayList<Integer>> toUpdateGrouped = new LongSparseArray<>();
        synchronized (controller) {
            Iterator it = update.messages.iterator();
            while (it.hasNext()) {
                int id = ((Integer) it.next()).intValue();
                long did = 0;
                MessageObject obj = MessageUtils.getMessage(currentAccount, 0L, id);
                if (obj != null) {
                    if (obj.messageOwner != null && obj.messageOwner.peer_id instanceof TLRPC.TL_peerChannel) {
                        continue;
                    }
                    did = obj.getDialogId();
                } else if (midToDid != null && midToDid.indexOfKey(id) >= 0) {
                    did = midToDid.get(id);
                }
                
                if (did != 0) {
                    if (!ni.shikatu.re_extera.settings.Settings.getSaveBotChats()) {
                        org.telegram.tgnet.TLRPC.User user = controller.getUser(did);
                        if (user != null && user.bot) {
                            continue;
                        }
                    }
                    ArrayList<Integer> list = (ArrayList) toUpdateGrouped.get(did);
                    if (list == null) {
                        list = new ArrayList<>();
                        toUpdateGrouped.put(did, list);
                    }
                    list.add(Integer.valueOf(obj != null ? obj.getId() : id));
                }
            }
            for (int i = 0; i < toUpdateGrouped.size(); i++) {
                final long did2 = toUpdateGrouped.keyAt(i);
                final ArrayList<Integer> ids = (ArrayList) toUpdateGrouped.valueAt(i);
                if (ids != null && !ids.isEmpty()) {
                    serverDeletedMessageIds.addAll(ids);
                    this.redb.batchPutDeletedMessagesAsync(did2, ids);
                    this.redb.postToDbThread(new Runnable() {
                        @Override
                        public void run() {
                            MessageUtils.forceUpdateViews(currentAccount, did2, ids);
                        }
                    });
                }
            }
        }
    }

    private void processDeleteScheduledMessages(org.telegram.tgnet.tl.TL_update.TL_updateDeleteScheduledMessages update, int currentAccount) {
        long dialogId = DialogObject.getPeerDialogId(update.peer);
        // update.sent_messages = IDs of the NEW real messages that were just delivered
        //   from the scheduled list — these must NOT be deleted, they are live messages.
        // update.messages     = IDs of the scheduled-slot entries being removed.
        //   Only these should be saved as "deleted scheduled messages".
        if (update.sent_messages != null && !update.sent_messages.isEmpty()) {
            Main.log("processDeleteScheduledMessages: ignoring %d sent_messages (live) for did=%d",
                    update.sent_messages.size(), dialogId);
        }
        if (update.messages == null || update.messages.isEmpty()) {
            return;
        }
        ArrayList<Integer> toDelete = new ArrayList<>(update.messages);
        Main.log("processDeleteScheduledMessages: saving %d deleted scheduled ids for did=%d", toDelete.size(), dialogId);
        InternalUtils.deleteMessages(currentAccount, dialogId, toDelete, true);
    }

    private void processDeleteChannelMessages(org.telegram.tgnet.tl.TL_update.TL_updateDeleteChannelMessages update, LongSparseArray<ArrayList<Integer>> channelDeleted, int currentAccount) {
        if (update.messages == null || update.messages.isEmpty()) {
            return;
        }
        ArrayList<Integer> list = channelDeleted.get(update.channel_id);
        if (list == null) {
            list = new ArrayList<>();
            channelDeleted.put(update.channel_id, list);
        }
        for (Integer id : update.messages) {
            MessageObject obj = MessageUtils.getMessage(currentAccount, update.channel_id != 0 ? -update.channel_id : 0L, id);
            list.add(id);
        }
    }
}
