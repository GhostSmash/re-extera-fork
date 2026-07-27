package ni.shikatu.re_extera.hooks.messagesstorage;

import de.robv.android.xposed.XC_MethodHook;
import java.util.ArrayList;
import ni.shikatu.re_extera.Main;
import ni.shikatu.re_extera.db.ReExteraDb;
import ni.shikatu.re_extera.settings.Settings;
import ni.shikatu.re_extera.utils.AccountUtils;
import ni.shikatu.re_extera.utils.MessageUtils;

public class UpdateDialogsWithDeletedMessages extends XC_MethodHook {
    private final ReExteraDb redb = ReExteraDb.get();

    public void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if ("deleteMessagesRange".equals(ste.getMethodName())) {
                return;
            }
        }
        if (Settings.getSaveDeletedMessages()) {
            int currentAccount = AccountUtils.getCurrentAccount(param.thisObject);
            long uid = ((Long) param.args[0]).longValue();
            long channelId = ((Long) param.args[1]).longValue();
            long did = channelId != 0 ? -channelId : uid;
            
            if (!Settings.getSaveBotChats()) {
                org.telegram.tgnet.TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(did);
                if (user != null && user.bot) {
                    return;
                }
            }
            
            ArrayList<Integer> ids = (ArrayList) param.args[2];
            if (ids == null || ids.isEmpty()) {
                return;
            }
            
            ArrayList<Integer> validIds = new ArrayList<>();
            ArrayList<Integer> tempIds = new ArrayList<>();
            for (Integer id : ids) {
                if (id != null && id > 0) {
                    validIds.add(id);
                } else if (id != null) {
                    tempIds.add(id);
                }
            }

            if (!validIds.isEmpty()) {
                Main.log("UpdateDialogsWithDeletedMessages: intercepting %d ids for did=%d (args=%d)", validIds.size(), did, param.args.length);
                this.redb.lambda$batchPutDeletedMessagesAsync$1(did, validIds);
                MessageUtils.forceUpdateViews(currentAccount, did, validIds);
            }

            param.args[2] = tempIds;
            boolean isInternalVariant = param.args.length == 4;
            if (isInternalVariant && tempIds.isEmpty()) {
                param.setResult((Object) null);
            }
        }
    }
}
