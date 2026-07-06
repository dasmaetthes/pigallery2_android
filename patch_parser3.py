with open('/app/applet/app/src/main/java/com/example/data/search/SearchQueryParser.kt', 'r') as f:
    content = f.read()

old_regex = r'Regex("""(?:[^\s"()]|"[^"]*"|\([^)]*\))+""")'
new_regex = r'Regex("""(?:[^\s"()]|"[^"]*(?:"|$)|\([^)]*(?:\)|$))+""")'

content = content.replace(old_regex, new_regex)

with open('/app/applet/app/src/main/java/com/example/data/search/SearchQueryParser.kt', 'w') as f:
    f.write(content)
