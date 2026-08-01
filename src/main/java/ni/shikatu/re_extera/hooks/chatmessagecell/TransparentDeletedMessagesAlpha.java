package ni.shikatu.re_extera.hooks.chatmessagecell;

import org.telegram.messenger.MessageObject;
import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.settings.Settings;
import java.lang.reflect.Method;

public class TransparentDeletedMessagesAlpha extends XC_MethodHook {
    private static Method getMessageObjectMethod;

    @Override
    public void beforeHookedMethod(MethodHookParam param) {
        if (!ni.shikatu.re_extera.hooks.HookInit.isActive || !Settings.getTransparentDeletedMessages()) {
            return;
        }
        
        try {
            if (getMessageObjectMethod == null) {
                getMessageObjectMethod = param.thisObject.getClass().getMethod("getMessageObject");
            }
            MessageObject messageObject = (MessageObject) getMessageObjectMethod.invoke(param.thisObject);
            
            if (messageObject != null) {
                boolean isDeleted = false;
                try {
                    if (param.thisObject instanceof org.telegram.ui.Cells.ChatMessageCell) {
                        org.telegram.ui.Cells.ChatMessageCell chatCell = (org.telegram.ui.Cells.ChatMessageCell) param.thisObject;
                        org.telegram.messenger.MessageObject.GroupedMessages group = chatCell.getCurrentMessagesGroup();
                        if (group != null && group.messages != null && !group.messages.isEmpty()) {
                            isDeleted = true;
                            for (MessageObject m : group.messages) {
                                if (m != null && !m.deleted && !ni.shikatu.re_extera.db.ReExteraDb.get().messageIsDeleted(m)) {
                                    isDeleted = false;
                                    break;
                                }
                            }
                        } else {
                            isDeleted = messageObject.deleted || ni.shikatu.re_extera.db.ReExteraDb.get().messageIsDeleted(messageObject);
                        }
                    } else {
                        isDeleted = messageObject.deleted || ni.shikatu.re_extera.db.ReExteraDb.get().messageIsDeleted(messageObject);
                    }
                } catch (Throwable e) {
                    isDeleted = messageObject.deleted || ni.shikatu.re_extera.db.ReExteraDb.get().messageIsDeleted(messageObject);
                }

                if (isDeleted && !messageObject.deletedByThanos) {
                    float currentAlpha = (float) param.args[0];
                    param.args[0] = currentAlpha * Settings.getTransparentDeletedMessagesAlpha();
                }
            }
        } catch (Throwable e) {
            // Ignore if method not found or other errors
        }
    }
}
