# Android 图片编辑 / NFC 上传

原生 Java，最小 Android 8/API26，目标 API35。无第三方库；通过系统图片选择器获取单张图片，应用不需要联网或存储权限。开启 NFC ReaderMode 只在用户点击上传后进行，跳过 NDEF 解析以免把 SRAM 邮箱当 NDEF。

## Windows 构建

需要 JDK17、Android SDK Platform35 和 Build Tools35。不需要安装 Android Studio 或 Gradle，`build.ps1` 调用官方 javac / d8 / aapt2 / zipalign / apksigner。

```powershell
.\build.ps1 -JavaHome 'C:\Tools\jdk17' -AndroidSdk 'C:\Android\sdk' -BuildToolsVersion '35.0.0'
```

输出 `../releases/epaper-nfc-v0.1.1-debug.apk`。默认开发签名密钥创建在忽略的 `android/build/android-debug.keystore`；可用 `-KeyStore` 指定已有开发密钥。密钥与 SDK 不上传 Git。不同密钥构建的 APK 需要卸载旧版后安装，或沿用原密钥。这里的密码 `android` 是标准开发测试配置，不用于正式生产发布。

应用是初版，已由用户在手机安装 0.1.0 并触发 NFC，但上传失败；0.1.1 编译通过，新增握手等待、原始 RF 字节和可复制诊断，**仍待用户重新验证端到端传图**。方向通过已实测的控制器映射实现；照片裁切支持拖动、缩放和手动 90° 旋转。抖动支持无 / 4×4 Bayer / Floyd–Steinberg，输出始终只有黑白红实际像素。透明区域先叠到白底。

上传前验证固件 BWR1 签名、212×104、5512 字节、UID 与 NFC Tag 一致。不是通用 NDEF 写入器，不匹配的标签不写。传输、刷新均显示不同阶段，中断后重新上传整图；已提交刷新无法用取消按钮撤回。

0.1.1 的失败弹窗支持复制诊断；不会上传日志到网络。若复制后要公开，请留意日志中含标签 UID。
