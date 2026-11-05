# شاشة المالك: ما ثبت (فحص 2026-11-05، التقرير في device/probe-report.txt)

| البند | الثابت | المصدر |
|---|---|---|
| النظام | أندرويد 10 (SDK 29)، رقم الإصدار QP1A.191105.004؛ «16» في الإعدادات غير صحيح | الفحص + صورة «لمحة عن الجهاز» |
| المعالج | Allwinner A133 (sun50iw10p1، ceres-b6) | الفحص |
| الذاكرة | 4 غيغابايت (3972 MB)، ليست low-ram | الفحص |
| الشاشة | 1280×720، 160 dpi (density 1.0) | الفحص |
| الواجهة الحالية | `com.dofun.variety/com.dofun.overseasvariety.Launcher` | الفحص |
| الملاحة | خرائط Google، Waze، iGO (`com.nng.igo.primong.igoworld`) | الفحص |
| الصوت | `com.tw.music`، يوتيوب، Seen (`com.incube.seen2`)، zlink (`com.zjinnova.zlink`) | الفحص |

## الملاحة داخل الواجهة
- **النوافذ الحرّة غير مدعومة** (`freeform_feature=false`)، و«الخرائط في نافذة» فتحت بملء الشاشة. الخطة أ مستبعدة.
- **صورة داخل صورة مدعومة** (`picture_in_picture=true`). خرائط Google تدخلها وحدها أثناء الملاحة عند الضغط على الرئيسية. لم يُجرَّب بعد.
- القرار المبدئي: بطاقة المنعطف من إشعار تطبيق الملاحة + الضغط يفتح التطبيق؛ وصورة داخل صورة لخرائط Google إن نجحت التجربة. تجربة الإشعار في فحص v2.

## الكاميرا
- `com.tw.reverse/cn.cardoor.desktop.window.floating.e` لم يفتح شيئًا (ثلاث محاولات).
- أندرويد يرى 7 كاميرات (Camera2) و4 أجهزة `/dev/video`؛ قراءة خصائصها أعطت CameraAccessException بلا إذن الكاميرا.
- مرشّحون لم يُجرَّبوا: `com.tw.service/.ReverseActivity` و`.ReverseNissanActivity`، `com.tw.carinfoservice/.lrreverse.LRReverseActivity`، `com.tw.rightview/.LRReverseActivity`، `cn.cardoor.zt360`.
- فحص v2: زر «فتح كاميرا الشاشة» صار يشملهم، وزر «الكاميرا داخل التطبيق» يعرض كل كاميرا بنفسه. إن ظهرت الصورة فالواجهة تعرض الكاميرا بنفسها في أي وضع لناقل الحركة.

## الراديو
- `com.tw.radio/.RadioActivity` يفتح الراديو (ثبت). `.activity.PTYActivity` فتح أيضًا.
- لا يوجد في `com.tw.radio` مستقبِل (receiver) مكشوف؛ فيه خدمة `RadioService` مكشوفة لا نعرف واجهتها.
- استنتاج: ضبط التردد من تطبيقنا غير متاح بطريقة معروفة. القرار المبدئي: زر الإذاعة يفتح راديو الشاشة ويعرض التردد المقترح ليُحفظ مرة في ذاكرة الراديو.
  فحص v2 يرى هل الراديو ينشر media session (فيُتحكم به من صفحة الموسيقى).

## لا يُلمس
`com.tw.car`، `com.tw.carchoose` (فيه MCUActivity وتحديث CAN)، `com.tw.carinfoservice`، `com.tw.uart`، `com.tw.service.xt`: تتصل بالسيارة.
