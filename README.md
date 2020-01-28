# mavendeployplugin

Maven plugin for deployment of Tomcat web application

## How to create new version of plugin

1. Let's say git repository is cloned into local folder `C:\cc-deploy-maven-plugin`.
1. Clone repository to separate folder, e.g. `C:\mvn-repo`.
1. Switch to `mvn-repo` branch in that folder.
1. Go back to folder `C:\cc-deploy-maven-plugin`
1. Change version in `pom.xml`.
1. Run `mvn clean install -DmvnRepo=C:\mvn-repo`. (when running on Cygwin, `\`, must be escaped: `mvn clean install -DmvnRepo=C:\\mvn-repo`)
1. Go to folder `C:\mvn-repo`.
1. Add new files to commit, then commit and push `mvn-repo` branch to server. 

## Maven: how to build and install locally, without uploading to remote repository

    mvn clean install
    
## Plugin configuration

|parameter|description|required|
|---|---|---|
|deploy.warName|name of the folder where plugin will be uploaded to, usually it should be `${project.build.finalName}`|yes|
|deploy.hostName|hostname to connect to via SSH|yes|
|deploy.port|SSH port|no, default value is `22`|
|deploy.userName|SSH username|yes|
|deploy.sshKeyFile|SSH key file|yes|
|deploy.remoteWebApps|remote folder where upload files to|yes|
|deploy.nginxCacheDir|remote folder which must be cleaned after any changes were made|yes|
|touchWebXml|whether `WEB-INF/web.xml` must be touched after remote files were uploaded/removed/overwritten|no, default `true`|
