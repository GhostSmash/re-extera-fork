package ni.shikatu.re_extera.hooks.navigation;

import com.exteragram.messenger.drawer.DrawerMenuView;
import de.robv.android.xposed.XC_MethodHook;
import ni.shikatu.re_extera.utils.GhostMenuHelper;
import org.telegram.ui.ActionBar.BaseFragment;

public class DrawerMenuGhostHook extends XC_MethodHook {
    public void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
        Object obj = param.thisObject;
        if (!(obj instanceof DrawerMenuView)) {
            return;
        }
        DrawerMenuView drawerMenuView = (DrawerMenuView) obj;
        int currentAccount = ((Integer) param.args[0]).intValue();
        BaseFragment fragment = (BaseFragment) param.args[1];
        GhostMenuHelper.injectIntoDrawer(drawerMenuView, currentAccount, fragment);
        // Вставляем пункт "Настройки" сразу после призрака. Т.к. GhostMenuHelper
        // мог не вставить свой пункт (если призрак скрыт настройкой), используем
        // текущее количество children контейнера как безопасный ориентир конца списка.
        ni.shikatu.re_extera.utils.SettingsMenuHelper.injectIntoDrawer(drawerMenuView, currentAccount, getContainerChildCount(drawerMenuView));
    }

    private static int getContainerChildCount(DrawerMenuView drawerMenuView) {
        try {
            java.lang.reflect.Field f = DrawerMenuView.class.getDeclaredField("container");
            f.setAccessible(true);
            android.widget.LinearLayout container = (android.widget.LinearLayout) f.get(drawerMenuView);
            return container != null ? container.getChildCount() : 0;
        } catch (Throwable e) {
            return 0;
        }
    }
}
