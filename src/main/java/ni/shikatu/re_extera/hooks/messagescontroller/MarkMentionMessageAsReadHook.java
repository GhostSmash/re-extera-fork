package ni.shikatu.re_extera.hooks.messagescontroller;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.settings.Settings;
import ni.shikatu.re_extera.utils.AccountUtils;
import org.telegram.messenger.NotificationCenter;

public class MarkMentionMessageAsReadHook extends XC_MethodHook {
    @Override
    public void afterHookedMethod(MethodHookParam param) {
        if (Settings.getHideReadingWithGhost()) {
            int messageId = (Integer) param.args[0];
            long dialogId = (Long) param.args[2];
            
            int currentAccount = AccountUtils.getCurrentAccount(param.thisObject);
            ArrayList<Integer> arrayList = new ArrayList<>();
            arrayList.add(messageId);
            
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.messagesReadContent, dialogId, arrayList
            );
        }
    }
}
