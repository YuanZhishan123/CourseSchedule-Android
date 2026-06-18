"""
⚠️ 已弃用 — 仅保留作为参考
用 PyMuPDF 解析方正教务系统 PDF 课表，输出 JSON。
已被 CourseSchedule/app/.../parser/*.kt (纯 Kotlin + PdfBox) 替代。
2026-06-18: 标记弃用
"""
import fitz, json, re

def parse_pdf(path):
    doc = fitz.open(path)
    full_text = ''
    for page in doc:
        full_text += page.get_text('text')

    # 规范化
    text = full_text.replace('\r\n', '\n').replace('\r', '\n')
    text = text.replace(':\n', ':').replace('\n:', ':')
    text = text.replace('(\n', '(').replace('\n(', '(')
    text = text.replace('\n/', '/').replace('/\n', '/')

    # 学期
    semester = '未知学期'
    m = re.search(r'(\d{4}-\d{4}\s*学年第?\s*\d\s*学期)', text)
    if m: semester = m.group(1).strip()

    # 学生
    student_name = '未知'
    m = re.search(r'([一-鿿]{2,4})\s*课表', text)
    if m: student_name = m.group(1)
    student_id = ''
    m = re.search(r'学号[:：]\s*(\d+)', text)
    if m: student_id = m.group(1)

    # 解析所有课程
    courses = parse_courses(text)

    result = {
        'semester': semester,
        'studentName': student_name,
        'studentId': student_id,
        'courses': courses,
        'totalWeeks': 20,
    }
    return result

