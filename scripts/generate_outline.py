import os
import re
import subprocess

WORKSPACE_DIR = os.getcwd()

PACKAGE_RX = re.compile(r'package\s+([\w\.]+);')
CLASS_DECL_RX = re.compile(r'\b(class|interface|enum|record)\s+(\w+)\b')

EXTS = {'.java', '.sql', '.yml', '.yaml', '.gradle', '.properties', '.xml', '.md'}
SPECIAL_FILES = {'.gitignore', '.env.example'}

def is_in_comment_or_string(content, pos):
    state = 'code'
    escape = False
    i = 0
    while i < pos:
        char = content[i]
        next_char = content[i+1] if i + 1 < len(content) else ''
        
        if state == 'block_comment':
            if char == '*' and next_char == '/':
                state = 'code'
                i += 2
                continue
            i += 1
            continue
        elif state == 'line_comment':
            if char == '\n':
                state = 'code'
            i += 1
            continue
        elif state == 'string':
            if escape:
                escape = False
            elif char == '\\':
                escape = True
            elif char == '"':
                state = 'code'
            i += 1
            continue
        elif state == 'char':
            if escape:
                escape = False
            elif char == '\\':
                escape = True
            elif char == "'":
                state = 'code'
            i += 1
            continue
        elif state == 'code':
            if char == '/' and next_char == '*':
                state = 'block_comment'
                i += 2
                continue
            elif char == '/' and next_char == '/':
                state = 'line_comment'
                i += 2
                continue
            elif char == '"':
                state = 'string'
                escape = False
                i += 1
                continue
            elif char == "'":
                state = 'char'
                escape = False
                i += 1
                continue
            i += 1
            
    return state != 'code'

def classify_and_add(decl_str, class_name, constructors, methods, fields, has_body):
    decl_str = ' '.join(decl_str.split()).strip()
    if not decl_str:
        return
        
    # Remove leading annotations
    while decl_str.startswith('@'):
        decl_str = re.sub(r'^@\w+(?:\([^\)]*\))?\s*', '', decl_str).strip()
        
    if not decl_str:
        return
        
    control_words = {
        'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 
        'throw', 'new', 'else', 'super', 'this', 'static', 'final', 
        'class', 'interface', 'enum', 'record', 'void'
    }
    
    # Check if constructor
    constructor_match = re.search(rf'\b{class_name}\s*\(([^\)]*)\)', decl_str)
    if constructor_match:
        params = constructor_match.group(1).strip()
        params = ' '.join(params.split())
        vis_match = re.match(r'^(public|protected|private)\b', decl_str)
        vis = vis_match.group(1) if vis_match else 'package-private'
        constructors.append(f"  - `Constructor` : `{vis} {class_name}({params})`")
        return
        
    # Check if method (must contain parenthesis)
    method_match = re.search(r'(\w+)\s*\(([^\)]*)\)', decl_str)
    if method_match:
        m_name = method_match.group(1)
        params = method_match.group(2).strip()
        params = ' '.join(params.split())
        
        if m_name in control_words:
            return
            
        before = decl_str[:method_match.start()].strip()
        parts = before.split()
        vis = 'package-private'
        modifiers = []
        ret_type = 'void'
        
        if parts:
            if parts[0] in ('public', 'protected', 'private'):
                vis = parts[0]
                parts = parts[1:]
            if parts:
                ret_type = parts[-1]
                modifiers = parts[:-1]
                
        if ret_type in control_words:
            return
            
        mod_str = ' '.join(modifiers) + ' ' if modifiers else ''
        methods.append(f"  - `Method` : `{vis} {mod_str}{ret_type} {m_name}({params})`")
        return
        
    # Field
    if '=' in decl_str:
        decl_str = decl_str.split('=', 1)[0].strip()
        
    parts = decl_str.split()
    if not parts:
        return
        
    f_name = parts[-1]
    before = parts[:-1]
    
    if f_name in control_words:
        return
        
    vis = 'package-private'
    modifiers = []
    f_type = 'Object'
    
    if before:
        if before[0] in ('public', 'protected', 'private'):
            vis = before[0]
            before = before[1:]
        if before:
            f_type = before[-1]
            modifiers = before[:-1]
            
    if f_type in control_words:
        return
        
    mod_str = ' '.join(modifiers) + ' ' if modifiers else ''
    fields.append(f"  - `Field` : `{vis} {mod_str}{f_type} {f_name}`")

