@echo off
echo ==============================================================
echo  Compiling Student Placement Management System Backend
echo ==============================================================

if not exist bin mkdir bin

javac -cp "lib/ojdbc6.jar" -d bin src/com/placement/db/*.java src/com/placement/server/*.java

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Backend compiled successfully into bin/ folder.
) else (
    echo [ERROR] Compilation failed. Check error messages above.
)
