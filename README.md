# mavendeployplugin

Maven plugin for deployment of Tomcat web application

## Introduction

### How to create new version of plugin

Maven repository is located in folder repository. To produce new version, change version in pom.xml, build plugin
JAR file, then create Maven Debug/Run configuration in IDEA and enter this in the `Command line` field:

    install:install-file
    -DgroupId=com.indigobyte.maven.plugins
    -DartifactId=cc-deploy-maven-plugin
    -Dversion=1.0-SNAPSHOT
    -Dfile=c:\path\to\project\folder\mavendeployplugin\target\cc-deploy-maven-plugin-1.0-SNAPSHOT.jar
    -Dpackaging=jar
    -DgeneratePom=true
    -DlocalRepositoryPath=c:\path\to\project\folder\mavendeployplugin\repository\
    -DcreateChecksum=true

Where `cc-deploy-maven-plugin-1.0-SNAPSHOT.jar` is the name of the jar file with new release.

To test how plugin works without sending changes to server, create Maven Debug/Run configuration in IDEA and
specify `install` in the `Command line`.

### Maven: how to build and install locally, without uploading to remote repository

    mvn clean install
    
### Plugin configuration

|parameter|description|required|
|---|---|---|
|deploy.warName|name of the folder where plugin will be uploaded to, usually it should be `${project.build.finalName}`|yes|
|deploy.hostName|hostname to connect to via SSH|yes|
|deploy.port|SSH port|no, default value is `22`|
|deploy.userName|SSH username|yes|
|deploy.sshKeyFile|SSH key file|yes|
|deploy.remoteWebApps|remote folder where upload files to|yes|
|deploy.nginxCacheDir|remote folder which must be cleaned after any changes were made|yes|
|deploy.predeployScript|remote command to always run before deployment|yes, can be set to `:` to do nothing|
|deploy.postdeployScript|remote command to always run after deployment|yes, can be set to `:` to do nothing|
