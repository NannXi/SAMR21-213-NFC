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
