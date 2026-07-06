import re

def fix_icons(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    
    content = content.replace("Icons.Default.Sort", "Icons.AutoMirrored.Filled.Sort")
    content = content.replace("Icons.Default.Logout", "Icons.AutoMirrored.Filled.Logout")
    content = content.replace("Icons.Default.RotateRight", "Icons.AutoMirrored.Filled.RotateRight")
    content = content.replace("Icons.Outlined.InsertDriveFile", "Icons.AutoMirrored.Outlined.InsertDriveFile")
    content = content.replace("Icons.Outlined.Label", "Icons.AutoMirrored.Outlined.Label")
    
    with open(file_path, 'w') as f:
        f.write(content)

fix_icons('app/src/main/java/com/example/ui/GalleryScreen.kt')
fix_icons('app/src/main/java/com/example/ui/MediaViewerDialog.kt')
