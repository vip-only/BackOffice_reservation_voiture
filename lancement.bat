# installer le JAR du framework localement (si vous avez lib/framework.jar)
mvn install:install-file -Dfile=lib/framework.jar -DgroupId=com.itu -DartifactId=framework -Dversion=1.0 -Dpackaging=jar

# lancer la webapp avec Jetty
mvn jetty:run