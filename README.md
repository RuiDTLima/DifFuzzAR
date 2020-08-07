# DifFuzzAR
[![Build Status](https://travis-ci.org/RuiDTLima/DifFuzzAR.svg?branch=master&style=plastic)](https://travis-ci.org/RuiDTLima/DifFuzzAR)
![Java CI](https://github.com/RuiDTLima/DifFuzzAR/workflows/Java%20CI/badge.svg?branch=master)
[![GitHub issues](https://img.shields.io/github/issues/RuiDTLima/DifFuzzAR?style=plastic)](https://github.com/RuiDTLima/DifFuzzAR/issues)
[![GitHub license](https://img.shields.io/github/license/RuiDTLima/DifFuzzAR?style=plastic)](https://github.com/RuiDTLima/DifFuzzAR/blob/master/LICENSE)

Automatic Repair of Java Code with Timing Side-Channel Vulnerabilities

### Setup
To run this tool it is first necessary to have used [DifFuzz](https://github.com/isstac/diffuzz) tool since this tool will take advantage of the Driver used in DifFuzz.
To run in the terminal simply run the command **gradle shadowJar** to create a Jar with all the dependencies needed. Once that is done, travel to the location of the jar. It should be in the **build/libs** of the tool directory.
Once in the correct location simply run the command **java -jar .\DifFuzzAR-1.0-SNAPSHOT.jar** indicating the location of the Driver to be used.

### Tests
The tool contains a set of tests that use the examples taken from DifFuzz to modifications the development process. To try it, simply run **gradle build**. Once the build process finishes a report of the tests is available in the directory **build\reports\tests\modifications\index.html**.  
### Notes
* As it stands to use the tool correctly the Driver must be in the same directory as the vulnerable code to be corrected.
* As it stands the tool is only capable of correcting very specific instances of early-exit timing side-channel vulnerabilities, where the correction of the vulnerability is when there is an early return can be replaced for an assignment to the variable returned as the final **return**.


### Dataset Information
 | **Dataset name** | **Has secure version?** | **Type** | **Correction Attempted** | **Symbolically Correct?** | **Correct Vulnerability?** |
 | --- | --- | --- | --- | --- | --- |
 | Apache FtpServer Clear | Yes | Mixed | Yes | No | ? |
 | Apache FtpServer Md5 | Yes | Early-Exit (If dependant) | Yes | No |  |
 | Apache FtpServer Salted Encrypt | No | Undefined | No | - |  |
 | Apache FtpServer Salted | Yes | Mixed | Yes | No |  |
 | Apache FtpServer StringUtils | Yes | Mixed | Yes | Yes | Yes |
 | Blazer Array | Yes | Control-Flow | Yes | Yes | Yes |
 | Blazer Gpt14 | Yes | Control-Flow | Yes | Yes | ? (No) |
 | Blazer K96 | Yes | Control-Flow | Yes | Yes | ? (No) |
 | Blazer LoopAndBranch | Yes | Control-Flow (ignored) | No | - |  |
 | Blazer Modpow1 | Yes | Control-Flow | Yes | Yes | Yes |
 | Blazer Modpow2 | Yes | Unknown | No | - |  |
 | Blazer PasswordEq | Yes | Early-Exit (If dependant) | Yes | Yes | Yes |
 | Blazer Sanity | Yes | Mixed | Yes | Yes | Yes |
 | Blazer StraightLine | Yes | Control-Flow | Yes | Yes | Yes |
 | Blazer UnixLogin | Yes | Control-Flow | Yes | Yes | Yes |
 | Example PWCheck | Yes | Mixed | Yes | Yes | Yes |
 | GitHub AuthmReloaded | Yes | Mixed | Yes | Yes | Yes |
 | STAC Crime | No | Unknown | No | - |  |
 | STAC Ibasys | No | Control-Flow | Yes | Yes | - |
 | Themis Boot-Stateless-Auth | Yes | Mixed | Yes | Yes | ? (Yes) |
 | Themis Dynatable | No | Early-Exit (If dependant) | Yes | Yes | No |
 | Themis GWT Advanced Table | No | Unknown | No | - |  |
 | Themis Jdk | Yes | Mixed | Yes | Yes | Yes |
 | Themis Jetty | Yes | Mixed | Yes | Yes | ? |
 | Themis OACC | No | Mixed | Yes | Yes | Yes |
 | Themis OpenMrs-Core | No | Possibly Early-Exit | No | - |  |
 | Themis OrientDb | Yes | Mixed | Yes | No | No |
 | Themis Pac4j | Yes | Control-Flow | Yes | Yes |  |
 | Themis PicketBox | Yes |  Mixed | Yes | Yes | No |
 | Themis Spring-Security | Yes | Early-Exit (If dependant and cycle dependant) | Yes | Yes | No |
 | Themis Tomcat | Yes |  Mixed | No | - | - |
 | Themis TourPlanner | Yes | Special Early-Exit (Ignored) | No | - |  |
 
 #### Captions of table
 **Has secure Version** indicates whether the dataset provided both the safe and unsafe versions of the program.
 
 **Type** indicates the type of timing side-channel in the dataset. It can be: *Early-Exit*, *Control-Flow*, *Mixed*. The Types: *Possibly Mixed* means that there is a possibility that the program has a mix of early-exit and control-flow, but further analysis is necessary, *Undefined* indicates that the analysis of the vulnerability showed that the vulnerability is in code inaccessible to the tool (the vulnerability is in a library), *Unknown* indicates a program where after analysis could not find the vulnerability. *Possibly Early-Exit* means that there is a suspicion that it might be early-exit, but further analysis is necessary, *Special Early-Exit* means that the vulnerability considered an early-exit but its correction is beyond the scope of the project.
 
 **Correction attempted** indicates if the tool executed on the dataset at least once. 
 
 **Symbolically Correct** indicates if the correction of the tool passed the modifications produced by the tool **EvoSuite** for the save version of the dataset.
 
 **Correct Vulnerability** indicates if after executing DifFuzz in the corrected version of the dataset it found a vulnerability.
 
 ### Result of executing DifFuzz on the examples used.
 |Name                                          |Medium delta |  Max delta |Medium time for delta > 0 |
 |----------------------------------------------|-------------|------------|--------------------------|
 |apache_ftpserver_clear_safe                   |1,00         |1           |5,40                      |
 |apache_ftpserver_clear_unsafe                 |47,00        |47          |6,00                      |
 |apache_ftpserver_clear_unsafe_corrected       |16           |16          |5,40                      |
 |blazer_passwordEq_safe                        |0            |0           |0                         |
 |blazer_passwodEq_unsafe                       |109,40       |127         |10,60                     |
 |blazer_passwordEq_unsafe_corrected            |0            |0           |0                         |
 |example_PWCheck_safe                          |0            |0           |0                         |
 |example_PWCheck_unsafe                        |47,00        |47          |5,60                      |
 |example_PWCheck_unsafe_corrected              |0            |0           |0                         |
 |github_authmreloaded_safe                     |1,00         |1           |4,20                      |
 |github_authmreloaded_unsafe                   |383,00       |383         |4,00                      |
 |github_authmreloaded_unsafe_corrected         |0            |0           |0                         |
 |themis_boot-stateless-auth_safe               |5,00         |5           |1,20                      |
 |themis_boot-stateless-auth_unsafe             |101,00       |101         |2,60                      |
 |themi_boot-stateless-auth_unsafe_corrected    |8,00         |8           |3,80                      |
 |themis_jdk_safe                               |1,00         |1           |19,60                     |
 |themis_jdk_unsafe                             |29386,00     |44352       |5,80                      |
 |themis_jdk_unsafe_corrected                   |0            |0           |0                         |
 |themis_jetty_safe                             |8168,00      |11910       |7,40                      |
 |themis_jetty_unsafe                           |13037,40     |17064       |6,80                      |
 |themis_jetty_unsafe_corrected                 |8911,40      |13283       |7,80                      |
 |themis_oacc_unsafe                            |49,40        |50          |8,80                      |
 |themis_oacc_unsafe_corrected                  |0            |0           |0                         |
 |themis_orientdb_safe                          |6,00         |6           |2,40                      |
 |themis_orientdb_unsafe                        |6085,20      |30268       |4,60                      |
 |themis_orientdb_unsafe_corrected              |9501,20      |15700       |1,80                      |
 |themis_picketbox_sade                         |1,00         |1           |20,40                     |
 |themis_picketbox_unsafe                       |5081,80      |9529        |5,60                      |
 |themis_picketbox_unsafe_corrected             |5864,00      |11836       |9,00                      |
 |themis_tomcat_safe                            |             |            |                          |
 |themis_tomcat_unsafe                          |             |            |                          |
 |themis_tomcat_unsafe_corrected                |             |            |                          |
 |themis_dynatable_unsafe                       |97,20        |99          |4,20                      |
 |themis_dynatable_unsafe_corrected             |94,40        |97          |3,40                      |
 |themis_spring-security_safe                   |1,00         |1           |9,20                      |
 |themis_spring-security_unsafe                 |149,00       |149         |7,60                      |
 |themis_spring-security_unsafe_corrected       |115,20       |120         |5,60                      |
 |Control-Flow                                  |#############|############|##########################|
 |blazer_array_safe                             |1,00         |1           |7,80                      |
 |blazer_array_unsafe                           |195,00       |195         |6,60                      |
 |blazer_array_unsafe_corrected                 |1,00         |1           |7,00                      |
 |blazer_gpt14_safe                             |389,00       |795         |3,60                      |
 |blazer_gpt14_unsafe                           |12371988,00  |27252112    |1,20                      |
 |blazer_gpt14_unsafe_corrected                 |8829897,00   |21415781    |3,40                      |
 |blazer_k96_safe                               |0            |0           |0                         |
 |blazer_k96_unsafe                             |1174376,00   |2768953     |3,40                      |
 |blazer_k96_unsafe_corrected                   |287835,60    |750701      |3,40                      |
 |blazer_modpow1_safe                           |0            |0           |0                         |
 |blazer_modpow1_unsafe                         |3119,40      |3612        |3,20                      |
 |blazer_modpow1_unsafe_corrected               |0            |0           |0                         |
 |blazer_straightline_safe                      |0            |0           |0                         |
 |blazer_straightline_unsafe                    |8,00         |8           |10,60                     |
 |blazer_straightline_unsafe_corrected          |0            |0           |0                         |
 |blazer_unixlogin_safe                         |2,00         |2           |162,00                    |
 |blazer_unixlogin_unsafe                       |3200000008,00|3200000008  |177,60                    |
 |blazer_unixlogin_unsafe_corrected             |2,00         |2           |380,20                    |
 |stac_ibasys_unsafe                            |1,00         |1           |1,00                      |
 |stac_ibasys_unsafe_corrected                  |1,00         |1           |1,40                      |
 |themis_pac4j_safe                             |             |            |                          |
 |themis_pac4j_unsafe                           |             |            |                          |
 |themis_pac4j_unsafe_corrected                 |             |            |                          |
 |Mixed                                         |#############|############|##########################|
 |apache_ftpserver_stringutils_safe             |0            |0           |0                         |
 |apache_ftpserver_stringutils_unsafe           |53,00        |53          |2,00                      |
 |apache_ftpserver_stringutils_unsafe_corrected | 0        |0           |0                         |
 |blazer_sanity_safe                            |0            |0           |0                         |
 |blazer_sanity_unsafe                          |4248461576,60|4294966785  |66,60                     |
 |blazer_sanity_unsafe_corrected                |0            |0           |0                         | 
  
 #### This tool will be constantly updated. 
