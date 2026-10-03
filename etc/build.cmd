@echo off
setlocal

set "APP_NAME=taostyle"
for %%I in ("%~dp0..") do set "SOURCE_DIR=%%~fI"
for %%I in ("%SOURCE_DIR%\..") do set "WORKSPACE_DIR=%%~fI"

if "%APP_BK_DIR%"=="" set "APP_BK_DIR=%WORKSPACE_DIR%\BK\codespace\%APP_NAME%"
if "%DEPLOY_DIR%"=="" set "DEPLOY_DIR=C:\dropbox\workspace\apache-tomcat-8.5.82\webapps"
set "WAR_BACKUP_DIR=%APP_BK_DIR%\war_backup"

pushd "%SOURCE_DIR%"
rem move .\src\main\resources\META-INF\spring\database.properties .\deploy\database_localBK.properties
rem copy .\deploy\database_%APP_NAME%.properties .\src\main\resources\META-INF\spring\database.properties /y

if not exist "%APP_BK_DIR%" mkdir "%APP_BK_DIR%"
if not exist "%WAR_BACKUP_DIR%" mkdir "%WAR_BACKUP_DIR%"
if not exist "%DEPLOY_DIR%" mkdir "%DEPLOY_DIR%"

call mvn clean install
if errorlevel 1 goto :end

if not exist ".\target\ROOT.war" (
    echo Expected artifact not found: .\target\ROOT.war
    goto :end
)

if exist "%DEPLOY_DIR%\ROOT.war" (
    copy /Y "%DEPLOY_DIR%\ROOT.war" "%WAR_BACKUP_DIR%\ROOT_%date:~10,4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%.war" >nul
)

copy /Y ".\target\ROOT.war" "%DEPLOY_DIR%\ROOT.war"
rem del .\target /q

rem copy the root into vip folders  copy %DEPLOY_DIR%\ROOT.war C:\Dropbox\prodspace\eeh\ROOT.war /y
rem mvn clean install

rem jar cvf %APP_BK_DIR%\TaoStyle_SRC_%date:~6,4%%date:~0,2%%date:~3,2%.zip .

rem copy .\deploy\database_localBK.properties .\src\main\resources\META-INF\spring\database.properties /y
rem mvn clean install

:end
popd
endlocal
