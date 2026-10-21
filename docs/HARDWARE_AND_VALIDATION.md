# 硬件与验证边界

对象是当前 LG Innotek ESL TAG2.13B Rev0.1 / REBE-TZ21B 板。MCU ATSAMR21G18A，256 KiB Flash / 32 KiB SRAM，自制固件 OSC8M 8 MHz。屏幕 WF0213T50CZ16E41e，横向 212×104，原生 104×212，黑白红，屏幕 IC 尚未确定。完整前期资料位于 [原仓库硬件文档](https://github.com/NannXi/SAMR21-213-EPaper/blob/main/docs/HARDWARE.md)。

## NFC 新实测

| 项目 | 本板结果 |
|---|---|
| I²C 七位地址 | 55h |
| SDA / SCL | PA16 / PA17，原 SERCOM1 引脚，v0.1.1 使用 SERCOM1 硬件 I²C |
| PA15 | 固件设为高，沿用原初始化，具体电路角色未单独测绘 |
| UID | 04 16 9A 6A 1E 44 80 |
| CC | E1 10 6D 00，声明 872 字节数据区，不是总容量证明 |
| 会话寄存器 | FE 的索引 0..6 可读；索引 7 先前探测 NAK |
| 启动会话快照 | 01 00 F8 48 08 01 00 |
| 镜像开启后 | 03 00 01 48 08 01 00 |
| SRAM | I²C 块 F8..FB，64 字节读写回读成功 |
| EEPROM 备份 | 0..55 块，896 字节，SHA256 aa2fadb624c42d9080efaa951cda56df7e70629cf3d91661896b21ebaacf4b55 |
| 精确料号 | 未确定；按实际响应和原代 NTAG I²C 寄存器实现，不硬称 NT3H2111 |

## 已验证证据

- `nfc_probe_validation.json`：UID、配置只读快照、SRAM 图样读写及恢复、会话镜像配置。
- `hardware_protocol_validation.json`：SWD 将请求交给 MCU 的调试邮箱，MCU **实际经 I²C 写入 NFC 芯片 SRAM**，协议轮询重新读出并处理；验证整图接收、重复包不重复计数、错误偏移和包 CRC 拒收、COMMIT 前不刷新、发布回应后刷新、DONE、图像逐字节一致、EEPROM 实读未变。
- `firmware_flash_validation.json`：当前固件全量 Flash 回读、未写区保留、辅助区不变、屏幕刷新完成。
- `android_build_validation.json`：APK 构建、签名验证与 Java 图像/协议测试。

SWD 测试没有经过手机的 RF 接口。测试中的调试命令与协议在同一 MCU 主循环执行，屏幕阻塞刷新时调试命令也会等待，所以 COMMIT ACK 的发布由 MCU RAM 标志辅助确认，刷新后再通过物理 I²C SRAM 读 DONE；不能把这一点当作手机在刷新中已读到 ACK。

## 待实机确认

Android 界面运行、RF ↔ SRAM 映射、手机读 UID 与签名、RF/I²C 仲裁、吞吐、移开手机恢复、RF 场上电的镜像状态、VCC 跌落、各种手机兼容性。用户手机已安装应用并提供失败现象，目前没有 ADB 连接用于直接抓取日志。芯片精确型号、射频供电刷新与电池续航仍未知。

当前应保留主板稳定 3.3 V 供电。原代 NXP 文档说明 SRAM 仅在 VCC 供电时可用，掉电后镜像地址回到 EEPROM；手机写到一半时断电有影响原 NDEF 的风险。本板 EEPROM 已留备份，但本版没有发布自动恢复 EEPROM 或修改锁定位的操作。

## 手工联调

1. 先安装 APK，确认选择图片、拖动、旋转、三种抖动和两个调节条运行正常。
2. 保持主板供电，等示例刷新停止，打开手机 NFC。
3. 点击上传再靠近线圈，先看是否识别 UID。若未识别，记录应用原始提示；不要先用通用写标签软件写入。
4. 验证进度和 DONE；拍照比对 212×104 三色像素。收到错误或手机移开则整图重传。
5. 如需进一步定位，可通过 ST-Link 只读 g_nfc、会话寄存器及 SRAM，对比 RF 触发前后状态。

## 参考文档

- NXP NT3H1101/NT3H1201 数据手册 Rev.3.3 (2015-07-15)，第 8.3、8.3.13、11 节：[厂商文档镜像](https://download.mikroe.com/documents/datasheets/nt3h1101-1201.pdf)。用于理解兼容寄存器，不表示已确认本板料号。
- NXP NT3H2111/NT3H2211 Rev.3.6 (2023-07-21)：[NXP 原文](https://www.nxp.com/docs/en/data-sheet/NT3H2111_2211.pdf)，仅交叉参考。
- [Android NfcA](https://developer.android.com/reference/android/nfc/tech/NfcA)、[NfcAdapter ReaderMode](https://developer.android.com/reference/android/nfc/NfcAdapter)。


## 2026-10-21 手机初测与修订

用户已在手机尝试 v0.1.0：曾提示未识别配套固件、TagLost 和 transceive failed，使用电池供电也复现。电脑端曾捕获 GPIO I²C 轮询中的 HardFault，因此 v0.1.0 不能视为可用上传版本。v0.1.1 切换原引脚 SERCOM1、增加 RF 空闲窗口和镜像/初始化重试；APK 身份握手等待最长 6 秒，错误对话框可复制命令、原始 READ 数据、UID 与手机型号。根因尚不能仅凭这些错误断定；修订后仍需手机重测。
