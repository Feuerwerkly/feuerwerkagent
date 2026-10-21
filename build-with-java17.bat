@echo off
set JAVA_HOME=D:\development_all\java_all\jdk_all\jdk17_all
set PATH=D:\development_all\java_all\jdk_all\jdk17_all\bin;%PATH%
echo Using Java:
java -version
echo.
echo Running Maven compile:
cd /d "D:\codeall\javapro\feuerwerkagent\feuerwerkagent"
call mvn clean compile