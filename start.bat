@echo off
setlocal

set JAVA_HOME=D:\development_all\java_all\jdk_all\jdk17_all
set CLASSPATH=target\classes

REM 添加所有Spring Boot依赖
for %%f in (D:\development_all\mavenall\mavendown\repository\org\springframework\boot\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for %%f in (D:\development_all\mavenall\mavendown\repository\org\springframework\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for %%f in (D:\development_all\mavenall\mavendown\repository\com\baomidou\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for %%f in (D:\development_all\mavenall\mavendown\repository\org\mybatis\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for %%f in (D:\development_all\mavenall\mavendown\repository\com\mysql\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for %%f in (D:\development_all\mavenall\mavendown\repository\com\zaxxer\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

echo Starting FeuerwerkAgent Application...
echo CLASSPATH: %CLASSPATH%

"%JAVA_HOME%\bin\java.exe" -cp "%CLASSPATH%" com.example.feuerwerkagent.FeuerwerkagentApplication

pause