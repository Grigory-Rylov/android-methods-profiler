# BASH SCRIPT: Register android-methods-profiler and its extension
# Need to launch by sudo in current application directory

APP="android-methods-profiler"

EXT1="trace"
EXT2="twb"
APP_DIR="/opt/${APP}"

COMMENT="Android trace files"

# Create directories if missing
mkdir -p ~/.local/share/mime/packages
mkdir -p ~/.local/share/applications
mkdir -p ~/.local/share/pixmaps

# Create mime xml
echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<mime-info xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\">
    <mime-type type=\"application/x-$APP\">
        <comment>$COMMENT</comment>
        <icon name=\"application-x-$APP\"/>
        <glob pattern=\"*.$EXT1\"/>
        <glob pattern=\"*.$EXT2\"/>
    </mime-type>
</mime-info>" > ~/.local/share/mime/packages/application-x-$APP.xml

# Create application desktop
echo "[Desktop Entry]
Name=$APP
Exec=$APP_DIR/$APP %U
MimeType=application/x-$APP
Icon=$APP_DIR/icon.png
Terminal=false
Type=Application
Categories=
Comment=
"> ~/.local/share/applications/$APP.desktop

# update databases for both application and mime
update-desktop-database ~/.local/share/applications
update-mime-database    ~/.local/share/mime

# copy associated icons to pixmaps
cp $APP_DIR/icon.png                ~/.local/share/pixmaps/$APP.png
cp $APP_DIR/icon.png                ~/.local/share/pixmaps/application-x-$APP.png

ln -sf $APP_DIR/$APP /usr/bin/$APP
ln -sf $APP_DIR/$APP /usr/bin/yamp
