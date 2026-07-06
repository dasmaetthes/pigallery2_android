import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# Revert "val ratio by" to "val aspectRatio by" everywhere
content = content.replace("val ratio by viewModel.aspectRatio.collectAsState()", "val aspectRatio by viewModel.aspectRatio.collectAsState()")

# Fix the .let { ... } in PersonItem where shadowing happens
# The problem in PersonItem was:
# .let { if (aspectRatio > 0f) it.aspectRatio(aspectRatio) else it.aspectRatio(1f) }
# In AlbumsTabContent they did:
# val cardModifier = Modifier.fillMaxWidth().let { modifier -> val ratio = aspectRatio; if (ratio > 0f) modifier.aspectRatio(ratio) else modifier }
bad_let = ".let { if (ratio > 0f) it.aspectRatio(ratio) else it.aspectRatio(1f) }"
# Also the original one might still be there if my previous script missed it or I run it again
bad_let_orig = ".let { if (aspectRatio > 0f) it.aspectRatio(aspectRatio) else it.aspectRatio(1f) }"

good_let = ".let { modifier -> val r = aspectRatio; if (r > 0f) modifier.aspectRatio(r) else modifier.aspectRatio(1f) }"

content = content.replace(bad_let, good_let)
content = content.replace(bad_let_orig, good_let)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
