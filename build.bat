@echo off
echo Building Persian Music Player APK...
echo.

REM Clean Gradle cache to avoid version conflicts
echo Cleaning Gradle cache...
rmdir /s /q ".gradle" 2>nul
rmdir /s /q "app\.gradle" 2>nul

REM Check if Java is available
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: Java is not installed or not in PATH.
    echo Please install Java 11, 17, or 21 and ensure it's in your PATH.
    pause
    exit /b 1
)

REM Clean and build
echo Cleaning project...
call gradlew.bat clean

echo Building APK with updated Gradle...
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
    echo ❌ Build failed. Trying to diagnose...
    echo.
    echo Common solutions:
    echo 1. Clean Gradle cache: gradlew.bat clean
    echo 2. Check Java version: java -version
    echo 3. Use Android Studio for easier setup
)

echo.
echo Press any key to continue...
pause >nul