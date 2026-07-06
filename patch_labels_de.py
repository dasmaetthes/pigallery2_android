import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/MediaViewerDialog.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace('MetadataRow(label = "Filename",', 'MetadataRow(label = "Dateiname",')
content = content.replace('MetadataRow(label = "Folder",', 'MetadataRow(label = "Ordner",')
content = content.replace('MetadataRow(label = "Dimensions",', 'MetadataRow(label = "Abmessungen",')
content = content.replace('MetadataRow(label = "Date Taken",', 'MetadataRow(label = "Aufnahmedatum",')
content = content.replace('MetadataRow(label = "Type",', 'MetadataRow(label = "Typ",')
content = content.replace('MetadataRow(label = "Camera",', 'MetadataRow(label = "Kamera",')
content = content.replace('MetadataRow(label = "Aperture",', 'MetadataRow(label = "Blende",')
content = content.replace('MetadataRow(label = "Exposure Time",', 'MetadataRow(label = "Belichtungszeit",')
content = content.replace('MetadataRow(label = "Keywords",', 'MetadataRow(label = "Schlagwörter",')
content = content.replace('MetadataRow(label = "Persons",', 'MetadataRow(label = "Personen",')

with open(file_path, "w") as f:
    f.write(content)
