# 动屿 Dongyu

一款本地优先的安卓运动记录 App，不只跑步：内置跑步、爬山/徒步、登山、游泳、骑行，支持自定义运动类型和每种运动的自定义指标，并提供丰富的统计图表。

## 功能
- GPS 轨迹记录（前台服务、自动暂停），开源地图（MapLibre / OSM）
- 自定义运动类型与指标
- 统计仪表盘：趋势、分布、热力图、个人纪录
- 蓝牙心率带（标准心率服务 0x180D）与心率区间
- 分享卡片：3:4 / 1:1 / 9:16，多种风格，周报月报
- GPX / TCX / FIT 导入导出，整库备份与恢复

## 技术栈
Kotlin · Jetpack Compose · Material 3 · Room · DataStore · WorkManager

## 构建
```
./gradlew assembleDebug
```
发布签名需要自行在项目根目录创建 `keystore.properties`（已被 git 忽略）。
