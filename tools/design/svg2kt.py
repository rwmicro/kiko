import re, math, sys
H = open(sys.argv[3] if len(sys.argv) > 3 else 'design/prototype-lezo.html', encoding='utf-8').read()

def symbol(sid):
    return re.search(r'<symbol[^>]*id="%s"([^>]*)>(.*?)</symbol>' % re.escape(sid), H, re.S)

TOK = re.compile(r'[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?')
def norm(d):
    out = []; i = 0; cmd = None; argi = 0
    s = d
    pos = 0
    res = []
    while pos < len(s):
        ch = s[pos]
        if ch in ' ,\n\t': pos += 1; continue
        if ch.isalpha():
            cmd = ch; res.append(ch); argi = 0; pos += 1; continue
        # arc flags: args index 3,4 within each group of 7 are single digits
        if cmd in 'Aa' and argi % 7 in (3, 4):
            res.append(ch); pos += 1; argi += 1; continue
        m = re.match(r'[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?', s[pos:])
        res.append(m.group(0)); pos += len(m.group(0)); argi += 1
    return ' '.join(res)

def fmt(x):
    x = round(float(x), 3)
    return ('%g' % x)

def ellipse_d(cx, cy, rx, ry, rot=0.0):
    a = math.radians(rot)
    dx, dy = rx * math.cos(a), rx * math.sin(a)
    sx, sy = cx - dx, cy - dy
    return f'M {fmt(sx)} {fmt(sy)} A {fmt(rx)} {fmt(ry)} {fmt(rot)} 1 0 {fmt(cx+dx)} {fmt(cy+dy)} A {fmt(rx)} {fmt(ry)} {fmt(rot)} 1 0 {fmt(sx)} {fmt(sy)} Z'

def rect_d(x, y, w, h, r):
    r = min(r, w/2, h/2)
    return (f'M {fmt(x+r)} {fmt(y)} H {fmt(x+w-r)} A {fmt(r)} {fmt(r)} 0 0 1 {fmt(x+w)} {fmt(y+r)} V {fmt(y+h-r)} '
            f'A {fmt(r)} {fmt(r)} 0 0 1 {fmt(x+w-r)} {fmt(y+h)} H {fmt(x+r)} A {fmt(r)} {fmt(r)} 0 0 1 {fmt(x)} {fmt(y+h-r)} '
            f'V {fmt(y+r)} A {fmt(r)} {fmt(r)} 0 0 1 {fmt(x+r)} {fmt(y)} Z')

def attrs(tag):
    return dict(re.findall(r'([\w:-]+)="([^"]*)"', tag))

def walk(body, inherited, out):
    # simple tokenizer over tags
    stack = [inherited]
    for m in re.finditer(r'<(/?)(\w+)([^>]*?)(/?)>', body):
        close, name, rest, selfclose = m.groups()
        if close:
            if name == 'g': stack.pop()
            continue
        a = attrs(rest)
        st = dict(stack[-1]); 
        for k in ('fill', 'stroke', 'stroke-width', 'opacity', 'stroke-linecap', 'stroke-linejoin'):
            if k in a: st[k] = a[k]
        if name == 'g':
            if not selfclose: stack.append(st)
            continue
        if name == 'use':
            ref = a.get('href', a.get('xlink:href'))[1:]
            walk(symbol(ref).group(2), st, out); continue
        if name == 'path': d = norm(a['d'])
        elif name == 'circle': d = ellipse_d(float(a['cx']), float(a['cy']), float(a['r']), float(a['r']))
        elif name == 'ellipse':
            rot = 0.0
            t = a.get('transform', '')
            mm = re.match(r'rotate\(([-\d.]+)', t)
            if mm: rot = float(mm.group(1))
            d = ellipse_d(float(a['cx']), float(a['cy']), float(a['rx']), float(a['ry']), rot)
        elif name == 'rect':
            d = rect_d(float(a.get('x', 0)), float(a.get('y', 0)), float(a['width']), float(a['height']), float(a.get('rx', 0)))
        else:
            continue
        out.append((d, st))

def color(c, alpha=1.0):
    if c in (None, 'none'): return None
    c = c.lstrip('#')
    if c == 'fff': c = 'ffffff'
    return 'Color(0xFF%s)' % c.upper()

def kt_vector(sid, name, mono, default_stroke=1.9):
    m = symbol(sid)
    vb = re.search(r'viewBox="([^"]+)"', m.group(1)).group(1).split()
    out = []
    base = {'fill': 'none', 'stroke': 'currentColor', 'stroke-width': str(default_stroke)} if mono else {'fill': 'black'}
    walk(m.group(2), base, out)
    lines = [f'ImageVector.Builder("{name}", {fmt(vb[2])}.dp, {fmt(vb[3])}.dp, {fmt(vb[2])}f, {fmt(vb[3])}f)']
    for d, st in out:
        op = float(st.get('opacity', 1))
        if mono:
            fill = 'null' if st.get('fill') == 'none' else 'SolidColor(Color.Black)'
            stroke = 'null' if st.get('stroke') == 'none' else 'SolidColor(Color.Black)'
        else:
            f = color(st.get('fill')); s = color(st.get('stroke'))
            fill = f'SolidColor({f})' if f else 'null'
            stroke = f'SolidColor({s})' if s else 'null'
        sw = fmt(st.get('stroke-width', default_stroke if mono else 1))
        lines.append(f'    .addPath(addPathNodes("{d}"), fill = {fill}, fillAlpha = {fmt(op)}f, stroke = {stroke}, strokeAlpha = {fmt(op)}f, strokeLineWidth = {sw}f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)')
    lines.append('    .build()')
    return '\n'.join(lines)

if __name__ == '__main__':
    print(kt_vector(sys.argv[1], sys.argv[1], sys.argv[2] == 'mono'))
