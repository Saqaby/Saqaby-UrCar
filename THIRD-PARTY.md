# ما في سيارتك لغير صاحبه

حقوق سلمان الصقعبي في `LICENSE` لا تشمل ما يلي. كل جزء هنا يبقى على رخصته أو لصاحبه.

| الجزء | أين هو | لمن | الرخصة أو الحال |
|---|---|---|---|
| الخط IBM Plex Sans Arabic (ملفاته باسم Thmanyah) | `assets/fonts/`، `android/app/src/main/assets/fonts/` | IBM | SIL Open Font License 1.1 (`IBMPlexSansArabic-OFL.txt`) |
| علامات Noto Naskh Arabic | `assets/fonts/` | Google | SIL Open Font License 1.1 (`NotoNaskhArabic-OFL.txt`) |
| خط حفص العثماني | `assets/fonts/` (المعاينة فقط) | مجمع الملك فهد | بشروطه في `UthmanicHafs-NOTICE.txt`؛ لا يُعدَّل |
| نصوص الآيات والأحاديث وأقوال السلف | `assets/shared/`، `android/app/src/main/assets/quotes.json` | مأخوذة من UrTV كما هي | انظر «UrTV» أدناه |
| آيتا ذكر الركوب (الزخرف ١٣–١٤) | `android/app/src/main/assets/duas.json` | نص Tanzil البسيط، عبر fawazahmed0/quran-api وsemarketir/quranjson | شروط Tanzil: النقل حرفيًا مع ذكر المصدر tanzil.net |
| دعاء السفر | `android/app/src/main/assets/duas.json` | من حديث سنن أبي داود ٢٥٩٨ كما في UrTV | نص نبوي؛ مصدر المجموعة المنقول منها انظر «UrTV» |
| عدد آيات السور | `android/app/src/main/assets/khatma.json` | fawazahmed0/quran-api | بيانات عامة |
| فهرس القرّاء وأسماء السور | `android/app/src/main/assets/khatma.json` | UrTV، وروابطه من mp3quran.net | انظر «UrTV» و«التلاوات» |
| التلاوات | تُبَث من cdn.mp3quran.net ولا تُحفظ في المستودع | mp3quran.net وأصحابها | بشروط mp3quran.net؛ لم نقرأها بعد (الموقع محجوب في بيئة العمل) |
| إذاعة القرآن الكريم السعودية | تُبَث من stream.radiojar.com | هيئة الإذاعة والتلفزيون | بث مباشر؛ لا نعيد نشره |
| الأذان (عبدالملك الملا) | يُنزَّل من archive.org | مجموعة «Azaan of World Mosques» | Public Domain Mark 1.0 |
| أسماء المدن وحدود السرعة | تُطلب من nominatim.openstreetmap.org و overpass-api.de | OpenStreetMap | ODbL: يُذكر «© OpenStreetMap contributors» (مذكور في إعدادات التطبيق) |
| بيانات الطقس والغبار | تُطلب من open-meteo.com | Open-Meteo | CC BY 4.0: يُذكر «بيانات الطقس من Open-Meteo.com» (مذكور في إعدادات التطبيق) |
| ترددات FM | `assets/shared/fm-quran.json` | مصادر عامة مذكورة في الملف | معلومات عامة |
| تسجيلا ذكر الركوب ودعاء السفر | `android/app/src/main/assets/dua/` | صنعهما صاحب المشروع بأداة ذكاء اصطناعي | قدّمهما خاليين من حقوق الغير؛ تشملهما `LICENSE` |

## UrTV

أجزاء من UrCar منقولة من [Saqaby/UrTV](https://github.com/Saqaby/UrTV)، وهو نفسه نسخة معدّلة من `tshi-dev/urtv`:
- حساب المواقيت بطريقة ISNA (`android/app/src/main/java/sa/urcar/home/PrayerMath.java`، ودوال المعاينة في `src/template.html`).
- دوال تجهيز المحتوى (`src/content.py`)، وأيقونات الطقس، ومنطق التذكير بالصيام.
- بيانات الآيات والأحاديث وأقوال السلف، وفهرس القرّاء.

**مستودع UrTV ليس فيه رخصة.** فحقوق أجزائه الأصلية لأصحابها، وتعديلات Saqaby عليه له.
وجزء الواجهة الرئيسية في UrTV مقتبس من [mihirgupta0900/tv-home](https://github.com/mihirgupta0900/tv-home) برخصة MIT،
ولم يُنقل منه شيء إلى UrCar.

**قبل النشر على نطاق واسع:** اطلب إذن صاحب `tshi-dev/urtv` لما نُقل من أجزائه الأصلية، أو أعد كتابتها.
ولم نحدّد بعد بالتفصيل أي الملفات المنقولة كتبها هو وأيها كتبها Saqaby.
