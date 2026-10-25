# Solowise Inquiries — Android starter

A native Kotlin + Jetpack Compose client inbox styled around the client's navy / ivory / antique-gold legal brand.

## Included
- Inquiry list, search, matter filters, detail sheet
- One-tap WhatsApp and Call actions
- Android 13+ notification permission request and notification channel
- Supabase REST read of `inquiries` scoped to `client_id=sharad-kumar-saxena`
- Foreground refresh every 30 seconds; new rows trigger a local notification while the app is running

## Important production requirements
1. **Do not ship this exact direct Supabase client as a production admin inbox.** The website's anon key is public and only safe if Row Level Security (RLS) strictly limits access. Configure authenticated staff accounts and RLS so only authorised staff can read this client's inquiries. Prefer a server/Edge Function that validates the staff session and returns only allowed fields. Never put a Supabase service-role key in the Android app.
2. **Remote notifications while the app is closed are not enabled yet.** Reliable push needs Firebase Cloud Messaging (FCM), an Android `google-services.json`, a secure device-token registration endpoint, and a Supabase Database Webhook/Edge Function that sends FCM when an inquiry row is inserted. The app currently demonstrates permission + foreground notifications only.
3. Confirm the exact database schema and permissions. The website sends `name`, `email`, `phone`, `client_id`, and `metadata` (`matter_type`, `preferred_time`, etc.); the project expects an `id` and `created_at` column too.
4. Build with Android Studio (JDK 17, Android Gradle Plugin 8.7.3). Open this folder as a project and run Gradle sync. Install an Android SDK platform for API 35.

## Run
Open this folder in Android Studio → Sync Gradle → Run on an Android device/emulator. Configure Supabase access/RLS before testing real inquiries.

## Brand
Made by Solowise · Client: Advocate Sharad Kumar Saxena, Bareilly.
