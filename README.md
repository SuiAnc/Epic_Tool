# Epic DumpDex

一个用于研究和还原特定 Epic/VMP 保护 APK 资源的 Android 工具。

本项目主要针对 APK 中经过 Epic 保护的 **DEX、Assets、AXML、resources.arsc、AndroidManifest.xml 以及 SO 内部配置**进行解析和还原。

> **项目定位：** Android APK 保护机制研究、逆向分析、样本分析与学习用途。

---

## ✨ 功能

### DEX 还原

支持从 APK 指定目录中寻找 `.epic` 文件，并根据 SO 中提取的配置进行解密。

支持：

- XOR
- RC4
- 自动按照文件名排序
- 自动转换为 `classes.dex`、`classes2.dex`、`classes3.dex` 等

示例：

```text
assets/
├── xxx.epic
├── yyy.epic
└── zzz.epic
```

还原后：

```text
DEX/
├── classes.dex
├── classes2.dex
└── classes3.dex
```

---

## 📦 Assets 资源还原

支持根据配置中的 `asset_rename_map` 对 Assets 进行还原。

例如：

```json
{
    "asset_protection_method": 0,
    "asset_protection_xor_key": 123,
    "asset_rename_map": {
        "assets/config.dat": "assets/EP_001",
        "assets/data.bin": "assets/EP_002"
    }
}
```

程序会：

1. 根据映射找到原始文件
2. 使用配置中的 XOR / RC4 Key 解密
3. 使用逻辑路径重新写入 ZIP

---

## 📝 AXML 还原

支持处理：

```text
res/**/*.xml
```

中的 Epic 加密 XML。

程序通过：

```text
Epic
```

魔数判断是否为 Epic 加密 XML。

检测到后根据配置执行：

- XOR 解密
- RC4 解密

并将文件头恢复为标准 Android Binary XML：

```text
03 00 08 00
```

---

## 🗂 resources.arsc / AndroidManifest.xml

支持处理资源字符串保护。

程序会遍历：

```text
resources.arsc
AndroidManifest.xml
```

中的字符串池。

对于以：

```text
EP_
```

开头的字符串：

```text
EP_xxxxxxxxxxxxxxxxxxxx
```

程序会：

1. 去掉 `EP_`
2. Base64 解码
3. 使用 XOR / RC4 解密
4. 转换为 UTF-8
5. 替换原始字符串

同时生成映射日志：

```text
EP_xxxxxxxx --> app_name
```

---

# 🔐 SO 配置解析

项目会扫描 APK 中的 `.so` 文件，并寻找：

```text
.ArmEpic
```

节区。

结构大致为：

```text
.ArmEpic
│
├── 16 bytes
│   └── RC4 Key
│
└── encrypted JSON
    └── RC4 encrypted
```

程序提取 `.ArmEpic` 后：

```text
前 16 字节
    ↓
RC4 Key

剩余数据
    ↓
RC4 解密
    ↓
JSON
```

最终得到 Epic 全局配置。

例如：

```json
{
    "so_name": "libEPIC.so",
    "dex_protection_method": 1,
    "dex_protection_rc4_key": "xxxxxxxx",
    "asset_protection_method": 0,
    "asset_protection_xor_key": 123,
    "axml_protection_method": 1,
    "axml_protection_rc4_key": "xxxxxxxx"
}
```

---

# 🔧 支持的保护类型

| 模块 | XOR | RC4 | 配置字段 |
|---|:---:|:---:|---|
| DEX | ✅ | ✅ | `dex_protection_method` |
| Assets | ✅ | ✅ | `asset_protection_method` |
| AXML | ✅ | ✅ | `axml_protection_method` |
| resources.arsc | ✅ | ✅ | `resource_string_protection_method` |
| AndroidManifest.xml | ✅ | ✅ | `resource_string_protection_method` |
| `.ArmEpic` 配置 | ❌ | ✅ | 内置 RC4 |

其中：

```text
0 = XOR
1 = RC4
```

---

# 🔏 certificate_md5

如果 Epic 配置中存在：

```json
{
    "certificate_md5": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
```

程序会检测到签名 MD5 校验。

用户可以选择是否修改签名 MD5。

修改过程：

```text
.ArmEpic
   ↓
提取 Key
   ↓
RC4 解密 JSON
   ↓
定位 certificate_md5
   ↓
替换 MD5
   ↓
重新 RC4 加密
   ↓
保持 .ArmEpic 节区长度
   ↓
写回 SO
```

为了避免改变 ELF 节区大小，程序会保持原始 `.ArmEpic` 节区长度。

---

# 🧩 项目结构

```text
app/
└── src/
    └── main/
        ├── java/
        │   └── epic/
        │       └── dumpdex/
        │           └── suianc/
        │               ├── MainActivity.java
        │               ├── Decryptor.java
        │               ├── DexProcessor.java
        │               ├── AssetProcessor.java
        │               ├── AxmlProcessor.java
        │               ├── ArscProcessor.java
        │               ├── ElfConfigParser.java
        │               ├── SigBypassModifier.java
        │               └── FileUtils.java
        │
        ├── assets/
        │   ├── 111.png
        │   ├── 222.png
        │   ├── 333.png
        │   └── fonts/
        │       └── JetBrainsMono-Regular.ttf
        │
        └── res/
            └── ...
```

