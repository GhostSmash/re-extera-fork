package ni.shikatu.re_extera.utils;

import java.lang.reflect.Method;
import ni.shikatu.re_extera.Main;

/**
 * Устойчивый к изменениям сигнатур поиск методов.
 *
 * Проблема: HookInit раньше искал методы по ТОЧНОЙ сигнатуре
 * (getDeclaredMethod(name, ExactType1.class, ExactType2.class, ...)).
 * Если Telegram/exteraGram меняет порядок параметров, тип одного из них
 * (например int -> long) или добавляет новый параметр, такой поиск
 * ломается полностью и XposedBridge.hookMethod() никогда не вызывается -
 * функция тихо перестаёт работать, хотя UI-тогл продолжает
 * сохранять состояние (это не связанные друг с другом вещи).
 *
 * Решение: искать метод по имени и КОЛИЧЕСТВУ параметров (как это делает
 * рабочий MAX re:extera форк). Это переживает изменение типов параметров
 * и их порядка, пока общее количество не меняется.
 */
public final class HookLookup {
    private HookLookup() {
    }

    /**
     * Находит первый объявленный метод с данным именем и точным
     * количеством параметров. Возвращает null, если ничего не найдено -
     * вызывающий код должен сам логировать это через Main.log(...).
     */
    public static Method findByArgCount(Class<?> clazz, String methodName, int paramCount) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterTypes().length == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /**
     * Находит первый объявленный метод с данным именем и количеством
     * параметров >= minParamCount. Полезно для методов, в которые TG
     * со временем добавляет новые (обычно опциональные/трейлинг) параметры,
     * например deleteMessages, где список параметров может расширяться.
     */
    public static Method findByMinArgCount(Class<?> clazz, String methodName, int minParamCount) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterTypes().length >= minParamCount) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /**
     * Находит метод по одному из нескольких допустимых имён (например
     * "isPremium" ИЛИ "hasPremium" - названия геттеров иногда переименовывают
     * между версиями библиотеки, а поведение остаётся тем же).
     */
    public static Method findByAnyName(Class<?> clazz, int paramCount, String... candidateNames) {
        for (String name : candidateNames) {
            Method m = findByArgCount(clazz, name, paramCount);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    public static void logMiss(String context, Class<?> clazz, String methodName) {
        Main.log("HookLookup: no match for %s.%s (context: %s)", clazz.getName(), methodName, context);
    }
}
