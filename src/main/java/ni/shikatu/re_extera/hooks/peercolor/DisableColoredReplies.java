package ni.shikatu.re_extera.hooks.peercolor;

import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.tgnet.TLRPC;

public class DisableColoredReplies {
    
    public static class UserColorId extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) {
            if (Settings.getDisableColoredReplies()) {
                TLRPC.User user = (TLRPC.User) param.args[0];
                if (user != null) {
                    param.setResult((int) (user.id % 7));
                } else {
                    param.setResult(0);
                }
            }
        }
    }

    public static class UserEmojiId extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) {
            if (Settings.getDisableColoredReplies()) {
                param.setResult(0L);
            }
        }
    }

    public static class ChatColorId extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) {
            if (Settings.getDisableColoredReplies()) {
                TLRPC.Chat chat = (TLRPC.Chat) param.args[0];
                if (chat != null) {
                    param.setResult((int) (chat.id % 7));
                } else {
                    param.setResult(0);
                }
            }
        }
    }

    public static class ChatEmojiId extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) {
            if (Settings.getDisableColoredReplies()) {
                param.setResult(0L);
            }
        }
    }
}
