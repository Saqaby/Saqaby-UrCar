# سيارتك · UrCar

واجهة رئيسية عربية لشاشة أندرويد في السيارة (head unit): المواقيت والأذان والتاريخ الهجري، الختمة والمكتبة الصوتية
وإذاعة القرآن، السرعة والبوصلة والقبلة وحد السرعة، الأذكار عند الركوب والسفر ودخول المنزل، وبطاقة آية أو حديث أو قول كل ساعة.

## التطبيق على الشاشة (android/app)

- يُبنى في GitHub Actions (`app-apk.yml`) وينشر الإصدار `app-latest`:
  https://github.com/Saqaby/Saqaby-UrCar/releases/download/app-latest/urcar.apk
- يحتاج سرّين في المستودع (Settings ← Secrets and variables ← Actions): `URCAR_KEYSTORE_B64` و`URCAR_KEYSTORE_PASSWORD`.
  مفتاح التوقيع لا يدخل المستودع أبدًا.
- يحدّث نفسه من الإصدار نفسه كل ٦ ساعات أو من «تحديث الآن».
- تطبيق الفحص في `android/probe` (`probe-apk.yml`).

## الحقوق

© 2026 [سلمان الصقعبي (Salman Alsaqabi)](https://www.linkedin.com/in/%D8%B3%D9%84%D9%85%D8%A7%D9%86-%D8%A7%D9%84%D8%B5%D9%82%D8%B9%D8%A8%D9%8A-78a098159). جميع الحقوق محفوظة: الاستخدام الشخصي مجاني، والنسخ والتعديل وإعادة النشر بإذن كتابي فقط. التفاصيل في [`LICENSE`](LICENSE).
ما في المشروع لغير صاحبه (الخطوط، النصوص ومصادرها، الخرائط، الطقس، التلاوات) مذكور برخصه في [`THIRD-PARTY.md`](THIRD-PARTY.md).
