# ORTA (Overlap-aware Rapid Type Analysis)
This repository aims to provide the source code to reproduce the experiments conducted for our research paper.

## Usage
### Tested System

- mariadb 10.4.7
- Apache Maven 3.6.1
- OpenJDK 1.8.0_222

### Usage
#### Install the maven plugin

> mvn install -DskipTests

#### Specify the environment variables

##### db.properties
- url=mariadb://{ip}:{port}/{databasename}
- user={username}
- password={password}

##### tools.properties
- MVN_HOME={the path to maven}
- JAVA_HOME={the path to JRE}

#### Collect the commits from a git repository
> ./collect "repositoryURL" "Head Commit Name" "# of commits to be selected"

ex)
> ./collect "https://github.com/rts-orta/orta" "785665667c1ad48bf66d7469347ce5f7749a094d" 2
#### Benchmark testAll, singleRTA, separateRTA, and ORTA of a commit
> ./benchmark "edgeId"

edgeId is the primary key of the "edges" table in the database.