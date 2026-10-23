# 硬件与验证状态

整理日期：2026-10-22。对象是当前 LG Innotek ESL TAG2.13B Rev0.1 / REBE-TZ21B，不能把单块板结论直接推广到所有 2.13 寸价签。

## 本板资源

| 项目 | 已知 |
|---|---|
| MCU | ATSAMR21G18A，256 KiB Flash / 32 KiB SRAM；本固件 OSC8M 8 MHz |
| 屏幕 | WF0213T50CZ16E41e，原生 104×212、横向 212×104、黑白红；驱动 IC 未定 |
| SWD | PA31 SWDIO、PA30 SWCLK、GND 与已验证 3.3 V 供电 |
| NFC I²C | PA16 SDA / PA17 SCL，SERCOM1，七位地址 0x55 |
| PA15 | 沿用原初始化设高，具体电气角色未测绘 |
| UID | 04 16 9A 6A 1E 44 80，仅本板标识 |
| CC | E1 10 6D 00，声明 872 字节数据区，不是总容量证明 |
| SRAM | I²C 块 F8h..FBh，64 字节 |
| 当前 RF 邮箱 | 90h..9Fh，镜像起始块 24h，最后块 27h，布局修订 2 |
| 静态 / 动态锁 | 本板 F0 FF / FF 00 00；目标页锁位只读确认未锁，不更改任何锁位 |
| NFC 精确料号 | 未确定；按已观测的 NTAG I²C 兼容寄存器与行为实现 |

完整照片、电源、屏幕端口、无线、UART、ADC 与原厂分析见[硬件资料仓库](https://github.com/NannXi/SAMR21-213-EPaper/blob/main/docs/HARDWARE.md)。

早期探测 JSON 中的会话快照对应当时配置；不代表当前镜像地址。当前以 v0.1.2 的 24h/27h 为准。

## 证据分层

| 证据 | 已证实范围 | 不能替代的验证 |
|---|---|---|
| [nfc_probe_validation.json](nfc_probe_validation.json) | UID、只读配置、SRAM 图样读写与恢复 | 手机完整传输 |
| [firmware_flash_validation.json](firmware_flash_validation.json) | 完整 Flash 回读、未写区和辅助区一致、刷新结束 | 手机 RF 仲裁、长期运行 |
| [hardware_protocol_validation.json](hardware_protocol_validation.json) | SWD 请求 MCU 经真实 I²C/SRAM 整图接收、重复包、偏移/CRC 拒收、刷新/DONE、EEPROM 比对 | 手机 RF 端到端 |
| [stability_validation.json](stability_validation.json) | 对应记录时长内的板端检查 | 长期稳定性、整机续航 |
| [android_build_validation.json](android_build_validation.json) | v0.1.5 APK构建、签名、Java核心测试与静态流程检查 | 实机新 UI、常亮及兼容性 |
| 用户 2026-10-21 反馈“可以了” | v0.1.2 手机上传成功；之前失败报告手机为 Android 17 / Xiaomi 24129PN74C | 多手机、多次成功率、吞吐、掉电/离场恢复 |

手机成功反馈记录自本次协作，未另附可供重放的成功 RF 抓包或 ADB 日志。v0.1.3/v0.1.4 没有修改 NFC 时序。v0.1.5 在 COMMIT ACK（状态2，已进入刷新）后立即关闭手机 NFC 轮询和常亮，不再读 DONE；这是用户根据上一版实物观察提出的流程修改，尚待新APK验收。不能把构建成功写成实机验收通过。

COMMIT 时显示驱动阻塞，调试邮箱同在主循环，测试脚本不能在刷新中同时通过该邮箱访问。历史 COMMIT ACK 的发布由 RAM 标志辅助核对，DONE 在刷新完成后通过真实 I²C/SRAM 读回。

## EEPROM、供电与恢复

[896 字节 EEPROM 备份](nfc_eeprom_before.bin)覆盖 I²C 块 0..55，SHA-256：

```text
aa2fadb624c42d9080efaa951cda56df7e70629cf3d91661896b21ebaacf4b55
```

它是现场读取范围，不是 EEPROM 总容量的证明。固件 I²C 写接口只写 SRAM 和会话寄存器，不提供 EEPROM/锁位写入。SWD 原厂 Flash 恢复也不会恢复独立 NFC EEPROM；当前没有发布自动 EEPROM 恢复操作。

本方案要求主板 VCC 供电。掉电后镜像地址可能回到 EEPROM，手机若继续写同一 RF 地址，存在影响原 NDEF 的风险。保持稳定供电，不在传图中切断主板电源；本板 VCC 跌落恢复和 RF 单独供能刷新尚未验证。

## 2026-10-22 仓库复核

[复核记录](repository_review_20261022.json)：隔离重编固件BIN与已实测v0.1.2逐字节一致；APK构建、签名与Java核心测试通过。只修改文档和电脑构建/启动工具，未刷板、未重复手机上传，也未替换既有Release附件。

## 已知限制

- 屏幕黑字折角偏淡、红底白字发泛；全白清屏后仍存在，原因未定。
- 显示驱动 IC、NFC 精确料号、完整升压电路与 FPC 针序未知。
- 无掉电保存、低功耗策略、NFC 单独供能刷新或断点续传。
- CPU 常醒与 NVM 诊断配置用于当前稳定性排查，不代表已优化电池运行。
- 无多手机兼容性、RF 吞吐、长时间成功率与移场/掉电恢复数据。
- Keil 内置 FLM/在线调试未配置；已验证下载路径是外部 ST-Link 工具。

## 接下来如何验收

1. 在 v0.1.5 确认固定预览、选图、旋转、拖动裁切、图像参数和提交后的取机提示。
2. 将系统休眠设短，验证等待贴近、传图与刷新期间常亮，结束/取消/后台后释放。
3. 稳定供电下重复上传并记录成功率和时间；再单独测试移场恢复，避免同时改变多项条件。
4. 使用电压/电流测量确认供电边界后，再安排掉电、低功耗与能量采集实验。

故障日志收集见[故障排查](TROUBLESHOOTING.md)。历史修订见[更新记录](CHANGELOG.md)。

## 参考资料

- [NXP NT3H1101/NT3H1201 数据手册镜像](https://download.mikroe.com/documents/datasheets/nt3h1101-1201.pdf)：早期兼容寄存器参考，不代表确认本板料号。
- [NXP NT3H2111/NT3H2211](https://www.nxp.com/docs/en/data-sheet/NT3H2111_2211.pdf)：交叉参考。
- [Android NfcA](https://developer.android.com/reference/android/nfc/tech/NfcA) 与 [ReaderMode](https://developer.android.com/reference/android/nfc/NfcAdapter)：接口参考。
