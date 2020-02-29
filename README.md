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
The tool contains a set of tests that use the examples taken from DifFuzz to test the development process. To test it, simply run **gradle build**. Once the build process is completed a report of the tests is available in the directory **build\reports\tests\test\index.html**.  
### Notes
* As it stands to use the tool correctly the Driver must be in the same directory as the vulnerable code to be corrected.
* As it stands the tool is only capable of correcting very specific instances of early-exit timing side-channel vulnerabilities, where the correction of the vulnerability is when there is an early return that can be replaced for an assignment to the variable returned in the final **return**. 