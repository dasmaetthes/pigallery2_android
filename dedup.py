with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    lines = f.read().split('\n')

res = []
for line in lines:
    if line.strip() == '@JsonClass(generateAdapter = true)':
        if len(res) > 0 and res[-1].strip() == '@JsonClass(generateAdapter = true)':
            continue
    res.append(line)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write('\n'.join(res))
