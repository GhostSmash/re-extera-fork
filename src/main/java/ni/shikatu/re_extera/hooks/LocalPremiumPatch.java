package ni.shikatu.re_extera.hooks.userconfig;

import android.util.Base64;
import de.robv.android.xposed.XC_MethodHook;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import ni.shikatu.re_extera.Main;
import ni.shikatu.re_extera.settings.Settings;
import ni.shikatu.re_extera.utils.AccountUtils;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

/**
 * "Локальный премиум" раньше подделывал только результат UserConfig.isPremium()
 * (см. isPremium.java). Этого недостаточно: значок Premium и связанные с ним
 * визуальные элементы (цвет ника, статус-эмодзи) в 12.9.0 рисуются на основе
 * поля TLRPC.User.premium самого объекта пользователя, а не через вызов
 * этого геттера. Патчим объект в двух точках входа, где текущий юзер
 * попадает в кэш/UI: UserConfig.setCurrentUser(User) и
 * MessagesController.putUser(User, boolean).
 *
 * Доп. сложность: без реального премиума сервер не присылает peer color /
 * profile color / emoji status - если мы просто выставим user.premium = true,
 * значок появится, но без цвета и статус-эмодзи, если они были раньше.
 * Поэтому сохраняем последние увиденные значения этих полей в Settings и
 * восстанавливаем их, когда сервер их не прислал.
 */
public final class LocalPremiumPatch {
    private LocalPremiumPatch() {
    }

    private static String encodeField(TLRPC.TL_peerColor color) {
        if (color == null) return null;
        try {
            int size = color.getObjectSize();
            org.telegram.tgnet.NativeByteBuffer buffer = new org.telegram.tgnet.NativeByteBuffer(size);
            color.serializeToStream(buffer);
            int written = buffer.position();
            byte[] out;
            if (buffer.buffer.hasArray()) {
                out = new byte[written];
                System.arraycopy(buffer.buffer.array(), buffer.buffer.arrayOffset(), out, 0, written);
            } else {
                out = new byte[written];
                buffer.buffer.position(0);
                buffer.buffer.get(out, 0, written);
            }
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Throwable e) {
            Main.log("LocalPremiumPatch: encode color failed: %s", e.getMessage());
            return null;
        }
    }

    private static TLRPC.TL_peerColor decodeColorField(String base64Value) {
        if (base64Value == null) return null;
        try {
            byte[] bytes = Base64.decode(base64Value, Base64.NO_WRAP);
            org.telegram.tgnet.NativeByteBuffer buffer = new org.telegram.tgnet.NativeByteBuffer(bytes.length);
            buffer.writeBytes(bytes);
            buffer.position(0);
            TLRPC.TL_peerColor color = new TLRPC.TL_peerColor();
            color.readParams(buffer, true);
            return color;
        } catch (Throwable e) {
            Main.log("LocalPremiumPatch: decode color failed: %s", e.getMessage());
            return null;
        }
    }

    private static boolean isEmptyColor(Object c) {
        return c == null;
    }

    private static void rememberFieldsIfPresent(int account, TLRPC.User user) {
        try {
            if (user.color instanceof TLRPC.TL_peerColor) {
                String enc = encodeField((TLRPC.TL_peerColor) user.color);
                if (enc != null) {
                    Settings.setCachedPremiumField(account, "color", enc);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (user.profile_color instanceof TLRPC.TL_peerColor) {
                String enc = encodeField((TLRPC.TL_peerColor) user.profile_color);
                if (enc != null) {
                    Settings.setCachedPremiumField(account, "profile_color", enc);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Главная точка патча: форсит user.premium = true и, если поля цвета
     * пустые, подставляет последние запомненные значения из кэша.
     */
    public static void patchUser(int account, TLRPC.User user) {
        if (user == null || !Settings.getLocalPremium()) {
            return;
        }
        long myId;
        try {
            myId = UserConfig.getInstance(account).getClientUserId();
        } catch (Throwable e) {
            return;
        }
        if (user.id != myId) {
            return;
        }

        rememberFieldsIfPresent(account, user);

        user.premium = true;

        if (isEmptyColor(user.color)) {
            TLRPC.TL_peerColor cached = decodeColorField(Settings.getCachedPremiumField(account, "color"));
            if (cached != null) {
                user.color = cached;
            }
        }
        if (isEmptyColor(user.profile_color)) {
            TLRPC.TL_peerColor cached = decodeColorField(Settings.getCachedPremiumField(account, "profile_color"));
            if (cached != null) {
                user.profile_color = cached;
            }
        }
    }

    /** Хук на UserConfig.setCurrentUser(TLRPC.User) - срабатывает при логине/обновлении своего профиля. */
    public static class SetCurrentUserHook extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) {
            if (!Settings.getLocalPremium()) {
                return;
            }
            try {
                TLRPC.User user = (TLRPC.User) param.args[0];
                int account = AccountUtils.getCurrentAccount(param.thisObject);
                patchUser(account, user);
            } catch (Throwable e) {
                Main.log("LocalPremiumPatch.SetCurrentUserHook: %s", e.getMessage());
            }
        }
    }

    /** Хук на MessagesController.putUser(TLRPC.User, boolean) - срабатывает при обновлении данных юзера из сети/кэша. */
    public static class PutUserHook extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) {
            if (!Settings.getLocalPremium()) {
                return;
            }
            try {
                TLRPC.User user = (TLRPC.User) param.args[0];
                int account = AccountUtils.getCurrentAccount(param.thisObject);
                patchUser(account, user);
            } catch (Throwable e) {
                Main.log("LocalPremiumPatch.PutUserHook: %s", e.getMessage());
            }
        }
    }

    /** Хук на MessagesController.putUsers(ArrayList<User>, boolean) - тот же случай, но для списка. */
    public static class PutUsersHook extends XC_MethodHook {
        @Override
        @SuppressWarnings("unchecked")
        public void beforeHookedMethod(MethodHookParam param) {
            if (!Settings.getLocalPremium()) {
                return;
            }
            try {
                ArrayList<TLRPC.User> users = (ArrayList<TLRPC.User>) param.args[0];
                if (users == null || users.isEmpty()) {
                    return;
                }
                int account = AccountUtils.getCurrentAccount(param.thisObject);
                for (TLRPC.User user : users) {
                    patchUser(account, user);
                }
            } catch (Throwable e) {
                Main.log("LocalPremiumPatch.PutUsersHook: %s", e.getMessage());
            }
        }
    }
}
