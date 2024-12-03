# Instructions:

- To run the application, ensure that both the java-libs and javafx folders are located in the C: drive. These folders can be found in the final-laptop directory. If they are stored elsewhere, please adjust the command accordingly.   
- Additionally, add the lib folder located within the javafxsdk to your system's PATH environment variable.
- If you encounter any issues with compilation due to your JDK version, we recommend using JDK 21. 

## Compile commands:
### (Server):
#### javac -classpath .;C:\java-libs\* AudioStreamingServer.java
 - javac --module-path "C:\javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml -classpath .;C:\java-libs\* AudioStreamingClientUI.java
 - java -classpath .;C:\java-libs\* AudioStreamingServer



- (Client)
* java --module-path "C:\javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml -classpath .;C:\java-libs\* AudioStreamingClientUI

