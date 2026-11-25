@echo off
rem Order Together build (Windows)
rem Compiles the entry files below and every class they use into out\
rem Usage: .\build.cmd   (works from any folder; it moves to the repository folder)

setlocal
cd /d "%~dp0" || exit /b 1

rem ---- Settings: keep in sync with build.sh ----
set "RELEASE=21"
set "ENTRIES=src\server\ServerMain.java src\client\ClientMain.java"
rem -----------------------------------------------

setlocal EnableDelayedExpansion
set "SOURCES="
for %%E in (%ENTRIES%) do (
    if exist "%%E" (
        set "SOURCES=!SOURCES! %%E"
    ) else (
        echo skip: %%E ^(not found^)
    )
)
if not defined SOURCES (
    echo No entry files to build yet.
    exit /b 1
)

if exist out rmdir /s /q out
javac -encoding UTF-8 --release %RELEASE% -d out -sourcepath src !SOURCES!
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)
echo Build OK: out\
