# SAMR21 2.13 NFC e-Paper

LG Innotek ESL TAG2.13B 的 **Keil5 C 固件 + 原生 Android 图片编辑与 NFC 上传应用**。

> **v0.1.0 是实验预览版。** 已实测 MCU → I²C → NFC SRAM → 协议校验 → 屏幕刷新；手机 NFC 空中链路与 Android 实机界面尚未联调，不能把这些测试当成手机传图成功。

## 下载与使用

在 [Releases](https://github.com/NannXi/SAMR21-213-NFC/releases) 下载 `epaper-nfc-v0.1.0-debug.apk`。Android 8.0 及以上，手机需 NFC-A；当前 APK 使用开发测试签名。不开启联网，不申请照片库或存储权限，通过系统文件选择器选图。

1. 主板保持原来已验证的 **3.3 V 供电**，烧录配套固件，等待上电示例画面刷新完成（约 15 秒）。
2. 手机安装 APK，打开 NFC，选择图片。
3. 在 **212×104** 横向画布上拖动裁切、缩放或旋转；调整亮度、对比度、黑白红 / 黑白模式与抖动（关闭、Bayer、Floyd–Steinberg）。
4. 点“上传到墨水屏”，应用提示靠近 NFC。识别固件签名、尺寸和 UID 后，显示“请勿移动手机”。
5. 逐块确认后显示进度；完整 CRC32 通过才提交刷新。等待刷新完成确认后，才能移开手机。

镜像需要主板供电。本版尚未实现 NFC 单独供能刷新、低功耗、掉电保存图片或断点续传。传输中断后重传整图。**不要在传输中切断主板电源**：当前方案把 SRAM 映射到 RF 用户页，失去 VCC 后该地址会回到 EEPROM，继续发送可能影响原 NDEF；现场 EEPROM 已备份，RF 供电跌落恢复尚未验证。

应用预览只包含黑白红实际像素。亮度和对比度是图片处理参数，不是屏幕电压或物理对比度设置；前期实物偏淡现象仍存在。

## 工程入口

| 目录 | 内容 |
|---|---|
| [firmware](firmware/README.md) | ARMCC5 C/ASM、Keil `.uvprojx`、NFC 驱动、协议、原屏幕驱动及 SWD 下载恢复工具 |
| [android](android/README.md) | 无第三方依赖的 Java Android 应用、可复现的 SDK 工具链构建脚本 |
| [协议说明](docs/PROTOCOL.md) | SRAM、64 字节帧、CRC、状态机、双平面图像坐标 |
| [硬件与验证](docs/HARDWARE_AND_VALIDATION.md) | 接线、实测证据、供电边界和未确认事项 |
| [测试](tests/README.md) | 图像/协议核心测试，实板 SWD→I²C 邮箱测试 |

原始照片、完整硬件介绍、原厂分析和最初显示工程继续保存在 [SAMR21-213-EPaper](https://github.com/NannXi/SAMR21-213-EPaper)。本仓库独立维护 NFC 固件和手机软件，不改写旧项目。

## 当前验证状态

- ARM Compiler 5 编译、实机烧录、256 KiB Flash 全量回读、未写区及辅助区一致。
- NFC I²C 地址 `0x55`，SRAM `0xF8..0xFB` 64 字节读写、会话寄存器镜像配置实测。
- 整图 5512 字节传输、重复分块、错误偏移、包 CRC 拒收、提交后刷新与 DONE 确认实测；见验证 JSON。
- Android 核心 Java 单测通过，APK 编译并通过 v2/v3 签名验证。
- **待测：Android 真机运行、RF ↔ SRAM、RF/I²C 仲裁、实际传输速度、手机离场与电压跌落恢复。**
- NFC 芯片具体型号尚未确定，行为与 NXP NTAG I²C 兼容；不能仅靠寄存器布局断言为某个 Plus 型号。

日期：2026-10-21。
