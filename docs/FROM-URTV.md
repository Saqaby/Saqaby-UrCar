# ما أُخذ من Saqaby/UrTV (الالتزام 0f214b1، الفرع master)

UrTV لم يُعدَّل. المنسوخ:

| في UrCar | المصدر في UrTV | ملاحظة |
|---|---|---|
| `computePrayers()` في `src/template.html` | `src/template.html` | ISNA (Aladhan method 2، الفجر والعشاء ١٥°)، صار يأخذ الموقع والمنطقة الزمنية الحالية |
| الإقامة، شريط الأذان، «الجمعة» بدل الظهر | `src/template.html` | كما هي |
| التاريخ الهجري (أم القرى) ونافذة رمضان | `tickDates()` و`tickOccasion()` | رمضان فقط، بلا بقية المناسبات |
| آية/حديث/سلف كل ٨ ساعات | `quoteOfDay()` | في السيارة النصوص القصيرة فقط (سطران) |
| `src/content.py` | `src/build.py` (السطور ١–٢٥٨) | دوال تجهيز الآيات والأحاديث كما هي |
| `assets/shared/{verses,hadith,salaf}.json` | `assets/shared/` | |
| `assets/shared/stations.json` | `android/launcher/src/main/assets/quran/stations.json` | أول محطة: إذاعة القرآن الكريم السعودية |
| رابط الأذان | `android/launcher/.../tilawah/Adhan.java` | عبدالملك الملا، Public Domain Mark 1.0 |
| `assets/fonts/` | `assets/fonts/` | IBM Plex Sans Arabic (OFL) بأسماء Thmanyah، وخط حفص العثماني، انظر FONTS-NOTE.txt |

## يُنقل عند كتابة كود أندرويد (لم يُنقل بعد)

- `android/launcher/src/main/java/sa/urtv/home/tilawah/{PrayerMath,PrayerTimes,AdhanScheduler,AdhanReceiver,Adhan,MusicService}.java`
  (حساب المواقيت في Java، جدولة الأذان، audio focus المؤقت ليعود الصوت بعد الأذان، تشغيل الإذاعة).
- `.github/workflows/android.yml` (البناء والتوقيع من أسرار المستودع).
