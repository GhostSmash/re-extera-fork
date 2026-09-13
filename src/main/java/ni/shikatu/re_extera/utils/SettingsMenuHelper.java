package ni.shikatu.re_extera.utils;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import com.exteragram.messenger.drawer.DrawerMenuItemView;
import com.exteragram.messenger.drawer.DrawerMenuView;
import java.lang.reflect.Field;
import ni.shikatu.re_extera.Main;
import ni.shikatu.re_extera.localization.Localization;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Пункт "Настройки re:extera Fork" в бургер-меню (drawer), рядом с пунктом
 * призрака из GhostMenuHelper. Использует тот же паттерн прямой инъекции
 * View в контейнер drawer (а не официальный Python plugin API, который
 * в GhostMenuHelper фактически отключён - см. registerPluginMenuItem).
 *
 * Вызывается из того же хука, что и GhostMenuHelper.injectIntoDrawer
 * (DrawerMenuGhostHook), сразу после вставки пункта призрака, чтобы новый
 * пункт встал рядом с уже вставленным, а не в произвольном месте layout'а.
 */
public final class SettingsMenuHelper {
    private static final int SETTINGS_MENU_ITEM_ID = 910002;
    private static Field drawerContainerField;
    private static Field drawerOnItemClickField;

    private SettingsMenuHelper() {
    }

    /**
     * Вставляет пункт "Настройки" сразу после пункта призрака в том же
     * контейнере. offsetFromGhost позволяет позиционировать относительно
     * уже вставленного childIndex призрака (обычно +1, т.е. сразу под ним).
     */
    public static void injectIntoDrawer(DrawerMenuView drawerMenuView, int currentAccount, int insertAtChildIndex) {
        if (!ni.shikatu.re_extera.hooks.HookInit.isActive) {
            return;
        }
        LinearLayout container = getDrawerContainer(drawerMenuView);
        if (container == null) {
            return;
        }
        // Не дублировать при повторной перестройке меню.
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof DrawerMenuItemView && child.getTag() != null
                    && SETTINGS_TAG.equals(child.getTag())) {
                return;
            }
        }

        int childIndex = Math.min(insertAtChildIndex, container.getChildCount());
        DrawerMenuItemView itemView = new DrawerMenuItemView(drawerMenuView.getContext());
        itemView.setMenuItem(SETTINGS_MENU_ITEM_ID, currentAccount, R.drawable.msg_settings, getTitle());
        itemView.setTag(SETTINGS_TAG);
        final Runnable onItemClick = getDrawerOnItemClick(drawerMenuView);
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClick != null) {
                    onItemClick.run();
                }
                openSettings();
            }
        });
        container.addView(itemView, childIndex);
    }

    private static final String SETTINGS_TAG = "re_extera_fork_settings_menu_item";

    private static String getTitle() {
        // Отдельной локализационной строки под это не заводим - используем
        // короткое фиксированное название, узнаваемое по бренду форка.
        return "re:extera Fork";
    }

    private static void openSettings() {
        try {
            ni.shikatu.re_extera.Main.showSettingsExternal();
        } catch (Throwable e) {
            Main.log("SettingsMenuHelper: failed to open settings: %s", e.getMessage());
        }
    }

    private static LinearLayout getDrawerContainer(DrawerMenuView drawerMenuView) {
        try {
            if (drawerContainerField == null) {
                drawerContainerField = DrawerMenuView.class.getDeclaredField("container");
                drawerContainerField.setAccessible(true);
            }
            return (LinearLayout) drawerContainerField.get(drawerMenuView);
        } catch (Exception e) {
            Main.log("SettingsMenuHelper: failed to access drawer container: %s", e.getMessage());
            return null;
        }
    }

    private static Runnable getDrawerOnItemClick(DrawerMenuView drawerMenuView) {
        try {
            if (drawerOnItemClickField == null) {
                drawerOnItemClickField = DrawerMenuView.class.getDeclaredField("onItemClick");
                drawerOnItemClickField.setAccessible(true);
            }
            return (Runnable) drawerOnItemClickField.get(drawerMenuView);
        } catch (Exception e) {
            Main.log("SettingsMenuHelper: failed to access drawer item callback: %s", e.getMessage());
            return null;
        }
    }
}
