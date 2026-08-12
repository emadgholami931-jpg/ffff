# flashcard

یک اپ شخصی فلش‌کارت انگلیسی به فارسی برای Android.

## امکانات

- افزودن کلمه انگلیسی به‌صورت دستی
- ورود گروهی کلمات از CSV
- ساخت خودکار معنی فارسی، IPA، مثال انگلیسی و ترجمه مثال با Google Gemini
- ورق‌زدن کارت و Swipe برای «یاد گرفتم / یاد نگرفتم»
- مرور زمان‌بندی‌شده با spaced repetition
- تلفظ با Android TTS
- ذخیره محلی کارت‌ها با Room
- اجرای تکمیل کارت‌ها در پس‌زمینه با WorkManager
- صفحه تنظیمات برای وارد کردن Google Gemini API Key هر کاربر
- رمزگذاری API Key با Android Keystore؛ کلید داخل سورس، APK یا GitHub قرار نمی‌گیرد
- آیکون برنامه از تصویر `docs/app-icon-original.jpg` ساخته شده است

## استفاده از Gemini API Key

بعد از نصب برنامه:

1. تب «تنظیمات» را باز کنید.
2. Google Gemini API Key خود را وارد کنید.
3. «ذخیره کلید» را بزنید.
4. از آن پس کارت‌های جدید مستقیماً از همین گوشی با Gemini تکمیل می‌شوند.

کلید در SharedPreferences به‌صورت رمز‌شده ذخیره می‌شود و کلید رمزنگاری در Android Keystore نگهداری می‌شود. Backup برنامه غیرفعال شده است تا Secret به Backup منتقل نشود.

## مدل Gemini

مدل پیش‌فرض داخل برنامه:

```text
gemini-3.5-flash
```

درخواست‌ها مستقیم به Gemini Developer API فرستاده می‌شوند و خروجی به‌صورت JSON ساخت‌یافته شامل این فیلدها دریافت می‌شود:

```text
word
ipa
translationFa
exampleEn
exampleFa
```

## ساخت APK در GitHub

Workflow آماده در مسیر زیر وجود دارد:

```text
.github/workflows/android-apk.yml
```

با هر Push به شاخه `main`، GitHub Actions تست‌ها را اجرا و APK Debug را می‌سازد. Artifact با نام زیر منتشر می‌شود:

```text
Flashcard-APK
```

و فایل داخل Artifact:

```text
flashcard-debug.apk
```

برای Build هیچ API Key یا GitHub Secret لازم نیست؛ هر کاربر کلید خودش را بعد از نصب در داخل برنامه وارد می‌کند.

## مشخصات Android

- Kotlin + Jetpack Compose
- Room
- WorkManager
- minSdk 23
- compileSdk 36
- targetSdk 36
- Java 17

## CSV نمونه

فایل `sample_words.csv` در ریشه پروژه قرار دارد. فایل CSV می‌تواند یک ستون `word` داشته باشد یا بدون header باشد؛ در حالت بدون header ستون اول خوانده می‌شود.

## امنیت

API Key را در GitHub، کد برنامه یا فایل‌های پروژه Commit نکنید. کلید باید فقط توسط خود کاربر از داخل صفحه تنظیمات وارد شود.
