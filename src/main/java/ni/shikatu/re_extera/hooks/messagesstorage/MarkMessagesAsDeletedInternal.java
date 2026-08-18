package ni.shikatu.re_extera.hooks.messagesstorage;

import de.robv.android.xposed.XC_MethodHook;
import java.util.ArrayList;
import ni.shikatu.re_extera.db.ReExteraDb;
import ni.shikatu.re_extera.settings.Settings;
import ni.shikatu.re_extera.utils.AccountUtils;
import ni.shikatu.re_extera.utils.MessageUtils;
import org.telegram.messenger.MessageObject;

public class MarkMessagesAsDeletedInternal extends XC_MethodHook {
    private final ReExteraDb redb = ReExteraDb.get();

    public void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if ("deleteMessagesRange".equals(ste.getMethodName())) {
                return;
            }
        }
        if (Settings.getSaveDeletedMessages()) {
            int currentAccount = AccountUtils.getCurrentAccount(param.thisObject);
            long did = ((Long) param.args[0]).longValue();
            
            if (!Settings.getSaveBotChats()) {
                org.telegram.tgnet.TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(did);
                if (user != null && user.bot) {
                    return;
                }
            }
            
            Object msgArg = param.args[1];
            ArrayList<Integer> validIds = new ArrayList<>();
            ArrayList<Integer> tempIds = new ArrayList<>();
            if (msgArg instanceof ArrayList) {
                ArrayList<Integer> originalMessages = (ArrayList<Integer>) msgArg;
                for (Integer id : originalMessages) {
                    if (id != null && id > 0) {
                        validIds.add(id);
                    } else if (id != null) {
                        tempIds.add(id);
                    }
                }
                param.args[1] = tempIds;
                if (tempIds.isEmpty()) {
                    param.setResult((Object) null);
                }
            } else if (msgArg instanceof Integer) {
                int mid = ((Integer) msgArg).intValue();
                if (mid > 0) {
                    validIds.add(mid);
                    param.setResult((Object) null);
                }
            }

            if (!validIds.isEmpty()) {
                this.redb.batchPutDeletedMessagesAsync(did, validIds);
                MessageUtils.forceUpdateViews(currentAccount, did, validIds);
                if (Settings.getSaveAttachments()) {
                    ni.shikatu.re_extera.utils.AttachmentSaver.saveAttachments(currentAccount, did, validIds);
                }
            }
        }
    }
}
