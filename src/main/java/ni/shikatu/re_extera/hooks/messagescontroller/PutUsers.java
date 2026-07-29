package ni.shikatu.re_extera.hooks.messagescontroller;

import java.util.ArrayList;
import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.db.ReExteraDb;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.tgnet.TLRPC;

public class PutUsers extends XC_MethodHook {
    @Override
    public void beforeHookedMethod(MethodHookParam param) {
        if (!Settings.getSaveLastOnline()) {
            return;
        }
        
        boolean fromCache = false;
        if (param.args.length > 1 && param.args[1] instanceof Boolean) {
            fromCache = (Boolean) param.args[1];
        }
        
        // Ignore users loaded from local database
        if (fromCache) {
            return;
        }
        
        ArrayList<TLRPC.User> users = (ArrayList<TLRPC.User>) param.args[0];
        if (users == null || users.isEmpty()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis() / 1000L;
        
        for (TLRPC.User user : users) {
            if (user != null && user.status != null) {
                if (user.status instanceof TLRPC.TL_userStatusOffline) {
                    ReExteraDb.get().saveLastOnlineAsync(user.id, ((TLRPC.TL_userStatusOffline) user.status).expires);
                } else if (user.status instanceof TLRPC.TL_userStatusOnline) {
                    ReExteraDb.get().saveLastOnlineAsync(user.id, (int) currentTime);
                }
            }
        }
    }
}
