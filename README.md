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
The tool contains a set of tests that use the examples taken from DifFuzz to modifications the development process. To modifications it, simply run **gradle build**. Once the build process is completed a report of the tests is available in the directory **build\reports\tests\modifications\index.html**.  
### Notes
* As it stands to use the tool correctly the Driver must be in the same directory as the vulnerable code to be corrected.
* As it stands the tool is only capable of correcting very specific instances of early-exit timing side-channel vulnerabilities, where the correction of the vulnerability is when there is an early return that can be replaced for an assignment to the variable returned in the final **return**.


### Dataset Information
 | **Dataset name** | **Has secure version?** | **Type** | **Correction Attempted** | **Symbolically Correct?** | **Correct Vulnerability?** |
 | --- | --- | --- | --- | --- | --- |
 | Apache FtpServer Clear | Yes | Mixed | Yes | Yes | No |
 | Apache FtpServer Md5 | Yes | Early-Exit | Yes | No | No |
 | Apache FtpServer Salted Encrypt | No | Undefined | No | - | - |
 | Apache FtpServer Salted | Yes | Mixed | Yes | No | - |
 | Apache FtpServer StringUtils | Yes | Mixed | Yes | Yes | - |
 | Blazer Array | Yes | Control-Flow | Yes | Yes | No |
 | Blazer Gpt14 | Yes | Control-Flow | Yes | Yes | No |
 | Blazer K96 | Yes | Control-Flow | Yes | Yes | No |
 | Blazer LoopAndBranch | Yes | Unknown | No | - | - |
 | Blazer Modpow1 | Yes | Control-Flow | Yes | Yes | Yes |
 | Blazer Modpow2 | Yes | Unknown | No | - | - |
 | Blazer PasswordEq | Yes | Early-Exit | Yes | Yes | Yes |
 | Blazer Sanity | Yes | Mixed | Yes | Yes | - |
 | Blazer StraightLine | Yes | Control-Flow | Yes | Yes | No |
 | Blazer UnixLogin | Yes | Control-Flow | Yes | Yes | No |
 | Example PWCheck | Yes | Mixed | Yes | Yes | No |
 | GitHub AuthmReloaded | Yes | Mixed | Yes | Yes | No |
 | STAC Crime | No | Unknown | No | - | - |
 | STAC Ibasys | No | Control-Flow | Yes | Yes | No |
 | Themis Boot-Stateless-Auth | Yes | Mixed | Yes | Yes | No |
 | Themis Dynatable | No | Early-Exit | Yes | No | No |
 | Themis GWT Advanced Table | No | Unknown | No | - | - |
 | Themis Jdk | Yes | Mixed | Yes | Yes | No |
 | Themis Jetty | Yes | Early-Exit | Yes | Yes | Yes |
 | Themis OACC | No | Mixed | Yes | Yes | No |
 | Themis OpenMrs-Core | No | Possibly Early-Exit | No | - | - |
 | Themis OrientDb | Yes | Mixed | Yes | Yes | No |
 | Themis Pac4j | Yes | Control-Flow | Yes | Yes | - |
 | Themis PicketBox | Yes |  Mixed | No | - | - |
 | Themis Spring-Security | Yes | Early-Exit | Yes | No | - |
 | Themis Tomcat | Yes |  Mixed | Yes | Yes | No |
 | Themis TourPlanner | Yes | Special Early-Exit | No | - | - |
 
 #### Captions of table
 **Has secure Version** indicates whether the dataset provided both the safe and unsafe versions of the program.
 
 **Type** indicates the type of timing side-channel in the dataset. It can be: *Early-Exit*, *Control-Flow*, *Mixed*. The Types: *Possibly Mixed* means that there is a possibility that the program has a mix of early-exit and control-flow, but further analysis is necessary, *Undefined* indicates that the analysis of the vulnerability showed that the vulnerability is in code inaccessible to the tool (the vulnerability is in a library), *Unknown* indicates a program where after analysis could not find the vulnerability. *Possibly Early-Exit* means that there is a suspicion that it might be early-exit, but further analysis is necessary, *Special Early-Exit* means that the vulnerability was considered to be early-exit but its correction is beyond the scope of the project.
 
 **Correction attempted** indicates if the tool was executed on the dataset at least once. 
 
 **Symbolically Correct** indicates if the correction of the tool passed the modifications produced by the tool **EvoSuite** for the save version of the dataset.
 
 **Correct Vulnerability** indicates if after executing DifFuzz in the corrected version of the dataset it found a vulnerability.
 
 
 #### This tool will be in constant update. 
