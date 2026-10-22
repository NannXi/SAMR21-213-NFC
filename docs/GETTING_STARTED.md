# 从接线到手机上传

## 准备设备

- 当前目标板：LG Innotek ESL TAG2.13B Rev0.1 / ATSAMR21G18A / WF0213T50CZ16E41e。
- 手机：Android 8.0/API26 以上，有 NFC-A。已收到成功反馈的手机为用户的小米 24129PN74C，诊断报告 Android 17；未据此证明所有同型号/系统组合均兼容。
- 首次下载需 ST-Link 和 Windows 驱动、Keil ARM Compiler 5、Python 3.10+。已运行 v0.1.2 固件时升级 APK 不需要 ST-Link。
- 主板稳定 3.3 V 供电。原厂两节 CR2430 并联；电池当前输出、刷新峰值电流和低功耗尚未测定。

SWD：PA31=SWDIO、PA30=SWCLK，GND 共地，使用已验证的 3.3 V 接法。照片和封装脚号见[原仓库硬件介绍](https://github.com/NannXi/SAMR21-213-EPaper/blob/main/docs/HARDWARE.md)。BAT_IN 没有验证可接 5 V；避免把多个电源输出直接并接。

## 首次构建与烧录

仓库根目录运行：

```powershell
cd firmware
.\build.ps1 -KeilRoot 'D:\12-keil5'
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r Tools/requirements.txt
$env:EPD_PROBE_SERIAL = '你的ST-Link序列号'
.\flash.ps1 -Action flash
```

也可在 Keil5 打开 `firmware/SAMR21_EPD.uvprojx`，选择 ARM Compiler 5。Python 只用于电脑控制 SWD，MCU 固件是 C/ASM。下载依赖匹配的 `Objects/epd_white.axf` 和 BIN，详情见[固件说明](../firmware/README.md)。

工具写复位向量前先备份，下载后验证完整 Flash 与辅助区。不要用整片擦除或自动解锁。上电会刷新一次内置示例，等待约 15 秒结束后再上传。

## 安装与编辑

1. 下载[当前 APK](RELEASES.md)，按系统提示安装并打开手机 NFC。
2. 应用打开后预览固定在顶部；所有编辑项集中在下方“图像调整”。
3. 选择图片；可顺时针旋转、缩放，并拖动预览定位裁切范围。
4. 选择黑白红或黑白，调亮度、对比度、抖动方式和 0–100% 强度。0% 等于关闭，100% 为算法全强度。
5. 点击“上传到墨水屏”，手机 NFC 区域贴近价签背面线圈；身份识别后保持位置，等待上传与刷新完成。

系统文件选择器只授权所选图片。不需要 root/Shizuku，不需要联网。上传期间保持手机前台与主板供电，应用保持亮屏；完成确认前不要移动。

## 失败与恢复

遇到失败时打开“查看诊断”或“复制诊断”，按[故障排查](TROUBLESHOOTING.md)定位。断链后重传整张图片；取消不能撤回已开始的屏幕物理刷新。

`firmware/flash.ps1 -Action recover` 会替换当前固件，写回本板原厂备份，恢复后手机应用将不再识别 NFC 协议。恢复镜像仅对应这块板，包含原标识，不是通用恢复包。

程序不保存上传图片，掉电再上电会显示内置示例。当前 NFC 不能单独为整机刷新供能。