def parse_members_with_braces(content, class_name, start_pos):
    constructors = []
    methods = []
    fields = []
    
    first_brace = content.find('{', start_pos)
    if first_brace == -1:
        return constructors, methods, fields
        
    scope_content = content[first_brace + 1:]
    
    depth = 1
    current_decl = []
    state = 'code'
    escape = False
    
    i = 0
    while i < len(scope_content):
        char = scope_content[i]
        next_char = scope_content[i+1] if i + 1 < len(scope_content) else ''
        
        if state == 'block_comment':
            if char == '*' and next_char == '/':
                state = 'code'
                i += 2
                continue
            i += 1
            continue
            
        elif state == 'line_comment':
            if char == '\n':
                state = 'code'
            i += 1
            continue
            
        elif state == 'string':
            if escape:
                escape = False
            elif char == '\\':
                escape = True
            elif char == '"':
                state = 'code'
            i += 1
            continue
            
        elif state == 'char':
            if escape:
                escape = False
            elif char == '\\':
                escape = True
            elif char == "'":
                state = 'code'
            i += 1
            continue
            
        elif state == 'code':
            if char == '/' and next_char == '*':
                state = 'block_comment'
                i += 2
                continue
            elif char == '/' and next_char == '/':
                state = 'line_comment'
                i += 2
                continue
            elif char == '"':
                state = 'string'
                escape = False
                i += 1
                continue
            elif char == "'":
                state = 'char'
                escape = False
                i += 1
                continue
                
            if char == '{':
                depth += 1
                if depth == 2:
                    classify_and_add(''.join(current_decl), class_name, constructors, methods, fields, has_body=True)
                    current_decl = []
            elif char == '}':
                depth -= 1
                if depth == 1:
                    current_decl = []
                elif depth == 0:
                    break
            elif char == ';':
                if depth == 1:
                    classify_and_add(''.join(current_decl), class_name, constructors, methods, fields, has_body=False)
                    current_decl = []
            else:
                if depth == 1:
                    current_decl.append(char)
            i += 1
            
    return constructors, methods, fields

