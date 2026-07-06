#!/usr/bin/env python3
"""
archive_patcher.py - Consolidated helper utility for code search-and-replace.
This merges the functional patching patterns of previous helper scripts into a single organized utility.
"""

import os
import re

def replace_in_file(file_path, old_text, new_text, count=0):
    """
    Reads a file, replaces `old_text` with `new_text` up to `count` times, 
    and writes the changes back if any replacement was made.
    """
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} does not exist.")
        return False
        
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
        
    if old_text not in content:
        print(f"Warning: Target content not found in {file_path}.")
        return False
        
    new_content = content.replace(old_text, new_text, count) if count > 0 else content.replace(old_text, new_text)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f"Successfully patched {file_path}.")
    return True

def regex_replace_in_file(file_path, pattern, replacement, flags=0):
    """
    Performs regex search-and-replace in a file.
    """
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} does not exist.")
        return False
        
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
        
    compiled_pattern = re.compile(pattern, flags)
    if not compiled_pattern.search(content):
        print(f"Warning: Pattern {pattern} not found in {file_path}.")
        return False
        
    new_content = compiled_pattern.sub(replacement, content)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f"Successfully applied regex patch to {file_path}.")
    return True

if __name__ == "__main__":
    print("archive_patcher.py: Run this utility with custom functions to patch or search codebase files.")
