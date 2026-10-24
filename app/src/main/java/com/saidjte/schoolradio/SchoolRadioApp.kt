package com.saidjte.schoolradio

import android.app.Application
import com.saidjte.schoolradio.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * فئة التطبيق: تهيئة الحاقن الاعتمادي + زرع التصنيفات الافتراضية +
 * فحص توفر الملفات المستوردة + إعادة ضبط المنبهات عند بدء العملية.
 * كل ذلك محلي 100% — لا يلمس الشبكة إطلاقاً.
 */
class SchoolRadioApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.appScope.launch {
            runCatching {
                // v0.3.0: زرع المشرف الافتراضي عند أول تشغيل (بعد تثبيت نظيف فقط)
                ServiceLocator.session.ensureDefaultUser()
                ServiceLocator.library.ensureDefaultCategories()
                // v0.14.0: زرع الجينلات المدمجة للفواصل (تصنيف «فواصل وجينلات») —
                // تظهر مباشرة في محرر الفواصل والحائط الصوتي دون أي استيراد يدوي
                ServiceLocator.library.ensureBundledJingles()
                ServiceLocator.library.refreshAvailability()
                // عند بدء العملية: أعد ضبط المنبهات (يغطي أيضًا الحالة بعد القتل القسري)
                val enabled = ServiceLocator.schedules.enabledSchedules().map { it.id }.toSet()
                val events = ServiceLocator.schedules.allEvents().filter { it.scheduleId in enabled }
                ServiceLocator.scheduler.rescheduleAll(events)
                // v0.13.0: إحياء خدمة الأتمتة الحارسة مع كل فتح للعملية —
                // تضمن انطلاق الفقرات أوتوماتيكياً حتى لو انقلبت المنبهات
                if (enabled.isNotEmpty()) {
                    com.saidjte.schoolradio.scheduler.AutomationService.start(this@SchoolRadioApp)
                }
            }
        }
    }
}
