# MingYue课程表

一款基于 Kotlin + Jetpack Compose 的 Android 课程表应用，支持 PDF/JSON 导入、桌面小部件、**小米手环通知提醒**。

## 功能

- 📄 **PDF 课表导入** — 自动解析学校教务系统导出的 PDF 课表
- 📋 **JSON 导入** — 支持 JSON 格式课表数据
- 📅 **周次滑动** — 原生 ViewPager2 横向翻页，丝滑切换周次
- 🧩 **桌面小部件** — 2×2 小部件，桌面直达今日课程
- ⌚ **手环通知** — 上课前自动推送通知到小米手环，手环振动+弹卡片
- 🎨 **课程颜色** — 自动配色，多课程时间冲突检测
- ➕ **手动添加** — 长按空格子快速添加课程
- 🗑️ **灵活删除** — 支持仅删本周或删除所有周

## 下载

[📥 下载 APK (v1.1.0)](https://github.com/YuanZhishan123/CourseSchedule-Android/releases/download/v1.1.0/MingYue%E8%AF%BE%E7%A8%8B%E8%A1%A8.apk)

## 手环通知设置

1. 确保手机已安装「小米运动健康」并配对手环
2. 打开「小米运动健康」→ 设备 → 手环 → 通知与提醒 → 应用通知管理
3. 勾选「MingYue课程表」
4. 上课前 10 分钟，手环会自动振动并显示课程信息

## 技术栈

- Kotlin + Jetpack Compose
- Material 3
- ViewPager2
- PdfBox-Android
- Android AppWidget (RemoteViews)
- AlarmManager + NotificationManager

## 开发

```bash
# 编译
./gradlew assembleDebug

# 安装到手机
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 许可证

MIT License
