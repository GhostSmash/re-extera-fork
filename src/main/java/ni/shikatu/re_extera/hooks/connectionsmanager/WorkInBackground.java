package ni.shikatu.re_extera.hooks.connectionsmanager;

import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.settings.Settings;

public class WorkInBackground extends XC_MethodHook {
    @Override
    public void beforeHookedMethod(MethodHookParam param) {
        if (Settings.getWorkInBackground()) {
            int currentAccount = ni.shikatu.re_extera.utils.AccountUtils.getCurrentAccount(param.thisObject);
            if (currentAccount == org.telegram.messenger.UserConfig.selectedAccount) {
                // Force setAppPaused(false, ...)
                param.args[0] = false;
                // The second parameter is whether to reset the idle state, which can be kept as is or also forced to false.
            }
        }
    }
}
