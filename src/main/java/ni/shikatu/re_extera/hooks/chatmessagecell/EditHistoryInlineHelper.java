package ni.shikatu.re_extera.hooks.chatmessagecell;

import de.robv.android.xposed.XC_MethodHook;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import ni.shikatu.re_extera.Main;
import ni.shikatu.re_extera.db.ReExteraDb;
import ni.shikatu.re_extera.localization.Localization;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

/**
 * Показывает "Изменено с: <исходный текст>" прямо над отредактированным
 * сообщением, переиспользуя стандартный reply-блок Telegram (MessageObject
 * .replyMessageObject) вместо кастомного рисования поверх ChatMessageCell -
 * так это гарантированно выглядит "родным" и не требует хрупких правок
 * самого рендер-кода ячейки (490KB класс с 14 перегрузками конструктора
 * MessageObject - слишком нестабильная почва для прямого хука рендера).
 *
 * Хук вешается на ChatMessageCell.setMessageObject(...), который принимает
 * уже готовый MessageObject - что бы ни создало этот объект (любая из 14
 * перегрузок конструктора), к моменту биндинга ячейки он уже есть.
 *
 * Кэш trackedIds - лёгкая in-memory прослойка (did:mid -> hasEdits), чтобы
 * не бить SQLite на каждый биндинг ячейки при скролле; инвалидируется явно
 * при записи новой версии (см. invalidate ниже, вызывается из ProcessUpdates).
 */
public final class EditHistoryInlineHelper {
    private static final Map<String, Boolean> hasEditsCache = new HashMap<>();
    private static final Map<String, MessageObject> originalReplyCache = new HashMap<>();

    private EditHistoryInlineHelper() {
    }

    private static String key(long did, int mid) {
        return did + ":" + mid;
    }

    /** Вызывается из ProcessUpdates после сохранения новой версии, чтобы кэш не показывал устаревшее "нет истории". */
    public static void invalidate(long did, int mid) {
        String k = key(did, mid);
        hasEditsCache.remove(k);
        originalReplyCache.remove(k);
    }

    private static boolean hasEdits(long did, int mid) {
        String k = key(did, mid);
        Boolean cached = hasEditsCache.get(k);
        if (cached != null) {
            return cached;
        }
        boolean result;
        try {
            result = ReExteraDb.get().messageHasSavedEdits(did, mid);
        } catch (Throwable e) {
            result = false;
        }
        hasEditsCache.put(k, result);
        return result;
    }

    /**
     * Строит (и кэширует) синтетический MessageObject с текстом самой первой
     * сохранённой версии сообщения, помеченный служебным customReplyName,
     * чтобы UI отрисовал его как "Изменено с: <текст>" вместо обычного имени
     * отправителя в reply-блоке.
     */
    private static MessageObject buildOriginalReplyObject(int account, long did, int mid) {
        String k = key(did, mid);
        MessageObject cached = originalReplyCache.get(k);
        if (cached != null) {
            return cached;
        }
        try {
            ArrayList<TLRPC.Message> versions = ReExteraDb.get().listVersionsOfEditedMessage(did, mid);
            if (versions == null || versions.isEmpty()) {
                return null;
            }
            TLRPC.Message original = versions.get(0);
            if (original == null || original.message == null) {
                return null;
            }
            TLRPC.TL_message synthetic = new TLRPC.TL_message();
            synthetic.message = original.message;
            synthetic.id = original.id;
            synthetic.out = original.out;
            synthetic.date = original.date;
            TLRPC.TL_peerUser fromUser = new TLRPC.TL_peerUser();
            fromUser.user_id = UserConfig.getInstance(account).getClientUserId();
            synthetic.from_id = fromUser;
            MessageObject obj = new MessageObject(account, synthetic, true, false);
            obj.customReplyName = Localization.EDIT_HISTORY_INLINE_LABEL;
            originalReplyCache.put(k, obj);
            return obj;
        } catch (Throwable e) {
            Main.log("EditHistoryInlineHelper: build failed: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Хук на ChatMessageCell.setMessageObject(MessageObject, ...): если у
     * сообщения есть сохранённая история и текущий текст отличается от
     * исходного, подменяет replyMessageObject на синтетическую цитату.
     * Оригинальный настоящий reply (если он был) не трогаем - подмена
     * происходит только когда своего reply у сообщения ещё нет, чтобы не
     * перекрывать реальный контекст ответа. Если хочется видеть оба -
     * это следующий шаг доработки, сейчас приоритет отдан настоящему reply.
     */
    public static class SetMessageObjectHook extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) {
            if (!Settings.getSaveEditedMessages()) {
                return;
            }
            try {
                if (param.args.length == 0 || !(param.args[0] instanceof MessageObject)) {
                    return;
                }
                MessageObject messageObject = (MessageObject) param.args[0];
                if (messageObject.replyMessageObject != null) {
                    // Не перекрываем настоящий reply синтетическим - приоритет реальному контексту.
                    return;
                }
                if (messageObject.messageOwner == null) {
                    return;
                }
                long did = messageObject.getDialogId();
                int mid = messageObject.getId();
                if (!hasEdits(did, mid)) {
                    return;
                }
                String currentText = messageObject.messageOwner.message;
                MessageObject syntheticReply = buildOriginalReplyObject(messageObject.currentAccount, did, mid);
                if (syntheticReply == null || currentText == null || currentText.equals(syntheticReply.messageOwner.message)) {
                    // Текст не менялся с последней сохранённой версии (или это она и есть) - нечего показывать.
                    return;
                }
                messageObject.replyMessageObject = syntheticReply;
            } catch (Throwable e) {
                Main.log("EditHistoryInlineHelper.SetMessageObjectHook: %s", e.getMessage());
            }
        }
    }
}
