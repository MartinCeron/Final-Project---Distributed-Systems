compile commands:
javac -classpath .;C:\java-libs\* AudioStreamingServer.java
javac --module-path "C:\javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml -classpath .;C:\java-libs\* AudioStreamingClientUI.java



java -classpath .;C:\java-libs\* AudioStreamingServer




java --module-path "C:\javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml -classpath .;C:\java-libs\* AudioStreamingClientUI


u need to have the java-libs and javafx folder in your c drive before running, both are in the final-laptop folder. Or adjust the commmand accordingly if not
U need to add the lib folder in javafxsdk to path
If you have problems compiling with your JDK version, try JDK21


