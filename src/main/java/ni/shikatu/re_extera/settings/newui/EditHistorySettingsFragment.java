package ni.shikatu.re_extera.settings.newui;

import android.view.View;
import com.exteragram.messenger.preferences.utils.SettingsRegistry;
import java.util.ArrayList;
import ni.shikatu.re_extera.db.ReExteraDb;
import ni.shikatu.re_extera.localization.Localization;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/**
 * Отдельный экран "История правок" в разделе Шпион. Тогл наверху
 * включает/выключает саму запись истории (ProcessUpdates.processEditedMessage
 * читает Settings.getSaveEditedMessages()); когда выключено, экран показывает
 * только этот тогл, когда включено - разворачивается статистика (количество
 * отслеживаемых сообщений, занятое место) и действия (очистить историю,
 * сохранять ли собственные правки).
 */
public class EditHistorySettingsFragment extends BasePreferencesActivityExtended {
    private boolean statsLoaded;
    private int trackedMessageCount;
    private long storageBytes;

    private enum EditHistoryIds {
        TOGGLE_ID,
        SAVE_SELF_EDITS_ID,
        STORAGE_INFO_ID,
        CLEAR_HISTORY_ID;

        public int getId() {
            return ordinal() + 1;
        }
    }

    public String getTitle() {
        return Localization.EDIT_HISTORY_MENU_BUTTON;
    }

    private UItem toggleUItem() {
        UItem item = UItem.asExteraExpandableSwitch(EditHistoryIds.TOGGLE_ID.getId(), Localization.MESSAGE_HISTORY_TOGGLE, null, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onToggleClicked();
            }
        });
        item.setChecked(Settings.getSaveEditedMessages());
        item.setCollapsed(!Settings.getSaveEditedMessages());
        item.clickCallback = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onToggleClicked();
            }
        };
        return item;
    }

    private void onToggleClicked() {
        boolean newValue = !Settings.getSaveEditedMessages();
        Settings.setSaveEditedMessages(newValue);
        if (newValue) {
            refreshStats();
        }
        this.listView.adapter.update(true);
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(toggleUItem().setLinkAlias("reExteraEditHistoryToggle", this));
        if (Settings.getSaveEditedMessages()) {
            items.add(UItem.asCheck(EditHistoryIds.SAVE_SELF_EDITS_ID.getId(), Localization.SAVE_SELF_EDITS).setChecked(Settings.getSaveSelfEdits()).setLinkAlias("reExteraSaveSelfEdits", this));
            items.add(UItem.asShadow());

            if (!this.statsLoaded) {
                refreshStats();
            }
            String storageLine = Localization.EDIT_HISTORY_STORAGE_USED + ": " + AndroidUtilities.formatFileSize(this.storageBytes);
            String trackedLine = Localization.EDIT_HISTORY_TRACKED_MESSAGES + ": " + this.trackedMessageCount;
            items.add(UItem.asHeader(storageLine + "  •  " + trackedLine));

            items.add(UItem.asButton(EditHistoryIds.CLEAR_HISTORY_ID.getId(), Localization.CLEAR_EDIT_HISTORY).setLinkAlias("reExteraClearEditHistory", this));
        }
    }

    private void refreshStats() {
        ReExteraDb.get().getMessageEditsStatsAsync(new java.util.function.Consumer<ReExteraDb.MessageEditsStats>() {
            @Override
            public void accept(ReExteraDb.MessageEditsStats stats) {
                EditHistorySettingsFragment.this.statsLoaded = true;
                EditHistorySettingsFragment.this.trackedMessageCount = stats.distinctMessageCount;
                EditHistorySettingsFragment.this.storageBytes = stats.totalBytes;
                if (EditHistorySettingsFragment.this.listView != null) {
                    EditHistorySettingsFragment.this.listView.adapter.update(true);
                }
            }
        });
    }

    public void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == EditHistoryIds.SAVE_SELF_EDITS_ID.getId()) {
            Settings.setSaveSelfEdits(!Settings.getSaveSelfEdits());
            refreshCheckBox(item, position, Settings.getSaveSelfEdits());
        } else if (id == EditHistoryIds.CLEAR_HISTORY_ID.getId()) {
            showClearHistoryDialog();
        }
    }

    private void showClearHistoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(Localization.CLEAR_EDIT_HISTORY + "?");
        builder.setMessage(Localization.CLEAR_EDIT_HISTORY_CONFIRM);
        builder.setPositiveButton(Localization.YES, new AlertDialog.OnButtonClickListener() {
            @Override
            public void onClick(AlertDialog dialog, int which) {
                dialog.dismiss();
                ReExteraDb.get().clearMessageEditsOnlyAsync(new Runnable() {
                    @Override
                    public void run() {
                        statsLoaded = false;
                        trackedMessageCount = 0;
                        storageBytes = 0;
                        if (listView != null) {
                            listView.adapter.update(true);
                        }
                        BulletinFactory.of(EditHistorySettingsFragment.this).createSuccessBulletin(Localization.CLEAR_EDIT_HISTORY).show();
                    }
                });
            }
        });
        builder.setNegativeButton(Localization.NO, new AlertDialog.OnButtonClickListener() {
            @Override
            public void onClick(AlertDialog dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }
}
