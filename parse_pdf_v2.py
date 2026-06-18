"""
⚠️ 已弃用 — 仅保留作为参考
用 PyMuPDF 按坐标解析 PDF 课表 - 精准版。
已被 CourseSchedule/app/.../parser/*.kt (纯 Kotlin + PdfBox) 替代。
2026-06-18: 标记弃用
"""
import fitz, json, re
from collections import defaultdict

def parse_pdf_coords(path):
    doc = fitz.open(path)

    # 收集所有文本行及其坐标
    all_lines = []  # (page, x0, y0, text)
    for page_num in range(doc.page_count):
        page = doc[page_num]
        blocks = page.get_text('dict')['blocks']
        for block in blocks:
            if 'lines' not in block: continue
            for line in block['lines']:
                text = ''.join([span['text'] for span in line['spans']]).strip()
                if text:
                    x0 = line['bbox'][0]
                    y0 = line['bbox'][1]
                    all_lines.append((page_num, x0, y0, text))

    # === 识别表结构 ===
    # 找到星期标签的 Y 坐标
    day_labels = []
    day_map = {'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'日':7}
    for _, x, y, text in all_lines:
        m = re.match(r'星期([一二三四五六日])', text)
        if m and x < 100:  # 星期标签在最左边
            day_labels.append((y, day_map[m.group(1)]))
    day_labels.sort(key=lambda d: d[0], reverse=True)  # 按Y降序(从上到下)
    print(f"Day labels: {day_labels}")

    # 找到节次编号的 X 坐标
    period_xs = []
    for _, x, y, text in all_lines:
        if re.match(r'^\d{1,2}$', text) and 1 <= int(text) <= 12:
            period_xs.append((x, int(text)))
    # 按 X 排序，去重取每个位置的第一个
    period_xs.sort()
    seen_x, unique_periods = set(), []
    for x, p in period_xs:
        x_rounded = round(x / 5) * 5
        if x_rounded not in seen_x:
            seen_x.add(x_rounded)
            unique_periods.append((x, p))
    unique_periods.sort()
    print(f"Period X positions: {unique_periods[:15]}")

    if not day_labels or not unique_periods:
        print("Failed to detect table structure")
        return None

    # === 解析课程 ===
    courses = []
    COLOR_PALETTE = [0xFFE8A0BF, 0xFFB4A7D6, 0xFF7EC8A0, 0xFF6CB4E4,
                     0xFFF0B866, 0xFFE8836E, 0xFF98D8C8, 0xFFC9B1D5,
                     0xFFF5A0A0, 0xFF87CEEB]

    # 为每行(day)收集课程块
    day_courses = defaultdict(list)  # day -> [(start_period, end_period, name, detail_text)]

    # 找所有节次标记
    period_pattern = re.compile(r'[（(](\d+)[-–—](\d+)节[)）]')

    for page_num, px, py, text in all_lines:
        m = period_pattern.search(text)
        if not m: continue

        sp = int(m.group(1))
        ep = int(m.group(2))
        if sp < 1 or sp > 12: continue

        # 确定 day: 找该行上方最近的星期标签
        day = 1
        min_dist = float('inf')
        for dy, dnum in day_labels:
            dist = abs(py - dy)
            if dist < min_dist:
                min_dist = dist
                day = dnum

        # 课程名: 同一页面中，节次标记前最近的非元数据行
        name = None
        # 找前面同 y 区域的行
        nearby_lines = [(x, y, t) for (p, x, y, t) in all_lines
                       if p == page_num and abs(y - py) < 60 and x < px]
        nearby_lines.sort(key=lambda l: l[0], reverse=True)  # 按x降序，离节次标记最近的在前

        for _, _, t in nearby_lines:
            t = t.strip()
            if not t: continue
            if re.match(r'^\d+$', t): continue
            if '：' in t or ':' in t: continue
            if re.search(r'\d+[-–—]\d+节', t): continue
            if re.search(r'星期[一二三四五六日]', t): continue
            if any(c in '@/()（）' for c in t): continue
            t = re.sub(r'[\[［].*?[\]］]', '', t).strip()
            if 2 <= len(t) <= 50:
                name = t
                break

        if not name: continue

        # 详情文本: 节次标记之后
        detail_start = text.find(m.group(0)) + len(m.group(0))
        detail = text[detail_start:].strip()

        # 周次
        weeks = []
        for wm in re.finditer(r'(\d+)(?:[-–—](\d+))?周', detail):
            ws = int(wm.group(1))
            we = int(wm.group(2)) if wm.group(2) else ws
            weeks.extend(range(ws, we+1))
        if not weeks:
            for wm in re.finditer(r'(\d+)周', detail):
                w = int(wm.group(1))
                if 1 <= w <= 25: weeks.append(w)
        weeks = sorted(set(weeks))

        # 教师
        teacher = '未知'
        tm = re.search(r'教师[:：]\s*([^/\n]+)', detail)
        if tm: teacher = tm.group(1).strip()

        # 地点
        location = '未知地点'
        rm = re.search(r'教室[:：]\s*(\([^)]*\))?\s*([^\n/]+)', detail)
        if rm:
            prefix = rm.group(1).strip('()') if rm.group(1) else ''
            room = rm.group(2).strip()
            location = f'({prefix}){room}' if prefix else room
        else:
            bm = re.search(r'楼宇[:：]([^/\n]+)', detail)
            if bm:
                room2 = re.search(r'([一-鿿]+楼\s*\w*\d+)', detail)
                location = f"{bm.group(1)}/{room2.group(1)}" if room2 else bm.group(1)
            else:
                rm3 = re.search(r'([一-鿿]+楼\s*\w*\d+)', detail)
                if rm3: location = rm3.group(1)

        courses.append({
            'name': name,
            'day': day,
            'startPeriod': sp,
            'endPeriod': ep,
            'weeks': weeks,
            'location': location,
            'teacher': teacher,
        })

    # 去重排序 & 加颜色
    seen = set()
    unique = []
    for c in courses:
        key = (c['name'], c['day'], c['startPeriod'])
        if key not in seen:
            seen.add(key)
            unique.append(c)
    unique.sort(key=lambda c: (c['day'], c['startPeriod']))

    for i, c in enumerate(unique):
        c['color'] = COLOR_PALETTE[i % len(COLOR_PALETTE)]
        c['detail'] = ''

    return {
        'semester': '2025-2026学年第2学期',
        'studentName': '滕家锋',
        'studentId': '2300305113',
        'courses': unique,
        'totalWeeks': 20,
    }

if __name__ == '__main__':
    result = parse_pdf_coords('D:/code/subject-task/滕家锋(2025-2026-2)课表.pdf')
    if result:
        with open('D:/code/subject-task/parsed_courses_v2.json', 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f'\nParsed {len(result["courses"])} courses')
        for c in result['courses']:
            print(f"  周{c['day']} {c['startPeriod']}-{c['endPeriod']}节 | {c['name']} | {c['teacher']} | {c['location']}")
