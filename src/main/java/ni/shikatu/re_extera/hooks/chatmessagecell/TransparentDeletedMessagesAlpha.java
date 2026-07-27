package ni.shikatu.re_extera.hooks.chatmessagecell;

import org.telegram.messenger.MessageObject;
import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.settings.Settings;
import java.lang.reflect.Method;

public class TransparentDeletedMessagesAlpha extends XC_MethodHook {
    @Override
    public void beforeHookedMethod(MethodHookParam param) {
        if (!Settings.getTransparentDeletedMessages()) {
            return;
        }
        
        try {
            Method getMessageObjectMethod = param.thisObject.getClass().getMethod("getMessageObject");
            MessageObject messageObject = (MessageObject) getMessageObjectMethod.invoke(param.thisObject);
            
            if (messageObject != null && messageObject.deleted && !messageObject.deletedByThanos) {
                float currentAlpha = (float) param.args[0];
                param.args[0] = currentAlpha * 0.6f;
            }
        } catch (Throwable e) {
            // Ignore if method not found or other errors
        }
    }
}
