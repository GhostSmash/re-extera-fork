package ni.shikatu.re_extera.hooks.chatmessagecell;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.messenger.MessageObject;

public class TransparentDeletedMessages extends XC_MethodHook {
    @Override
    public void afterHookedMethod(MethodHookParam param) {
        if (!ni.shikatu.re_extera.hooks.HookInit.isActive || !Settings.getTransparentDeletedMessages()) {
            return;
        }
        
        try {
            MessageObject messageObject = (MessageObject) param.args[0];
            View cell = (View) param.thisObject;
            
            if (messageObject != null) {
                boolean isDeleted = false;
                try {
                    if (cell instanceof org.telegram.ui.Cells.ChatMessageCell) {
                        org.telegram.ui.Cells.ChatMessageCell chatCell = (org.telegram.ui.Cells.ChatMessageCell) cell;
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
                    cell.setAlpha(Settings.getTransparentDeletedMessagesAlpha());
                } else {
                    cell.setAlpha(1.0f);
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("re-extera", "TransparentDeletedMessages error", e);
        }
    }
}
