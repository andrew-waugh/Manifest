@echo off
if exist "J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode" (
	set code="J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode"
	set javafx="C:/Program Files/Java/javafx-sdk-17.0.1"
) else if exist "Z:\VERSCode" (
	set code="Z:\VERSCode"
	set javafx="C:/Program Files/Java/javafx-sdk-17.0.1"
) else (
	set code="C:/Users/Andrew/Documents/Work/VERSCode"
	set javafx="C:/Program Files/java/javafx-sdk-17.0.1"
)
java --module-path %javafx%/lib --add-modules=javafx.controls,javafx.fxml -classpath %code%/Manifest/dist/* Manifest.ManifestGUI Manifest.jar -a %code%/Manifest/assets %*
Rem java -classpath %code%\Manifest/dist/* Manifest.Manifest -s %code%/VPA/support %^
