Please configure the file src/main/resources/config.properties.
* projects.path  is the parent directory where the projects are stored
* dataset.path is the parent directory where the commit hash pairs are stored.The commit hash is stored as json file.

Please modify the  shadowJar`s destinationDirectory in build.gradle.
Please package the current project as a jar package.

Please Run 
```shell
java -jar Divergent_Jar_path --first=true --name=<repo_name>
```