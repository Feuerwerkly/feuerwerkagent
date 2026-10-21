@echo off
setlocal enabledelayedexpansion

set JAVA_HOME=D:\development_all\java_all\jdk_all\jdk17_all
set APP_DIR=D:\codeall\javapro\feuerwerkagent\feuerwerkagent
set MAVEN_REPO=D:\development_all\mavenall\mavendown\repository

echo Building CLASSPATH...
set CLASSPATH=%APP_DIR%\target\classes

REM 添加Spring Boot核心
for /r "%MAVEN_REPO%\org\springframework\boot" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

REM 添加Spring框架
for /r "%MAVEN_REPO%\org\springframework" %%f in (spring-*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

REM 添加MyBatis Plus
for /r "%MAVEN_REPO%\com\baomidou" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

REM 添加MySQL驱动
for /r "%MAVEN_REPO%\com\mysql" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

REM 添加其他必需依赖
for /r "%MAVEN_REPO%\org\apache\tomcat" %%f in (tomcat-*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for /r "%MAVEN_REPO%\jakarta" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for /r "%MAVEN_REPO%\com\fasterxml\jackson" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for /r "%MAVEN_REPO%\ch\qos\logback" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for /r "%MAVEN_REPO%\org\slf4j" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for /r "%MAVEN_REPO%\io\micrometer" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

echo Starting FeuerwerkAgent Application on port 8080...
echo.
echo Using Java: %JAVA_HOME%\bin\java.exe
echo.

cd /d "%APP_DIR%"
"%JAVA_HOME%\bin\java.exe" -cp "!CLASSPATH!" com.example.feuerwerkagent.FeuerwerkagentApplication

pause