def parse_java_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"
    
    pkg_match = PACKAGE_RX.search(content)
    package = pkg_match.group(1) if pkg_match else ""
    
    outline = []
    if package:
        outline.append(f"Package: {package}")
        
    classes = []
    for match in CLASS_DECL_RX.finditer(content):
        if is_in_comment_or_string(content, match.start()):
            continue
        keyword = match.group(1)
        name = match.group(2)
        classes.append((keyword, name, match.start()))
        
    if not classes:
        return "Empty or non-class Java file"
        
    classes.sort(key=lambda x: x[2])
    
    for i, (keyword, name, start) in enumerate(classes):
        outline.append(f"\n{keyword.capitalize()}: {name}")
        
        # Extract record components if record
        if keyword == 'record':
            paren_start = content.find('(', start)
            if paren_start != -1:
                first_brace = content.find('{', start)
                if first_brace == -1 or paren_start < first_brace:
                    paren_count = 1
                    paren_end = paren_start + 1
                    while paren_end < len(content) and paren_count > 0:
                        if content[paren_end] == '(':
                            paren_count += 1
                        elif content[paren_end] == ')':
                            paren_count -= 1
                        paren_end += 1
                    if paren_count == 0:
                        record_header = content[paren_start:paren_end]
                        header_clean = re.sub(r'\s+', ' ', record_header[1:-1].strip())
                        
                        parts = []
                        current = []
                        depth = 0
                        for char in header_clean:
                            if char in ('<', '('):
                                depth += 1
                            elif char in ('>', ')'):
                                depth -= 1
                            if char == ',' and depth == 0:
                                parts.append(''.join(current).strip())
                                current = []
                            else:
                                current.append(char)
                        if current:
                            parts.append(''.join(current).strip())
                            
                        record_fields = []
                        for part in parts:
                            if part:
                                subparts = part.split()
                                if len(subparts) >= 2:
                                    f_name = subparts[-1]
                                    f_type = ' '.join(subparts[:-1])
                                    record_fields.append(f"  - Record Component : public final {f_type} {f_name}")
                        if record_fields:
                            outline.append("\nRecord Components:")
                            outline.extend(record_fields)
                            
        # Extract enum constants if enum
        if keyword == 'enum':
            brace_pos = content.find('{', start)
            if brace_pos != -1:
                enum_scope = content[brace_pos + 1:]
                depth = 1
                enum_body_chars = []
                state = 'code'
                escape = False
                e_i = 0
                while e_i < len(enum_scope):
                    char = enum_scope[e_i]
                    next_char = enum_scope[e_i+1] if e_i + 1 < len(enum_scope) else ''
                    
                    if state == 'block_comment':
                        if char == '*' and next_char == '/':
                            state = 'code'
                            e_i += 2
                            continue
                        e_i += 1
                        continue
                    elif state == 'line_comment':
                        if char == '\n':
                            state = 'code'
                        e_i += 1
                        continue
                    elif state == 'string':
                        if escape: escape = False
                        elif char == '\\': escape = True
                        elif char == '"': state = 'code'
                        e_i += 1
                        continue
                    elif state == 'char':
                        if escape: escape = False
                        elif char == '\\': escape = True
                        elif char == "'": state = 'code'
                        e_i += 1
                        continue
                    elif state == 'code':
                        if char == '/' and next_char == '*':
                            state = 'block_comment'
                            e_i += 2
                            continue
                        elif char == '/' and next_char == '/':
                            state = 'line_comment'
                            e_i += 2
                            continue
                        elif char == '"':
                            state = 'string'
                            escape = False
                            e_i += 1
                            continue
                        elif char == "'":
                            state = 'char'
                            escape = False
                            e_i += 1
                            continue
                            
                        if char == '{':
                            depth += 1
                        elif char == '}':
                            depth -= 1
                            if depth == 0:
                                break
                        
                        if depth == 1:
                            enum_body_chars.append(char)
                        e_i += 1
                        
                enum_body = ''.join(enum_body_chars)
                constants_part = enum_body.split(';', 1)[0].strip()
                
                constants = []
                current_c = []
                p_depth = 0
                for char in constants_part:
                    if char == '(':
                        p_depth += 1
                    elif char == ')':
                        p_depth -= 1
                    if char == ',' and p_depth == 0:
                        constants.append(''.join(current_c).strip())
                        current_c = []
                    else:
                        current_c.append(char)
                if current_c:
                    constants.append(''.join(current_c).strip())
                    
                valid_constants = []
                for c in constants:
                    c_clean = c.strip()
                    name_match = re.match(r'^(\w+)', c_clean)
                    if name_match:
                        valid_constants.append(f"  - Enum Constant : {c_clean}")
                if valid_constants:
                    outline.append("\nEnum Constants:")
                    outline.extend(valid_constants)
                    
        # Extract members
        constructors, methods, fields = parse_members_with_braces(content, name, start)
        
        if constructors:
            outline.append("\nConstructors:")
            outline.extend(constructors)
        if methods:
            outline.append("\nMethods:")
            outline.extend(methods)
        if fields:
            outline.append("\nFields:")
            outline.extend(fields)
            
    return "\n".join(outline)

