@echo off
echo Building Music Player APK...
echo.

REM Check if Android SDK is available
if not exist "%ANDROID_HOME%" (
    echo Error: ANDROID_HOME is not set. Please install Android Studio and set ANDROID_HOME.
    echo Example: set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
    pause
    exit /b 1
)

REM Clean and build
echo Cleaning project...
call gradlew.bat clean

echo Building APK...
call gradlew.bat assembleDebug

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Your Persian Music Player is ready to install! 🎵🇮🇷💙
) else (
    echo.
    echo ❌ Build failed. Please check the error messages above.
)

pause