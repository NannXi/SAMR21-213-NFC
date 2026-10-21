# 测试

## 图像 / 协议纯 Java 核心

用 JDK17 编译 `ImageProcessor.java`、`Wire.java` 与 `CoreTest.java` 到临时目录，再运行 `CoreTest`。验证纯白、黑红极性、四角和中间坐标、三色量化、抖动与标准 CRC32 向量。

## 实板协议

保持已烧录匹配 AXF 的板卡连接，使用安装了 `firmware/Tools/requirements.txt` 的 Python：

```powershell
python tests/hardware_mailbox.py
```

测试会通过 SWD 调试邮箱发送当前帧缓冲内容，经真实 NFC I²C SRAM 接收并触发一次实际屏幕刷新；不是只读测试。不会写 NFC EEPROM，最后会逐块回读比较 896 字节。会覆盖 `docs/hardware_protocol_validation.json`。约 1–2 分钟。

这条路径**没有经过手机 RF 接口**。COMMIT 时屏幕驱动阻塞，主循环调试邮箱不能同时处理，因此 ACK 发布检查由 RAM 标志辅助，DONE 在屏幕完成后用物理 SRAM 读回。不要把脚本通过解释成 Android/手机 NFC 全链路成功。

## v0.1.2 邮箱迁移（2026-10-21）

手机日志已读取完整 EPA1、UID 和尺寸，但第一条 A2 04 写命令失败。旧邮箱选址有误：静态锁 F0 FF，RF 04h..0Fh 只读；动态锁 FF 00 00，RF 10h..8Fh 也被锁定。动态锁在 I2C block38h 字节8..10，不在块首。

新邮箱 RF 90h..9Fh，I2C镜像块24h，目标动态锁byte1 bit0清零。保持全部锁位、用户EEPROM和持久配置不变。Android先只读锁位、校验UID/签名/邮箱版本再写。固件和APK须同时升级至0.1.2。

RF场存在时MCU只读会话寄存器；手机每读完邮箱后静默90ms，MCU在最后块读取事件20ms后访问SRAM。LAST_NDEF_BLOCK=27h，手机presence-check间隔1秒。响应字节39=90h，40=布局修订2。写完一帧后额外READ9Ch发出交接事件。无RF场时50ms轮询，镜像配置每500ms检查恢复。

板端SWD经真实I2C到SRAM整图5512字节、重复包、CRC、刷新和DONE通过。手机0.1.2端到端仍待验证；不能把电脑测试当成手机上传成功。0.1.1的3004失败仍可能涉及仲裁，不能仅凭异常判定手机移开。v0.1.3新增两种抖动算法的强度0/25/50/75/100调色板、0%关闭、100%兼容、渐变差异和范围夹取测试。