def parse_sql_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"
    
    tables = re.findall(r'(?i)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([\w\.\"`\']+)', content)
    indices = re.findall(r'(?i)CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?([\w\.\"`\']+)', content)
    alters = re.findall(r'(?i)ALTER\s+TABLE\s+([\w\.\"`\']+)', content)
    
    outline = []
    if tables:
        outline.append("Created Tables:")
        outline.extend([f"- {t}" for t in tables])
    if indices:
        outline.append("Created Indices:")
        outline.extend([f"- {idx}" for idx in indices])
    if alters:
        outline.append("Table Alterations:")
        outline.extend([f"- {a}" for a in alters])
        
    if not outline:
        lines = [line.strip() for line in content.split('\n') if line.strip() and not line.strip().startswith('--')]
        if lines:
            outline.append("Queries/Statements:")
            outline.extend([f"- {line[:80]}" for line in lines[:5]])
        else:
            outline.append("Empty SQL File")
            
    return "\n".join(outline)

def parse_yaml_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"
        
    keys = []
    for line in content.split('\n'):
        if line.strip() and not line.strip().startswith('#'):
            match = re.match(r'^([\w\-]+)\s*:', line)
            if match:
                keys.append(match.group(1))
    if keys:
        return "Top-Level Config Keys:\n" + "\n".join([f"- {k}" for k in keys])
    return "Empty or unstructured YAML"

def parse_gradle_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"
        
    plugins = re.findall(r'id\s+[\'"]([\w\.]+)[\'"]', content)
    deps = re.findall(r'(?:implementation|runtimeOnly|testImplementation|compileOnly)\s+[\'"]([^\'"]+)[\'"]', content)
    if not deps:
        deps = re.findall(r'(?:implementation|runtimeOnly|testImplementation|compileOnly)\s+group:\s*[\'"]([^\'"]+)[\'"],\s*name:\s*[\'"]([^\'"]+)[\'"]', content)
        deps = [f"{g}:{n}" for g, n in deps]
        
    outline = []
    if plugins:
        outline.append("Applied Plugins:")
        outline.extend([f"- {p}" for p in plugins])
    if deps:
        outline.append("Key Dependencies:")
        outline.extend([f"- {d}" for d in deps[:20]])
        if len(deps) > 20:
            outline.append(f"- *...and {len(deps)-20} more*")
            
    return "\n".join(outline) if outline else "Gradle Build Settings"

def parse_properties_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"
        
    props = []
    for line in content.split('\n'):
        if line.strip() and not line.strip().startswith('#'):
            parts = line.split('=', 1)
            if parts:
                props.append(parts[0].strip())
    if props:
        return "Properties Defined:\n" + "\n".join([f"- {p}" for p in props])
    return "No properties defined"

def parse_xml_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"
        
    appenders = re.findall(r'<appender\s+name="([^"]+)"', content)
    loggers = re.findall(r'<logger\s+name="([^"]+)"', content)
    root_level = re.findall(r'<root\s+level="([^"]+)"', content)
    
    outline = []
    if appenders:
        outline.append("Appenders:")
        outline.extend([f"- {a}" for a in appenders])
    if loggers:
        outline.append("Loggers:")
        outline.extend([f"- {l}" for l in loggers])
    if root_level:
        outline.append(f"Root Log Level: {root_level[0]}")
        
    return "\n".join(outline) if outline else "XML Configuration"

def parse_md_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"
        
    lines = content.split('\n')
    title = ""
    for line in lines:
        if line.strip().startswith('#'):
            title = line.strip().lstrip('#').strip()
            break
            
    if title:
        return f"Markdown Document: {title}"
    return "Markdown Document"

def get_file_outline(rel_path, abs_path):
    ext = os.path.splitext(rel_path)[1].lower()
    
    if ext == '.java':
        return parse_java_file(abs_path)
    elif ext == '.sql':
        return parse_sql_file(abs_path)
    elif ext in ('.yml', '.yaml'):
        return parse_yaml_file(abs_path)
    elif rel_path.endswith('gradle'):
        return parse_gradle_file(abs_path)
    elif ext == '.properties':
        return parse_properties_file(abs_path)
    elif ext == '.md':
        return parse_md_file(abs_path)
    elif ext == '.xml':
        return parse_xml_file(abs_path)
    else:
        return "Binary or Text Resource File"

