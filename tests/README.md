# 校验与测试

## 离线仓库校验

仓库根目录：

```powershell
python tools/check_repository.py
```

只使用 Python 标准库和 Git，检查受版本管理的 Markdown/HTML 本地链接、JSON、Keil 输入文件、二进制清单和应用版本。不会联系硬件，也不会检查外网链接。

## Java 核心

JDK17，在仓库根目录：

```powershell
.\tests\run-core.ps1 -JavaHome 'C:\Tools\jdk17'
```

覆盖白/黑/红极性、四角与中间坐标、三色量化、抖动强度0/25/50/75/100、0%关闭、100%兼容、边界限制、标准CRC32向量与EPD1帧。产物在忽略的 `android/build/core-tests`。

GitHub Actions执行离线检查与这组Java测试。不使用签名私钥，不编译Keil，不连接实物，不把测试通过当成UI或RF实机验证。

## 实板协议

需已连接并运行匹配固件/AXF的板卡，使用安装了 `firmware/Tools/requirements.txt` 的Python：

```powershell
python tests/hardware_mailbox.py
```

脚本通过SWD请求MCU，把帧缓冲经真实I²C写入NFC SRAM，再读回处理整图、重复包、错误偏移、CRC、COMMIT、刷新和DONE。会改变实物画面，约1–2分钟，覆盖 `docs/hardware_protocol_validation.json`；不要与手机上传同时执行。

不会写NFC EEPROM，结束时逐块核对896字节。此路径没有手机RF。屏幕刷新阻塞主循环，COMMIT ACK发布以RAM标志辅助，DONE在结束后读物理SRAM。报告反映测试脚本的实际覆盖范围。

## 手机验收

v0.1.2已收到用户上传成功反馈。v0.1.4固定预览、旋转图标、常亮以及v0.1.5 ACK后取机提示、多机/移场/掉电条件仍需实机数据，见[验收步骤](../docs/HARDWARE_AND_VALIDATION.md)。

当前校验结果与实验报告分开存放，原始Flash/EEPROM备份和历史报告不因CI重新生成。
