@echo off

REM ---------------------------------------------------------------------------
REM  Copyright 2005-2009 WSO2, Inc. http://www.wso2.org
REM
REM  Licensed under the Apache License, Version 2.0 (the "License"); 
REM  you may not use this file except in compliance with the License.
REM  You may obtain a copy of the License at
REM
REM      http://www.apache.org/licenses/LICENSE-2.0
REM
REM  Unless required by applicable law or agreed to in writing, software
REM  distributed under the License is distributed on an "AS IS" BASIS,
REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM  See the License for the specific language governing permissions and
REM  limitations under the License.

rem ----------------------------------------------------------------------------

title WSO2 Carbon Server Installation

rem ----- if JAVA_HOME is not set we're not happy ------------------------------

:checkJava
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
goto runIt

:noJavaHome
echo "You must set the JAVA_HOME variable before running WSO2 Carbon Installer."
goto end

:runIt
set CURRENT_DIR = %cd%
rem check the CARBON_HOME environment variable
if not "%CARBON_HOME%" == "" goto gotHome
set CARBON_HOME=%~sdp0..
if exist "%CARBON_HOME%\bin\version.txt" goto okHome

rem guess the home. Jump one directory up to check if that is the home
cd ..
set CARBON_HOME=%cd%
cd %CURRENT_DIR% >> NULL

:gotHome
if not exist "%CARBON_HOME%\bin\version.txt" goto pathError

SET curDrive=%cd:~0,1%
SET wsasDrive=%CARBON_HOME:~0,1%
if not "%curDrive%" == "%wsasDrive%" %wsasDrive%:

goto okHome

set CARBON_HOME=%~dp0..
if exist "%CARBON_HOME%\bin\version.txt" goto okHome

:pathError
echo The CARBON_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end

:okHome
setlocal EnableDelayedExpansion

if "%CATALINA_HOME%" == "" set CATALINA_HOME="INVALID"
if "%JBOSS_HOME%" == "" set JBOSS_HOME="INVALID"
if "%GERONIMO_HOME%" == "" set GERONIMO_HOME="INVALID"
if "%JAVA_HOME%" == "" set JAVA_HOME="INVALID"

cd %CARBON_HOME%

set CARBON_CLASSPATH=
FOR %%C in ("%CARBON_HOME%\lib\*.jar") DO set CARBON_CLASSPATH=!CARBON_CLASSPATH!;".\lib\%%~nC%%~xC"

set CARBON_CLASSPATH="%JAVA_HOME%\lib\tools.jar";%CARBON_CLASSPATH%;

"%JAVA_HOME%\bin\java.exe" -cp %CARBON_CLASSPATH% org.wso2.carbon.core.installer.InstallationManager "install" -java_home "%JAVA_HOME%" -tomcat_home "%CATALINA_HOME%" -jboss_home "%JBOSS_HOME%"  -geronimo_home "%GERONIMO_HOME%"

if %ERRORLEVEL%==0 goto complete
if %ERRORLEVEL%==-1 goto complete

if %ERRORLEVEL%==50 goto installService

rem -------------- INSTALL WSAS AS AN NT SERVICE -------------------------
:installService
rem Copyright (c) 1999, 2006 Tanuki Software Inc.
rem
rem Java Service Wrapper general NT service install script
rem

if "%OS%"=="Windows_NT" goto nt
echo This script only works with NT-based versions of Windows.
goto :eof

:nt
rem
rem Find the application home.
rem
rem %~dp0 is location of current script under NT
set _REALPATH=%~dp0..\

rem Decide on the wrapper binary.
set _WRAPPER_BASE=wrapper
set _WRAPPER_DIR=%_REALPATH%bin\native\
set _WRAPPER_EXE=%_WRAPPER_DIR%%_WRAPPER_BASE%-windows-x86-32.exe
if exist "%_WRAPPER_EXE%" goto conf
set _WRAPPER_EXE=%_WRAPPER_DIR%%_WRAPPER_BASE%-windows-x86-64.exe
if exist "%_WRAPPER_EXE%" goto conf
set _WRAPPER_EXE=%_WRAPPER_DIR%%_WRAPPER_BASE%.exe
if exist "%_WRAPPER_EXE%" goto conf
echo Unable to locate a Wrapper executable using any of the following names:
echo %_WRAPPER_DIR%%_WRAPPER_BASE%-windows-x86-32.exe
echo %_WRAPPER_DIR%%_WRAPPER_BASE%-windows-x86-64.exe
echo %_WRAPPER_DIR%%_WRAPPER_BASE%.exe
pause
goto :eof

rem
rem Find the wrapper.conf
rem
:conf
set _WRAPPER_CONF="%~f1"
if not %_WRAPPER_CONF%=="" goto startup
set _WRAPPER_CONF="%_REALPATH%repository\conf\wrapper.conf"

rem
rem Install the Wrapper as an NT service.
rem
:startup
"%_WRAPPER_EXE%" -i %_WRAPPER_CONF%
if not errorlevel 1 goto :eof
goto complete:
rem ------------------------ END -------------------------------------


:complete

if "%CATALINA_HOME%" == "INVALID" set CATALINA_HOME=""
if "%JBOSS_HOME%" == "INVALID" set JBOSS_HOME=""
if "%GERONIMO_HOME%" == "INVALID" set GERONIMO_HOME=""
if "%JAVA_HOME%" == "INVALID" set JAVA_HOME=""

cd %CURRENT_DIR% >> NULL
endlocal
rem pause so that the user is able to see the messages
if not "%curDrive%" == "%wsasDrive%" %curDrive%:
:end
pause

