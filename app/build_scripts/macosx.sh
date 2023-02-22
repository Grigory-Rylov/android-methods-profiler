
APP_NAME=YAMP
APP_DIR_NAME=${APP_NAME}.app
VERSION=$1

JAR_NAME=yamp
JAR_PATH=build/libs/${JAR_NAME}

jpackage --name ${APP_NAME} \
  --input  build/libs \
  --main-class com.github.grishberg.profiler.Launcher \
  --main-jar ${APP_NAME}-${VERSION}.jar \
  --icon macosx_icon/android-methods-profiler.icns \
  --app-version ${VERSION} \
  --dest build/release \
  --vendor "Grigory Rylov" \
  --file-associations dist_files/FAtrace.properties \
  --file-associations dist_files/FAtwb.properties \
  --type dmg \
  --verbose

#Move files to release folder
mv build/libs/${JAR_NAME}-${VERSION}.jar build/release/${JAR_NAME}.jar

echo 'Done.'

exit