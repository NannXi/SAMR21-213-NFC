# Android 图片编辑 / NFC 上传

原生 Java，最小 Android 8/API26，目标 API35。无第三方库；通过系统图片选择器获取单张图片，应用不需要联网或存储权限。开启 NFC ReaderMode 只在用户点击上传后进行，跳过 NDEF 解析以免把 SRAM 邮箱当 NDEF。

## Windows 构建

需要 JDK17、Android SDK Platform35 和 Build Tools35。不需要安装 Android Studio 或 Gradle，`build.ps1` 调用官方 javac / d8 / aapt2 / zipalign / apksigner。

```powershell
.\build.ps1 -JavaHome 'C:\Tools\jdk17' -AndroidSdk 'C:\Android\sdk' -BuildToolsVersion '35.0.0'
```

输出 `../releases/epaper-nfc-v0.1.4-debug.apk`。默认开发签名密钥创建在忽略的 `android/build/android-debug.keystore`；可用 `-KeyStore` 指定已有开发密钥。密钥与 SDK 不上传 Git。不同密钥构建的 APK 需要卸载旧版后安装，或沿用原密钥。这里的密码 `android` 是标准开发测试配置，不用于正式生产发布。

用户已确认 v0.1.2 手机端到端上传成功。v0.1.3 更新界面、图标、抖动强度与上传常亮，继续兼容 v0.1.2 板端固件；新界面与常亮等待手机验收。方向通过已实测的控制器映射实现；照片裁切支持拖动、缩放和手动 90° 旋转。抖动支持无 / 4×4 Bayer / Floyd–Steinberg，输出始终只有黑白红实际像素。透明区域先叠到白底。

上传前验证固件 BWR1 签名、212×104、5512 字节、UID 与 NFC Tag 一致。不是通用 NDEF 写入器，不匹配的标签不写。传输、刷新均显示不同阶段，中断后重新上传整图；已提交刷新无法用取消按钮撤回。

0.1.1 的失败弹窗支持复制诊断；不会上传日志到网络。若复制后要公开，请留意日志中含标签 UID。

## v0.1.2 邮箱迁移（2026-10-21）

手机日志已读取完整 EPA1、UID 和尺寸，但第一条 A2 04 写命令失败。旧邮箱选址有误：静态锁 F0 FF，RF 04h..0Fh 只读；动态锁 FF 00 00，RF 10h..8Fh 也被锁定。动态锁在 I2C block38h 字节8..10，不在块首。

新邮箱 RF 90h..9Fh，I2C镜像块24h，目标动态锁byte1 bit0清零。保持全部锁位、用户EEPROM和持久配置不变。Android先只读锁位、校验UID/签名/邮箱版本再写。固件和APK须同时升级至0.1.2。

RF场存在时MCU只读会话寄存器；手机每读完邮箱后静默90ms，MCU在最后块读取事件20ms后访问SRAM。LAST_NDEF_BLOCK=27h，手机presence-check间隔1秒。响应字节39=90h，40=布局修订2。写完一帧后额外READ9Ch发出交接事件。无RF场时50ms轮询，镜像配置每500ms检查恢复。

板端SWD经真实I2C到SRAM整图5512字节、重复包、CRC、刷新和DONE通过。用户随后确认手机0.1.2端到端上传成功。0.1.1的3004失败仍可能涉及仲裁，不能仅凭异常判定手机移开。
## v0.1.3 墨屏（2026-10-21）

用户确认v0.1.2手机上传成功。本版只升级Android应用，继续使用v0.1.2固件，无需重新烧录。

- 应用名改为墨屏；浅色卡片、预览与图像调整分组、分段选择、底部固定上传按钮。
- 抖动强度0–100%：有序调整阈值幅度，扩散调整误差传播。0%等于无抖动，100%保持旧版结果。关闭抖动时禁用滑条。
- 从等待连接、传图至屏幕刷新全程保持亮屏，成功、失败、取消和后台退出均清理；不修改系统休眠设置。
- 原创黑白红像素SVG位于android/artwork/icon.svg，已转五档PNG和Adaptive Icon打入APK。
- versionCode4，沿用同一开发签名，可覆盖安装；NFC时序和板端固件未改。构建与算法测试通过，新界面和常亮尚无手机实测。图标再生成：安装cairosvg与Cairo运行库后执行 python tools/render_icon.py。预生成资源已提交，正常APK构建无需Python。

## v0.1.4（2026-10-21）

- 删除首页图片编辑、调整画面贴近上传及预览标题，保留墨屏品牌。
- 画面预览独立为固定圆角矩形窗，位于调整滚动区之外；滚动下方参数时预览持续可见。
- 选择图片、顺时针旋转90°、图片信息与裁切缩放全部移入图像调整卡片。
- 重绘顺时针旋转图标，使圆弧与箭头连接，方向与旋转操作一致。
- 应用versionCode5，沿用签名；图像算法、NFC时序和v0.1.2固件不变。构建及核心测试结果见docs/android_build_validation.json；新布局仍待手机验收。
