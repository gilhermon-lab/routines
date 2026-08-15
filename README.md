# שגרות — Routines

מצבים ושגרות למכשיר אישי, עם דחיית שיחות אוטומטית, משוב ב-SMS, ופריצה בחיוג חוזר.

## בנייה בלי מחשב — GitHub Actions

הפרויקט כולל `.github/workflows/build.yml` שבונה APK בענן ומפרסם אותו כ-Release.

מ-Termux (F-Droid), פעם אחת:

```
pkg install git unzip
unzip routines-android-project.zip && cd routines
git init && git branch -M main
git add . && git commit -m "first"
git remote add origin https://github.com/<user>/<repo>.git
git push -u origin main
```

הדחיפה מבקשת סיסמה — שם שם Personal Access Token עם הרשאת `repo`
(github.com → Settings → Developer settings → Tokens).

אחרי כשלוש דקות ה-APK יופיע בעמוד Releases של המאגר, בתגית `latest`.
מורידים ומתקינים ישירות מהטלפון. כל `git push` הבא בונה מחדש.

## בנייה במחשב

1. פתח את התיקייה ב-Android Studio (Ladybug ומעלה).
2. הסכם ל-Gradle sync.
3. Run על מכשיר עם Android 10 (API 29) ומעלה.

`minSdk = 29` כי `CallScreeningService.setSilenceCall` קיים רק מ-API 29.

אם Android Studio מתלונן על wrapper חסר — `gradle wrapper --gradle-version 8.9` בתיקיית השורש, או פשוט תן ל-Studio לייצר אותו.

## הפעלה ראשונה

במסך הראשי יש רשימת הרשאות. סדר מומלץ:

| הרשאה | איפה | פותחת |
|---|---|---|
| סינון שיחות | בקשה באפליקציה | דחייה והשתקה |
| SMS ואנשי קשר | בקשה באפליקציה | משוב אוטומטי, חריגת אנשי קשר |
| נא לא להפריע | מסך הגדרות | DND |
| שינוי הגדרות מערכת | מסך הגדרות | בהירות |
| התראות מדויקות | מסך הגדרות | מעבר מדויק בין מצבים |
| הגדרות מוגנות | ADB בלבד | מצב טיסה, גווני אפור |

ההרשאה האחרונה בלי מחשב — מאנדרואיד 11 ומעלה יש ניפוי באגים אלחוטי,
ואפליקציות כמו Shizuku או LADB מריצות פקודות ADB על המכשיר עצמו:

```
pm grant com.gil.routines android.permission.WRITE_SECURE_SETTINGS
```

ההרשאה שורדת אתחולים ועדכוני אפליקציה, אבל **נמחקת בהסרה והתקנה מחדש**.
ב-Shizuku צריך להפעיל מחדש את השירות אחרי כל אתחול של הטלפון.

## מבנה

```
data/Models.kt         מודל המצבים, חלונות בדיוק של דקה, סריאליזציה ב-org.json
data/ModeStore.kt      שמירה ב-SharedPreferences + ברירות מחדל בעברית
engine/RoutineEngine   אילו מצבים פעילים כרגע
engine/RoutineScheduler תזמון ב-AlarmManager לפי ZonedDateTime מלא
engine/ModeApplier     יישום DND, השתקה, בהירות, ופעולות ה-ADB
call/CallGuardService  ה-CallScreeningService — הליבה
call/CallAttemptLog    ספירת חיוגים חוזרים ומרווח בין הודעות
ui/                    Compose, RTL, כרטיס הרשאות ומסך עריכה
```

## נקודות שחשוב להכיר

**תפקיד סינון השיחות הוא בלעדי.** רק אפליקציה אחת במכשיר יכולה להחזיק אותו. אם סמסונג או Google Phone מחזיקות בו, המשתמש יעביר אותו ידנית דרך הבקשה באפליקציה.

**חלון שחוצה חצות שייך ליום שבו התחיל.** שגרת "ראשון 22:45–06:30" ממשיכה לפעול בשתיים לפנות בוקר ביום שני. זה מטופל ב-`Mode.isActiveAt`.

**שעון קיץ.** התזמון מחושב כ-`ZonedDateTime` מלא ולא כשעה ביום, אחרת המעבר היה מזיז את כל השגרות בשעה.

**מספר חסוי.** כשאין `handle`, השיחה עוברת כרגיל — אין למי לשלוח הודעה ואין מה לספור.

**קו נייח.** ההודעה תישלח ותיעלם. שווה להוסיף בדיקת קידומת לפני שליחה.

**עלות.** כל דחייה שולחת SMS אמיתי. מרווח ההודעות (`smsCooldownHours`) קיים בדיוק בשביל זה.

## מה עוד לא נמצא כאן

- טריגרים מלבד שעה: Wi-Fi, מיקום, Bluetooth, יומן, זיהוי נהיגה
- מסנן אור כחול (`Settings.Secure.night_display_activated`)
- Shizuku כתחליף ל-ADB
- מסך היסטוריית שיחות שנחסמו