def get_tracked_files():
    try:
        res = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True)
        files = [line.strip() for line in res.stdout.split('\n') if line.strip()]
        return files
    except Exception:
        ignore_dirs = {'.git', '.gradle', '.idea', 'build', 'out', 'node_modules', 'bin'}
        files = []
        for root, dirs, filenames in os.walk(WORKSPACE_DIR):
            dirs[:] = [d for d in dirs if d not in ignore_dirs]
            for f in filenames:
                full_path = os.path.join(root, f)
                rel_path = os.path.relpath(full_path, WORKSPACE_DIR).replace(os.sep, '/')
                files.append(rel_path)
        return files

def remove_old_outline(content, comment_ext):
    if comment_ext in ('.java', '.gradle'):
        pattern = r'(?ms)^\s*//\s*\[OUTLINE START\].*?//\s*\[OUTLINE END\]\r?\n?'
    elif comment_ext == '.sql':
        pattern = r'(?ms)^\s*--\s*\[OUTLINE START\].*?--\s*\[OUTLINE END\]\r?\n?'
    elif comment_ext in ('.properties', '.yml', '.yaml', '.gitignore', '.env.example'):
        pattern = r'(?ms)^\s*#\s*\[OUTLINE START\].*?#\s*\[OUTLINE END\]\r?\n?'
    elif comment_ext in ('.xml', '.md'):
        pattern = r'(?ms)^\s*<!--\s*\[OUTLINE START\].*?\[OUTLINE END\]\s*-->\r?\n?'
    else:
        return content
        
    return re.sub(pattern, '', content)

def wrap_outline(outline_text, comment_ext):
    lines = outline_text.split('\n')
    if comment_ext in ('.java', '.gradle'):
        commented = ["// [OUTLINE START]"]
        for line in lines:
            commented.append(f"// {line}")
        commented.append("// [OUTLINE END]\n")
        return '\n'.join(commented)
    elif comment_ext == '.sql':
        commented = ["-- [OUTLINE START]"]
        for line in lines:
            commented.append(f"-- {line}")
        commented.append("-- [OUTLINE END]\n")
        return '\n'.join(commented)
    elif comment_ext in ('.properties', '.yml', '.yaml', '.gitignore', '.env.example'):
        commented = ["# [OUTLINE START]"]
        for line in lines:
            commented.append(f"# {line}")
        commented.append("# [OUTLINE END]\n")
        return '\n'.join(commented)
    elif comment_ext in ('.xml', '.md'):
        commented = ["<!-- [OUTLINE START]"]
        for line in lines:
            commented.append(line)
        commented.append("[OUTLINE END] -->\n")
        return '\n'.join(commented)
    return ""

def insert_xml_outline(content, outline_block):
    match = re.match(r'^\s*(<\?xml.*?\?>)\s*', content, re.DOTALL)
    if match:
        header = match.group(1)
        rest = content[match.end():]
        return f"{header}\n\n{outline_block}\n{rest}"
    return f"{outline_block}\n{content}"

def update_file_in_place(rel_path, abs_path, outline_text):
    ext = os.path.splitext(rel_path)[1].lower()
    filename = os.path.basename(rel_path)
    
    if ext not in EXTS and filename not in SPECIAL_FILES:
        return
        
    try:
        with open(abs_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {rel_path}: {e}")
        return
        
    comment_ext = ext if ext else filename
    cleaned_content = remove_old_outline(content, comment_ext)
    outline_block = wrap_outline(outline_text, comment_ext)
    
    if comment_ext == '.xml':
        new_content = insert_xml_outline(cleaned_content, outline_block)
    else:
        new_content = f"{outline_block}\n{cleaned_content}"
        
    try:
        with open(abs_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated {rel_path} with outline.")
    except Exception as e:
        print(f"Error writing {rel_path}: {e}")

def main():
    files = get_tracked_files()
    
    for f_path in files:
        if f_path in ("scripts/generate_outline.py", "repo_outline.md"):
            continue
            
        abs_path = os.path.join(WORKSPACE_DIR, f_path.replace('/', os.sep))
        outline_content = get_file_outline(f_path, abs_path)
        
        update_file_in_place(f_path, abs_path, outline_content)

if __name__ == "__main__":
    main()
