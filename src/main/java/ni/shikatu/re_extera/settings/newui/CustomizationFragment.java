package ni.shikatu.re_extera.settings.newui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import ni.shikatu.re_extera.Defaults;
import ni.shikatu.re_extera.hooks.chatmessagecell.MeasureTime;
import ni.shikatu.re_extera.localization.Localization;
import ni.shikatu.re_extera.settings.Settings;
import org.telegram.ui.Cells.EditTextSettingsCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

public class CustomizationFragment extends BasePreferencesActivityExtended {

    private enum CustomizationIds {
        DISABLE_COLORED_REPLIES_ID,
        TRANSPARENT_DELETED_MESSAGES_ID,
        RED_DELETED_MARK_ID,
        CUSTOM_DELETED_MARK_ID;

        public int getId() {
            return ordinal() + 1;
        }
    }

    @Override
    public String getTitle() {
        return Localization.CUSTOMIZATION;
    }

    private FrameLayout customMarkView() {
        FrameLayout frameLayout = new FrameLayout(getContext());
        EditTextSettingsCell customPrefix = new EditTextSettingsCell(getContext());
        customPrefix.setTextAndHint(Settings.getCustomPrefix(), Localization.LEAVE_BLANK_FOR_RECYCLE, false);
        customPrefix.getTextView().addTextChangedListener(new TextWatcher() { 
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Settings.setCustomPrefix(s.toString());
                MeasureTime.notifyMarkChanged(Settings.getCustomPrefix());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        frameLayout.addView(customPrefix);
        return frameLayout;
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(CustomizationIds.DISABLE_COLORED_REPLIES_ID.getId(), Localization.DISABLE_COLORED_REPLIES).setChecked(Settings.getDisableColoredReplies()).setLinkAlias("reExteraDisableColoredReplies", this));
        items.add(UItem.asShadow());

        items.add(UItem.asCheck(CustomizationIds.TRANSPARENT_DELETED_MESSAGES_ID.getId(), Localization.ENABLE_ALPHA).setChecked(Settings.getTransparentDeletedMessages()).setLinkAlias("reExteraTransparentDeletedMessages", this));
        items.add(UItem.asCheck(CustomizationIds.RED_DELETED_MARK_ID.getId(), Localization.RED_DELETED_MARK).setChecked(Settings.getRedMark()).setLinkAlias("reExteraRedDeletedMark", this));
        items.add(UItem.asHeader(Localization.CUSTOM_PREFIX));
        items.add(UItem.asCustom(CustomizationIds.CUSTOM_DELETED_MARK_ID.getId(), customMarkView()).setLinkAlias("reExteraCustomDeletedMark", this));
        items.add(UItem.asShadow());
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id <= 0 || item.id > CustomizationIds.values().length) {
            return;
        }
        CustomizationIds clicked = CustomizationIds.values()[item.id - 1];
        switch (clicked) {
            case DISABLE_COLORED_REPLIES_ID:
                Settings.setDisableColoredReplies(!Settings.getDisableColoredReplies());
                refreshCheckBox(item, position, Settings.getDisableColoredReplies());
                break;
            case TRANSPARENT_DELETED_MESSAGES_ID:
                Settings.setTransparentDeletedMessages(!Settings.getTransparentDeletedMessages());
                refreshCheckBox(item, position, Settings.getTransparentDeletedMessages());
                break;
            case RED_DELETED_MARK_ID:
                Settings.setRedMark(!Settings.getRedMark());
                refreshCheckBox(item, position, Settings.getRedMark());
                break;
        }
    }
}
