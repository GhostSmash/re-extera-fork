package ni.shikatu.re_extera.hooks.messagescontroller;

import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.Main;
import ni.shikatu.re_extera.settings.Settings;
import java.util.ArrayList;

public class DisableAds extends XC_MethodHook {
    @Override
    public void beforeHookedMethod(MethodHookParam param) {
        if (!Settings.getDisableAds()) {
            return;
        }
        
        try {
            Class<?> infoClass = Class.forName("org.telegram.messenger.MessagesController$SponsoredMessagesInfo");
            Object info = infoClass.getDeclaredConstructor(param.thisObject.getClass()).newInstance(param.thisObject);
            
            java.lang.reflect.Field messagesField = infoClass.getDeclaredField("messages");
            messagesField.setAccessible(true);
            messagesField.set(info, new ArrayList<>());
            
            java.lang.reflect.Field loadingField = infoClass.getDeclaredField("loading");
            loadingField.setAccessible(true);
            loadingField.set(info, false);
            
            param.setResult(info);
            Main.log("DisableAds: Returned empty SponsoredMessagesInfo");
        } catch (Exception e) {
            Main.log("DisableAds error: %s", e.getMessage());
        }
    }
}
