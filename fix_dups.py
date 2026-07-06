with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    lines = f.read().split('\n')

out = []
# Keep track of functions we are inside to remove duplicates only if needed
# Actually, if we just find 'val spacing by viewModel.spacing.collectAsState()'
# and the very next line or close by is the same, we drop it.
# Wait, let's just deduplicate within a 15 line window.
def remove_dup_spacing(lines):
    new_lines = []
    for line in lines:
        if line.strip() == 'val spacing by viewModel.spacing.collectAsState()':
            # Check if we already added it recently (within last 15 lines of new_lines)
            recent_lines = [l.strip() for l in new_lines[-15:]]
            if 'val spacing by viewModel.spacing.collectAsState()' in recent_lines:
                continue # Skip this duplicate
        new_lines.append(line)
    return new_lines

out = remove_dup_spacing(lines)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write('\n'.join(out))