---

# ⚙️ 核心处理流程

```text
                    APK
                     │
                     ▼
              ┌─────────────┐
              │ 读取 APK ZIP │
              └──────┬──────┘
                     │
                     ▼
             扫描所有 .so 文件
                     │
                     ▼
             查找 .ArmEpic 节区
                     │
                     ▼
             提取 16 字节 RC4 Key
                     │
                     ▼
              RC4 解密配置 JSON
                     │
                     ▼
              ┌──────┴──────┐
              │   Epic 配置  │
              └──────┬──────┘
                     │
       ┌─────────────┼──────────────┐
       ▼             ▼              ▼
      DEX          Assets          XML
       │             │              │
       ▼             ▼              ▼
    XOR/RC4       XOR/RC4        XOR/RC4
       │             │              │
       └─────────────┼──────────────┘
                     │
                     ▼
              resources.arsc
                     │
                     ▼
              AndroidManifest.xml
                     │
                     ▼
                还原结果
```

---

# 📁 输出结构

程序处理完成后会生成类似：

```text
原APK名称-20260820_123456-[还原].zip
```

ZIP 内部可能包含：

```text
说明.txt

DEX/
├── classes.dex
├── classes2.dex
└── classes3.dex

Assets.zip

ResXML.zip

resources.arsc

AndroidManifest.xml

lib/
└── arm64-v8a/
    └── libEPIC.so
```

实际内容取决于 APK 中启用的保护模块。

---

# 📋 处理日志

程序会实时显示处理日志，例如：

```text
[12:30:01] 正在分析 SO 配置文件...
[12:30:02] 共发现 3 个 .so 文件
[12:30:02] 成功从 lib/arm64-v8a/libEPIC.so 读取配置
[12:30:03] Dex保护: assets/a.epic -> classes.dex
[12:30:03] Dex保护: assets/b.epic -> classes2.dex
[12:30:04] Assest资源解密: assets/xxx -> assets/config.dat
[12:30:05] 修复 ARSC 字符串池...
[12:30:05] 解密 res/ 布局 XML...
[12:30:06] 处理完成！
```

---

# 🛠️ 技术实现

主要使用：

- Java
- Android SDK
- AndroidX
- Material Components
- `java.util.zip`
- `org.json`
- ReAndroid ARSC/XML 库

核心实现包括：

### ELF Section Parser

通过 ELF Header 和 Section Header Table 定位：

```text
.ArmEpic
```

支持：

```text
ELF32
ELF64
```

并按照小端格式读取 ELF 结构。

### RC4

项目内部实现 RC4：

```java
public static byte[] rc4Crypt(byte[] data, Object keyObj)
```

包含：

```text
KSA
+
PRGA
```

### XOR

支持整数 XOR Key，例如：

```text
123
```

或者：

```text
0x7F
```

### APK ZIP 处理

通过：

```java
ZipFile
ZipEntry
ZipOutputStream
```

完成 APK 内部资源读取和还原结果打包。

---

# 📱 使用方法

1. 打开应用
2. 选择需要分析的 APK
3. 程序自动扫描 SO
4. 从 `.ArmEpic` 中读取配置
5. 根据配置执行对应还原操作
6. 如果检测到 `certificate_md5`，可以选择是否修改
7. 等待处理完成
8. 在原 APK 所在目录找到：

```text
xxx-时间-[还原].zip
```

---

# ⚠️ 注意事项

### 1. 本项目不是通用 APK 脱壳工具

项目针对的是特定 Epic/VMP 保护方案。

不同版本、不同保护配置以及不同修改方式可能导致：

```text
无法找到 .ArmEpic
无法解析配置
解密结果错误
DEX 无法加载
XML 无法解析
```

### 2. 解密成功不代表 APK 可以直接运行

本项目主要负责：

```text
资源还原
+
配置修改
+
文件导出
```

并不保证还原后的文件可以直接重新打包、签名并正常运行。

如果 APK 存在：

- 签名校验
- 完整性校验
- SO 自校验
- DEX 完整性校验
- 运行时动态解密
- 反调试
- 反篡改

仍然可能需要进一步分析。

### 3. 建议在测试样本上使用

请不要直接对生产环境 APK 进行修改。

建议保留原始 APK，并使用副本进行分析。

---

# 👨‍💻 项目作者

**SuiAnc / 岁安辞**

GitHub：

> https://github.com/SuiAnc/

---

# 🤝 致谢

感谢以下人员对项目 UI、技术研究及测试过程中提供的帮助：

- Mt论坛 @Shiray
- Mt论坛 @寒风科技
- Mt论坛 @zskj2736472509

排名不分前后。

---

# 📜 开源说明

本项目仅用于：

- Android 安全研究
- APK 保护机制研究
- 逆向工程学习
- 软件保护技术分析
- 自有 APK 调试与测试

使用本项目时，请确保你拥有目标 APK 的合法分析、修改或测试权限。

**请勿将本项目用于未经授权的软件破解、恶意修改或其他违法用途。**

---

## ⭐ Star

如果这个项目对你的 Android 逆向研究或学习有帮助，欢迎点一个 Star ⭐

如果发现问题，也欢迎提交 Issue。
