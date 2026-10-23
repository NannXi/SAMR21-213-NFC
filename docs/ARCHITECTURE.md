# 架构与数据流

## 手机

`MainActivity` 负责选图、212×104 裁切预览、编辑与上传状态。`ImageProcessor` 进行亮度/对比度、三色量化及 Bayer/Floyd–Steinberg 抖动，再打包两个 2756 字节平面。

`Wire` 负责 EPD1 帧、CRC32 与小端字段。`NfcTransfer` 使用 Android NfcA 发送 READ/WRITE，在写入前只读核对锁位、UID、签名、尺寸与布局修订。应用不连接服务器。

手机流程：编辑 → 等待靠近 → 身份识别 → BEGIN → 126 个 DATA 块 → COMMIT ACK → 提示等待刷新并允许移开手机。每个 DATA 最多 44 字节，整图 5512 字节。上传期间禁止修改本次图片快照。

Activity 和上传对话框使用 FLAG_KEEP_SCREEN_ON；完成、失败、取消、后台退出后清理。不申请 WAKE_LOCK，不修改系统休眠超时。当前没有界面状态持久化或后台传输。

## NFC 芯片与 MCU

手机通过 RF90h..9Fh 访问镜像到 SRAM 的 64 字节邮箱；MCU 经 SERCOM1 I²C、0x55、F8h..FBh 访问同一 SRAM。不是把整张图片写入标签 EEPROM，也不是 Android 通用 NDEF 写入。

手机读最后镜像块并静默 90 ms，MCU 观察会话事件后延迟 20 ms 访问 SRAM，以减少 RF/I²C 争用。只修改会话配置，不更改锁位和持久配置。掉电时镜像不保证保留。

`protocol.c` 校验每包 CRC、序号、offset 和 transfer_id，暂存完整图像；整图 CRC 通过后才拷入显示帧缓冲。COMMIT ACK 确认已接受并进入刷新状态后，手机立即关闭 NFC ReaderMode 与常亮；界面提示等待屏幕刷新，用户点 OK 后可拿开手机。手机不再轮询刷新 DONE。`epd.c` 刷新结束仍会在邮箱发布 DONE，留给其他协议客户端读取。

协议没有密码认证或加密。UID、BWR1 与 CRC 是设备匹配及完整性检查，不构成访问控制。当前只有一个活动传输，不支持并行客户端和断点续传。

## 工程与工具

| 部分 | 运行位置 | 作用 |
|---|---|---|
| firmware/App、Drivers、Startup | ATSAMR21 | C/ASM 固件，NFC 协议与显示 |
| android/app | 手机 | Java 原生界面、图像处理、NfcA |
| firmware/Tools | 电脑 | Python 控制 ST-Link、备份、下载与核对 |
| tests/hardware_mailbox.py | 电脑与实板 | SWD→MCU→I²C/SRAM 测试，不经过手机 RF |
| tools/check_repository.py | 电脑/CI | 离线资料、版本与哈希检查 |

完整字段、时序和兼容说明见[协议](PROTOCOL.md)；实际证据与限制见[硬件与验证](HARDWARE_AND_VALIDATION.md)。
