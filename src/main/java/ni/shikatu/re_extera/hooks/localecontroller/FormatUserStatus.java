package ni.shikatu.re_extera.hooks.localecontroller;

import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.db.ReExteraDb;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.tgnet.TLRPC;

public class FormatUserStatus extends XC_MethodHook {
    private static final ThreadLocal<TLRPC.UserStatus> origStatusLocal = new ThreadLocal<>();

    public void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
        if (!Settings.getSaveLastOnline()) {
            return;
        }
        TLRPC.User user = (TLRPC.User) param.args[1];
        if (user == null || user.status == null) {
            return;
        }

        boolean isHidden = (user.status instanceof TLRPC.TL_userStatusRecently) ||
                           (user.status instanceof TLRPC.TL_userStatusLastWeek) ||
                           (user.status instanceof TLRPC.TL_userStatusLastMonth) ||
                           (user.status instanceof TLRPC.TL_userStatusEmpty);

        if (isHidden) {
            int wasOnline = ReExteraDb.get().getLastOnline(user.id);
            if (wasOnline > 0) {
                int currentTime = (int) (System.currentTimeMillis() / 1000L);
                origStatusLocal.set(user.status);
                if (currentTime - wasOnline < 60) {
                    TLRPC.TL_userStatusOnline exactStatus = new TLRPC.TL_userStatusOnline();
                    exactStatus.expires = wasOnline + 60;
                    user.status = exactStatus;
                } else {
                    TLRPC.TL_userStatusOffline exactStatus = new TLRPC.TL_userStatusOffline();
                    exactStatus.expires = wasOnline;
                    user.status = exactStatus;
                }
            }
        }
    }

    public void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
        if (!Settings.getSaveLastOnline()) {
            return;
        }
        TLRPC.User user = (TLRPC.User) param.args[1];
        if (user == null) {
            return;
        }
        TLRPC.UserStatus origStatus = origStatusLocal.get();
        if (origStatus != null) {
            boolean wasOffline = user.status instanceof TLRPC.TL_userStatusOffline;
            user.status = origStatus; // restore original status
            origStatusLocal.remove();
            String res = (String) param.getResult();
            if (res != null && wasOffline) {
                param.setResult(res + " *");
            }
        }
    }
}
