# mavendeployplugin

Maven plugin for deployment of Tomcat web application

## Introduction

### How to create new version of plugin

1. Checkout `mvn-repo` branch in separate folder.
1. Change version in `pom.xml`.
1. Change `serialVersionUID` in `com.indigobyte.deploy.Checksum` if you want checksums old versions of the plugin not to be ignored.
1. Run `mvn clean deploy`. This will overwrite contents of `mvn-repo` branch on the server. You need to put old versions 
back in the repo.
1. Clone `mvn-repo` branch from the server in another folder.
1. Merge contents of `mvn-repo` branch created on step 1 with the one created during previous step:
    1. You need to change `/com/indigobyte/maven/plugins/cc-deploy-maven-plugin/maven-metadata.xml` and add all the 
    previous versions in the `versions` tag:
     ```
    <versions>
      <version>1.4</version>
      <version>1.5</version>
      <version>1.6</version>
    </versions>
    ```
    1. After that calculate checksums of the changed `maven-metadata.xml` file and update `maven-metadata.xml.md5` and 
    `maven-metadata.xml.sha1` files. You can use http://onlinemd5.com/ for checksum generation, just don't forget to 
    change letters to lowercase.
    1. Copy folders with previous versions from the folder created on step 1.
    1. Commit and push updated version to the server. 

### How to create new version of plugin (old / doesn't work)

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

### How to create new version of plugin

1. Let's say git repository is cloned into local folder `C:\cc-deploy-maven-plugin`.
1. Clone repository to separate folder, e.g. `C:\mvn-repo`.
1. Switch to `mvn-repo` branch in that folder.
1. Go back to folder `C:\cc-deploy-maven-plugin`
1. Change version in `pom.xml`.
1. Run `mvn clean install -DmvnRepo=C:\mvn-repo`. (when running on Cygwin, `\`, must be escaped: `mvn clean install -DmvnRepo=C:\\mvn-repo`)
1. Go to folder `C:\mvn-repo`.
1. Add new files to commit, then commit and push `mvn-repo` branch to server. 

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
|touchWebXml|whether `WEB-INF/web.xml` must be touched after remote files were uploaded/removed/overwritten|no, default `true`|
