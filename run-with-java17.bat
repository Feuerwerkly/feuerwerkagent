@echo off
echo Starting FeuerwerkAgent with Java 17...
set "JAVA_HOME=D:\development_all\java_all\jdk_all\jdk17_all"
set "PATH=D:\development_all\java_all\jdk_all\jdk17_all\bin;%PATH%"
cd /d "D:\codeall\javapro\feuerwerkagent\feuerwerkagent"
call mvn package -DskipTests
cd target
"D:\development_all\java_all\jdk_all\jdk17_all\bin\java.exe" -jar feuerwerkagent-0.0.1-SNAPSHOT.jar