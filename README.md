# mavendeployplugin

Maven plugin for deployment of Tomcat web application

## Introduction

Maven repository is located in folder repository. To produce new version, change version in pom.xml, build plugin JAR file, then create Maven Debug/Run configuration in IDEA and enter this in the `Command line` field:

    install:install-file -DgroupId=com.indigobyte.maven.plugins -DartifactId=cc-deploy-maven-plugin -Dversion=1.0-SNAPSHOT -Dfile=c:\path\to\project\folder\mavendeployplugin\target\cc-deploy-maven-plugin-1.0-SNAPSHOT.jar -Dpackaging=jar -DgeneratePom=true -DlocalRepositoryPath=c:\path\to\project\folder\mavendeployplugin\repository\ -DcreateChecksum=true

Where `cc-deploy-maven-plugin-1.0-SNAPSHOT.jar` is the name of the jar file with new release.

To test how plugin works without sending changes to server, create Maven Debug/Run configuration in IDEA and specify `install` in the `Command line`.

