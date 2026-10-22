# 修改与反馈

## 先核对证据

先阅读根README和当前状态文档。报告结论时注明依据：源码推导、寄存器读取、工具验证、用户观察或仪器测量；不要把构建/CI通过写成实机通过。

- 修改硬件文档时附原始照片、测量条件或对应证据文件。
- 保留历史实验、原厂备份和第三方文件，补充说明其时间范围，不改写历史结果。
- 使用自己的探针序列号与备份；固件/AXF符号必须匹配。
- 不上传SDK/JDK/Keil安装、私钥、凭据、虚拟环境和新生成的大量运行备份。
- 原创与第三方授权边界见[NOTICE](NOTICE.md)，本次整理不追加统一开源授权。

## 离线检查

在仓库根目录，Python3.10+与Git：

```powershell
python tools/check_repository.py
git diff --check
```

校验覆盖受版本管理文件中的JSON、本地文档/图片链接、Keil输入和文件清单；不访问外网、不连接实物、不编译商业Keil。

修改并复核文件后，先暂存本次准备提交的文件，再更新清单：

```powershell
git add <本次修改的文件>
python tools/check_repository.py --update-manifests
git add <更新后的清单>
python tools/check_repository.py
git diff --cached --stat
```

显示仓库清单为 `repository_manifest.json` 和工程内 `manifest.json`，自校验文件不列入自身。NFC仓库使用 `artifact_manifest.json` 记录受版本管理的BIN/AXF/PNG；HEX/SVG是文本格式，不跨行尾做字节清单。校验基于本地工作树；更新清单前先检查差异，不将不明的备份变化直接接受为新基线。

## 构建与实机

- Keil构建命令和工具版本见工程README；商业编译器不放入CI。
- NFC仓库另外运行 `tests/run-core.ps1`，构建APK并验证版本和签名。
- 实板脚本和烧录会改变设备状态，手工执行并保存新报告；不放到云端CI。
- 文档整理不替换既有Release附件。二进制变更需新版本、匹配源码和SHA256SUMS。

## 反馈内容

附仓库/提交、硬件型号、供电、编译器/应用/固件版本、复现步骤、实际与预期结果及诊断。NFC失败还需手机/Android版本与失败命令；屏幕问题尽量保持相同拍摄条件。

公开日志前检查UID、设备配置、探针序列号及私人路径。使用仓库Issue模板即可，避免把多项无关条件同时改动后再判断原因。
