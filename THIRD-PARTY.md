# ما في سيارتك لغير صاحبه

حقوق سلمان الصقعبي في `LICENSE` لا تشمل ما يلي. كل جزء هنا يبقى على رخصته أو لصاحبه.

| الجزء | أين هو | لمن | الرخصة أو الحال |
|---|---|---|---|
| الخط IBM Plex Sans Arabic (ملفاته باسم Thmanyah) | `assets/fonts/`، `android/app/src/main/assets/fonts/` | IBM | SIL Open Font License 1.1 (`IBMPlexSansArabic-OFL.txt`) |
| علامات Noto Naskh Arabic | `assets/fonts/` | Google | SIL Open Font License 1.1 (`NotoNaskhArabic-OFL.txt`) |
| خط حفص العثماني | `assets/fonts/` (المعاينة فقط) | مجمع الملك فهد | بشروطه في `UthmanicHafs-NOTICE.txt`؛ لا يُعدَّل |
| الآيات | `android/app/src/main/assets/quotes.json` (`src/quotes/verses.txt`) | نص Tanzil البسيط عبر fawazahmed0/quran-api | شروط Tanzil: النقل حرفيًا مع ذكر المصدر tanzil.net |
| الأحاديث | `quotes.json` (`src/quotes/hadith.txt`) | صحيح البخاري وصحيح مسلم عبر fawazahmed0/hadith-api | ملكية عامة (Unlicense) |
| أقوال السلف | `quotes.json` (`src/quotes/salaf.json`) | الكتب المذكورة مع كل قول (بالجزء والصفحة)، وُجدت في turath.io | نصوص تراثية؛ المصدر مذكور مع كل قول |
| آيتا ذكر الركوب (الزخرف ١٣–١٤) | `android/app/src/main/assets/duas.json` | نص Tanzil البسيط، عبر fawazahmed0/quran-api وsemarketir/quranjson | شروط Tanzil: النقل حرفيًا مع ذكر المصدر tanzil.net |
| دعاء السفر | `android/app/src/main/assets/duas.json` | سنن أبي داود ٢٥٩٨ عبر fawazahmed0/hadith-api | ملكية عامة (Unlicense) |
| عدد آيات السور | `android/app/src/main/assets/khatma.json` | fawazahmed0/quran-api | بيانات عامة |
| فهرس القرّاء وأسماء السور | `android/app/src/main/assets/khatma.json` (`src/khatma_for_app.py`) | واجهة mp3quran.net البرمجية | بيانات عامة من مصدر التلاوات |
| التلاوات | تُبَث من cdn.mp3quran.net ولا تُحفظ في المستودع | mp3quran.net وأصحابها | بشروط mp3quran.net؛ لم نقرأها بعد (الموقع محجوب في بيئة العمل) |
| إذاعة القرآن الكريم السعودية | تُبَث من stream.radiojar.com | هيئة الإذاعة والتلفزيون | بث مباشر؛ لا نعيد نشره |
| الأذان (عبدالملك الملا) | يُنزَّل من archive.org | مجموعة «Azaan of World Mosques» | Public Domain Mark 1.0 |
| حدود السرعة (assets/limits.bin) | من مستخرج Geofabrik لخرائط OpenStreetMap عبر src/limits_for_app.py، يُبنى شهريًا | OpenStreetMap | ODbL: يُذكر «© OpenStreetMap contributors» (مذكور في إعدادات التطبيق)، والملف قاعدة بيانات مشتقة متاحة بالترخيص نفسه في هذا المستودع |
| المساجد (assets/mosques.bin) | من المستخرج نفسه عبر src/mosques_for_app.py، يُبنى شهريًا | OpenStreetMap | ODbL كحدود السرعة: قاعدة بيانات مشتقة متاحة بالترخيص نفسه في هذا المستودع |
| أسماء المدن (assets/cities.json) | GeoNames عبر src/cities_for_app.py | GeoNames.org | CC BY 4.0: يُذكر المصدر (مذكور في إعدادات التطبيق) |
| بيانات الطقس والغبار | تُطلب من open-meteo.com | Open-Meteo | CC BY 4.0: يُذكر «بيانات الطقس من Open-Meteo.com» (مذكور في إعدادات التطبيق) |
| ترددات FM | `assets/shared/fm-quran.json` | مصادر عامة مذكورة في الملف | معلومات عامة |
| تسجيلا ذكر الركوب ودعاء السفر | `android/app/src/main/assets/dua/` | صنعهما صاحب المشروع بأداة ذكاء اصطناعي | قدّمهما خاليين من حقوق الغير؛ تشملهما `LICENSE` |

## UrTV

بدأ المشروع بأجزاء منقولة من Saqaby/UrTV (وهو نسخة معدّلة من `tshi-dev/urtv`، بلا رخصة). أُعيدت كتابتها كلها في نوفمبر ٢٠٢٦،
فلم يبق في UrCar شيء منه:
- حساب المواقيت (`PrayerMath.java`): مكتوب من جديد من معادلات NOAA المنشورة للشمس، وطابق الحساب السابق في حدود دقيقة.
- الآيات والأحاديث وأقوال السلف: اختيار جديد من مصادره (انظر الجدول أعلاه).
- فهرس القرّاء وأسماء السور: من واجهة mp3quran.net مباشرة.
- المعاينة القديمة (`src/template.html` و`src/build.py` و`src/content.py` و`dist/`) وبياناتها: حُذفت.
- الخطوط: IBM Plex Sans Arabic بأسمائها الأصلية (OFL).
- رابط الأذان ورابط بث الإذاعة: عنوانان عامّان، مصدرهما Internet Archive وradiojar، لا شيء من كود UrTV.
