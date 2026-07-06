import re

def fix_icons(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    
    content = content.replace("Icons.AutoMirrored.Filled.Sort", "Icons.Default.Sort")
    content = content.replace("Icons.AutoMirrored.Filled.Logout", "Icons.Default.Logout")
    content = content.replace("Icons.AutoMirrored.Filled.RotateRight", "Icons.Default.RotateRight")
    content = content.replace("Icons.AutoMirrored.Outlined.InsertDriveFile", "Icons.Outlined.InsertDriveFile")
    content = content.replace("Icons.AutoMirrored.Outlined.Label", "Icons.Outlined.Label")
    
    with open(file_path, 'w') as f:
        f.write(content)

fix_icons('app/src/main/java/com/example/ui/GalleryScreen.kt')
fix_icons('app/src/main/java/com/example/ui/MediaViewerDialog.kt')
