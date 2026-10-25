package com.saidjte.schoolradio.core.ui

/**
 * v0.21.0 — دعم الوضع الأفقي: منطق التخطيط التكيفي (صافٍ قابل للاختبار).
 *
 * القاعدة المتبعة (Material 3 adaptive):
 *  - الشاشات العريضة (أجهزة لوحية بعرض 600dp+ حتى في العمودي، أو أي هاتف مُدار أفقياً)
 *    تستخدم تنقلاً جانبياً [useSideNavigation] بدل الشريط السفلي — الشريط السفلي
 *    يستهلك ارتفاعاً ثميناً في الأفقي، والجانبي يستغل العرض الفائض.
 *  - المحتوى (NavHost) يبقى مؤلفاً دائماً في نفس الموضع بالشجرة حتى لا يفقد
 *    حالته عند الدوران — الدوران يغيّر الحاوية لا المحتوى.
 */
object OrientationLogic {

    /** عتبة الأجهزة اللوحية/سطح المكتب (Material 3: medium window يبدأ عند 600dp) */
    const val TABLET_MIN_WIDTH_DP = 600

    /**
     * هل نستخدم تنقلاً جانبياً (Rail) بدل الشريط السفلي؟
     *  - أي شاشة عرضها 600dp+ (لوحية عمودية أو هاتف أفقي)
     *  - أو الشاشة مُدارة أفقياً مهما كان عرضها (هواتف صغيرة قديمة 320dp عمودياً)
     */
    fun useSideNavigation(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= TABLET_MIN_WIDTH_DP || widthDp > heightDp

    /** هل الجهاز مُدار أفقياً؟ (للتخطيطات ثنائية الأبعاد داخل الشاشات) */
    fun isLandscape(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp

    /**
     * الارتفاع التقديري لبند واحد في القائمة الجانبية المضغوطة (dp):
     * أيقونة 22 + تسمية ~11 + حشوة عمودية 10 + هوامش 4 — يُستخدم لضمان أن
     * 7 بنود تتسع في ارتفاع هاتف أفقي (~360dp) مع تباعد متساوٍ.
     */
    const val RAIL_ITEM_ESTIMATED_HEIGHT_DP = 47

    /** هل تتسع قائمة جانبية مضغوطة بـ[itemCount] بنداً في ارتفاع [availableHeightDp]؟ */
    fun compactRailFits(itemCount: Int, availableHeightDp: Int): Boolean =
        itemCount * RAIL_ITEM_ESTIMATED_HEIGHT_DP <= availableHeightDp
}
