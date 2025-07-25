please configure the file src/main/resources/config.properties.
* projects.path  is the parent directory where the projects are stored
* dataset.path is the parent directory where the commit hash pairs are stored.The commit hash is stored as json file.

please modify the  shadowJar`s destinationDirectory in build.gradle.
please package the current project as a jar package.


Run 
```shell
java -jar Divergent_Jar_path --first=true --name=<repo_name>
```