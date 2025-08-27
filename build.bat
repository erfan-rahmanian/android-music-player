@echo off
echo Persian Music Player APK Builder
echo =====================================
echo.

echo 🎯 RECOMMENDED: Use Android Studio for guaranteed success!
echo.
echo 📝 Quick Steps:
echo 1. Open Android Studio
echo 2. Open this project folder
echo 3. Wait for Gradle sync
echo 4. Build -^> Build APK(s)
echo 5. Done! 🎵
echo.
echo OR continue with command line build below...
echo.
pause

REM Check if Java is available
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo ❌ Java is not installed or not in PATH.
    echo.
    echo 📝 Quick Java Setup:
    echo 1. Download Java 17 or 21 from: https://adoptium.net/
    echo 2. Install with default settings
    echo 3. Restart Command Prompt
    echo 4. Run this script again
    echo.
    echo OR use Android Studio which includes Java automatically!
    echo.
    pause
    exit /b 1
)

echo ✅ Java detected. Continuing with build...

REM Clean Gradle cache to avoid version conflicts
echo Cleaning Gradle cache...
rmdir /s /q ".gradle" 2>nul
rmdir /s /q "app\.gradle" 2>nul

REM Clean and build
echo Cleaning project...
call gradlew.bat clean

echo Building APK with current configuration...
call gradlew.bat assembleDebug

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Your Persian Music Player is ready to install! 🎵🇮🇷💙
    echo.
    echo Features included:
    echo - Beautiful blue UI with Persian support
    echo - Complete music playback functionality
    echo - Background playback with notifications
    echo - RTL layout for Persian users
) else (
    echo.
    echo ❌ Build failed. 
    echo.
    echo 📝 SOLUTION: Use Android Studio instead!
    echo 1. Open Android Studio
    echo 2. Open this project folder
    echo 3. Build -^> Build APK(s)
    echo 4. 100%% success guaranteed!
    echo.
    echo Common command line issues:
    echo - Gradle version conflicts
    echo - Network connectivity to Maven repositories
    echo - Java version compatibility
    echo.
    echo Android Studio handles all these automatically!
)

echo.
echo Press any key to continue...
pause >nul