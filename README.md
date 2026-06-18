# MingYue课程表

一款基于 Kotlin + Jetpack Compose 的 Android 课程表应用，支持 PDF/JSON 导入、桌面小部件。

## 功能

- 📄 **PDF 课表导入** — 自动解析学校教务系统导出的 PDF 课表
- 📋 **JSON 导入** — 支持 JSON 格式课表数据
- 📅 **周次滑动** — 原生 ViewPager2 横向翻页，丝滑切换周次
- 🧩 **桌面小部件** — 2×2 小部件，桌面直达今日课程
- 🎨 **课程颜色** — 自动配色，多课程时间冲突检测
- ➕ **手动添加** — 长按空格子快速添加课程
- 🗑️ **灵活删除** — 支持仅删本周或删除所有周

## 下载

[📥 下载 APK (v1.0.0)](https://github.com/YuanZhishan123/MingYue-schedule/releases/download/v1.0.0/MingYue.apk)

## 技术栈

- Kotlin + Jetpack Compose
- Material 3
- ViewPager2
- PdfBox-Android
- Android AppWidget (RemoteViews)

## 开发

```bash
# 编译
./gradlew assembleDebug

# 安装到手机
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 许可证

MIT License
