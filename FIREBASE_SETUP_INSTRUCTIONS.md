# Firebase Phone Authentication Setup Instructions

## Problem
You're getting these errors:
- `INVALID_CERT_HASH 400`
- `Failed to get reCAPTCHA token with error [There was an error while trying to get your package certificate hash]`
- `SMS verification code request failed: unknown status code: 17093`

This happens because Firebase Phone Authentication requires your app's SHA-1 and SHA-256 certificate hashes to be registered in Firebase Console.

## Solution: Add SHA-1 and SHA-256 to Firebase Console

### Step 1: Get Your App's SHA-1 and SHA-256 Hashes

#### For Debug Build (Development):
Open a terminal/command prompt in your project root and run:

**Windows (PowerShell):**
```powershell
cd android
.\gradlew signingReport
```

**Windows (CMD):**
```cmd
cd android
gradlew signingReport
```

**Mac/Linux:**
```bash
cd android
./gradlew signingReport
```

Look for output like this:
```
Variant: debug
Config: debug
Store: C:\Users\YourName\.android\debug.keystore
Alias: AndroidDebugKey
MD5: XX:XX:XX:...
SHA1: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
SHA-256: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
```

Copy the **SHA1** and **SHA-256** values.

#### Alternative Method (Using keytool):
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

### Step 2: Add Hashes to Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click the **Settings gear icon** (⚙️) → **Project settings**
4. Scroll down to **Your apps** section
5. Find your Android app (package: `com.manavoori.muchhatlu`)
6. Click **Add fingerprint**
7. Paste your **SHA-1** hash and click **Save**
8. Click **Add fingerprint** again
9. Paste your **SHA-256** hash and click **Save**

### Step 3: Download Updated google-services.json

1. In Firebase Console, download the updated `google-services.json`
2. Replace the existing file in `app/google-services.json`
3. Rebuild your app

### Step 4: For Release Build

When you create a release build, you'll need to:
1. Get the SHA-1 and SHA-256 from your release keystore:
   ```bash
   keytool -list -v -keystore your-release-keystore.jks -alias your-key-alias
   ```
2. Add those hashes to Firebase Console as well

## Important Notes

- You need to add **both** SHA-1 and SHA-256 hashes
- You need to add hashes for **both** debug and release builds
- After adding hashes, wait a few minutes for Firebase to update
- The error should disappear after the hashes are registered

## Verification

After adding the hashes:
1. Clean and rebuild your app
2. Try sending OTP again
3. The reCAPTCHA should work and OTP should be sent successfully

