package ni.shikatu.re_extera.hooks.chatmessagecell;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.messenger.MessageObject;

public class TransparentDeletedMessages extends XC_MethodHook {
    @Override
    public void afterHookedMethod(MethodHookParam param) {
        if (!Settings.getTransparentDeletedMessages()) {
            return;
        }
        
        try {
            MessageObject messageObject = (MessageObject) param.args[0];
            View cell = (View) param.thisObject;
            
            if (messageObject != null && messageObject.deleted && !messageObject.deletedByThanos) {
                cell.setAlpha(0.6f);
            } else {
                cell.setAlpha(1.0f);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
