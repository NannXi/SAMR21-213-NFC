# 墨屏 · SAMR21 NFC e-Paper

LG Innotek ESL TAG2.13B 的 **Keil5 C/ASM 固件 + 原生 Android 图片编辑与 NFC 上传应用**。在横向 212×104 画布上选图、裁切、调色，再贴近价签上传。

<img src="android/artwork/icon-preview.png" alt="墨屏黑白红像素图标" width="112">

**当前组合：Android v0.1.4 + 板端固件 v0.1.2。** 用户已确认 v0.1.2 手机上传成功；v0.1.3/v0.1.4 的新界面、固定预览与常亮仍待手机验收。

[下载 APK](https://github.com/NannXi/SAMR21-213-NFC/releases/download/v0.1.4/epaper-nfc-v0.1.4-debug.apk) · [下载板端 v0.1.2](https://github.com/NannXi/SAMR21-213-NFC/releases/tag/v0.1.2) · [版本与校验](docs/RELEASES.md) · [快速开始](docs/GETTING_STARTED.md)

## 使用流程

1. 给主板稳定供电，首次烧录板端 v0.1.2，等上电示例刷新结束（本板约 15 秒）。已经运行此固件时，只需升级 APK。
2. 手机安装应用并打开 NFC。Android 8.0/API26 以上，需要 NFC-A；不需要 root 或 Shizuku。
3. 在固定的圆角预览中看效果；在“图像调整”里选图、顺时针旋转 90°、缩放并拖动裁切。
4. 调整亮度、对比度、黑白红/黑白、关闭/有序/扩散抖动，以及 0–100% 抖动强度。
5. 点击“上传到墨水屏”，按提示靠近线圈。识别后保持位置，等待传输和刷新完成，再移开手机。

等待连接、传图、刷新期间应用保持亮屏，完成、取消、失败或进入后台后释放。应用只申请 NFC 权限；系统文件选择器授予单张图片访问，不申请联网、照片库或存储权限。

预览是三色实际像素，不能代表实物反射对比度。图片亮度/对比度不会调节屏幕电压；本板物理偏淡问题仍存在。

## 仓库导航

| 入口 | 内容 |
|---|---|
| [快速开始](docs/GETTING_STARTED.md) | 接线、首次烧录、安装与传图 |
| [版本与发布包](docs/RELEASES.md) | APK/固件兼容、签名与 SHA-256 |
| [故障排查](docs/TROUBLESHOOTING.md) | 未识别、TagLost、transceive failed、刷新和诊断 |
| [Android 工程](android/README.md) | JDK/SDK 命令行构建、界面、图标生成 |
| [Keil 固件](firmware/README.md) | 构建、SWD 烧录、状态与原厂恢复 |
| [架构](docs/ARCHITECTURE.md) / [协议](docs/PROTOCOL.md) | 图片处理、RF/SRAM 交接、双平面和 64 字节帧 |
| [硬件与验证](docs/HARDWARE_AND_VALIDATION.md) | 证据文件、手机反馈与未验证事项 |
| [测试](tests/README.md) | Java 核心测试、离线仓库检查、实板测试边界 |
| [更新记录](docs/CHANGELOG.md) | 从 v0.1.0 到 v0.1.4 的修订 |
| [贡献说明](CONTRIBUTING.md) / [来源说明](NOTICE.md) | 复核流程、反馈、许可证边界 |

原始照片、完整主板资源、原厂固件分析与最初显示工程位于 [SAMR21-213-EPaper](https://github.com/NannXi/SAMR21-213-EPaper)。两个工程各自保留匹配的 AXF 和下载工具，避免交叉使用 RAM 地址。

## 当前边界

| 已确认 | 尚待验证或实现 |
|---|---|
| 用户反馈 v0.1.2 手机端到端上传成功 | v0.1.4 布局与常亮实机验收、多机兼容、吞吐和移场恢复 |
| 实板 SRAM 整图 5512 字节、重复包、偏移/CRC 拒收、刷新与 DONE | VCC 跌落时 RF 镜像恢复、稳定性与续航测量 |
| 完整 Flash 回读、未写区/辅助区、NFC EEPROM 比对 | NFC 精确芯片料号、屏幕驱动 IC |
| Android 构建、v2/v3 签名、核心算法测试 | NFC 单独供能刷新、整机低功耗、掉电保存、断点续传 |

本方案依赖主板 VCC。传输时保持稳定供电：掉电后 SRAM 镜像地址可能回到 EEPROM，继续 RF 写有影响原 NDEF 的风险。尚未发布自动恢复 EEPROM 或更改锁位的功能，详见[硬件与验证](docs/HARDWARE_AND_VALIDATION.md)。

整理日期：2026-10-22。当前应用是开发签名的预发布版本；本次资料整理未改动板端固件和手机应用逻辑。