def parse_courses(text):
    """按列解析 PDF 课表文本，识别 day/period/weeks/teacher/location"""
    COLOR_PALETTE = [0xFFE8A0BF, 0xFFB4A7D6, 0xFF7EC8A0, 0xFF6CB4E4,
                     0xFFF0B866, 0xFFE8836E, 0xFF98D8C8, 0xFFC9B1D5,
                     0xFFF5A0A0, 0xFF87CEEB]

    # 检测表头中星期列的顺序
    header_day_order = [1,2,3,4,5,6,7]  # 默认周一~周日
    m = re.search(r'周([一二三四五六日])\s*周([一二三四五六日])\s*周([一二三四五六日])\s*周([一二三四五六日])\s*周([一二三四五六日])\s*周([一二三四五六日])\s*周([一二三四五六日])', text)
    if m:
        day_map = {'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'日':7}
        header_day_order = [day_map[m.group(i)] for i in range(1,8)]

    # 找到所有节次标记
    period_pattern = re.compile(r'[（(](\d+)[-–—](\d+)节[)）]')
    all_matches = list(period_pattern.finditer(text))
    if not all_matches:
        return []

    # 提取每个课程条目
    entries = []
    for idx, match in enumerate(all_matches):
        start_pos = match.start()
        end_pos = match.end()
        sp = int(match.group(1))
        ep = int(match.group(2))
        if sp < 1 or sp > 12: continue

        # 课程名：节次标记之前最近的中文行
        before = text[max(0, start_pos-200):start_pos]
        name = extract_name(before)
        if not name: continue

        # 详情文本
        detail_end = all_matches[idx+1].start()-1 if idx+1 < len(all_matches) else min(len(text), end_pos+500)
        detail = text[end_pos:detail_end]

        # 周次
        weeks = extract_weeks(detail)

        # 教师
        teacher = '未知'
        tm = re.search(r'教师[:：]\s*([^/\n]+)', detail)
        if tm: teacher = tm.group(1).strip()

        # 地点
        location = extract_location(detail)

        # 星期：在上下文中搜索
        day = None
        context = text[max(0, start_pos-300):start_pos]
        for dw, dnum in [('周一',1),('周二',2),('周三',3),('周四',4),('周五',5),('周六',6),('周日',7)]:
            if dw in context:
                day = dnum
                break

        entries.append({
            'pos': start_pos, 'sp': sp, 'ep': ep,
            'name': name, 'weeks': weeks, 'teacher': teacher,
            'location': location, 'day': day,
        })

    # 对没有找到 day 的条目，按 position + period 分组推断
    # 按 startPeriod 分组，每组内按位置排序，分配 day
    by_period = {}
    for e in entries:
        by_period.setdefault(e['sp'], []).append(e)
    for period, group in by_period.items():
        group.sort(key=lambda x: x['pos'])
        for i, e in enumerate(group):
            if e['day'] is None:
                e['day'] = (i % 7) + 1

    # 构建 Course 列表
    courses = []
    for i, e in enumerate(entries):
        if e['day'] is None: e['day'] = (i % 7) + 1
        courses.append({
            'name': e['name'],
            'day': e['day'],
            'startPeriod': e['sp'],
            'endPeriod': e['ep'],
            'weeks': e['weeks'],
            'location': e['location'],
            'teacher': e['teacher'],
            'color': COLOR_PALETTE[i % len(COLOR_PALETTE)],
            'detail': '',
        })

    # 去重排序
    seen = set()
    unique = []
    for c in courses:
        key = (c['name'], c['day'], c['startPeriod'])
        if key not in seen:
            seen.add(key)
            unique.append(c)
    unique.sort(key=lambda c: (c['day'], c['startPeriod']))
    return unique

def extract_name(before):
    """从节次标记前的文本中提取课程名"""
    lines = [l.strip() for l in before.split('\n') if l.strip()]
    candidates = []
    for line in reversed(lines):
        # 硬过滤
        if re.match(r'^\d{1,2}\s*$', line): continue
        if '：' in line or ':' in line: continue
        if re.match(r'^\d+\s*$', line): continue
        if re.search(r'\d+[-–—]\d+节', line): continue
        if '周' in line and len(line) < 15: continue
        if re.search(r'星期[一二三四五六日]', line): continue
        if re.search(r'^[（(]\d+', line): continue
        if re.match(r'^\d{4}\s', line): continue

        # 去标签
        line = re.sub(r'[\[［].*?[\]］]', '', line).strip()

        # 过滤含特殊字符
        if any(c in '@/()（）' for c in line): continue
        if re.match(r'^\d+[A-Z]\d+', line): continue

        if line and 2 <= len(line) <= 50:
            # 去掉尾部符号
            line = line.rstrip('：: -_,，.')
            # 去掉开头的数字+空格
            line = re.sub(r'^[\d\s]+', '', line)
            if line and len(line) >= 2:
                candidates.append(line)

    # 选含中文且最长的
    chinese_candidates = [c for c in candidates if any('一' <= ch <= '鿿' for ch in c)]
    if chinese_candidates:
        return max(chinese_candidates, key=len)
    return None

def extract_weeks(detail):
    weeks = []
    for m in re.finditer(r'(\d+)(?:[-–—](\d+))?周', detail):
        s = int(m.group(1))
        e = int(m.group(2)) if m.group(2) else s
        weeks.extend(range(s, e+1))
    if not weeks:
        for m in re.finditer(r'(\d+)周', detail):
            w = int(m.group(1))
            if 1 <= w <= 25: weeks.append(w)
    return sorted(set(weeks))

def extract_location(detail):
    m = re.search(r'教室[:：]\s*(\([^)]*\))?\s*([^\n/]+)', detail)
    if m:
        prefix = m.group(1).strip('()') if m.group(1) else ''
        room = m.group(2).strip()
        return f'({prefix}){room}' if prefix else room
    m = re.search(r'楼宇[:：]([^/\n]+)', detail)
    if m:
        b = m.group(1).strip()
        rm = re.search(r'([一-鿿]+楼\s*\w*\d+)', detail)
        return f'{b}/{rm.group(1)}' if rm else b
    m = re.search(r'([一-鿿]+楼\s*\w*\d+)', detail)
    if m: return m.group(1)
    return '未知地点'

if __name__ == '__main__':
    result = parse_pdf('D:/code/subject-task/滕家锋(2025-2026-2)课表.pdf')
    print(json.dumps(result, ensure_ascii=False, indent=2))
