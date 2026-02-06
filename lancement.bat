@echo off
REM installer le JAR du framework localement
mvn install:install-file "-Dfile=lib\framework.jar" "-DgroupId=com.itu" "-DartifactId=framework" "-Dversion=1.0" "-Dpackaging=jar"

REM lancer la webapp avec Jetty
mvn jetty:run