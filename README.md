# 口袋桌宠 · Android

项目位置：`E:\petapk`。四个安装包位于 `dist` 目录。

[下载 v1.0.0 四个版本](https://github.com/VyskoyeNebo/DesktopPet-Android/releases/tag/v1.0.0)

| 版本 | 最低系统 / API | 下载 |
| --- | --- | --- |
| Android 8 版 | Android 8.0 / API 26 | [Pet_Android8_v1.0.0.apk](https://github.com/VyskoyeNebo/DesktopPet-Android/releases/download/v1.0.0/Pet_Android8_v1.0.0.apk) |
| Android 10 版 | Android 10 / API 29 | [Pet_Android10_v1.0.0.apk](https://github.com/VyskoyeNebo/DesktopPet-Android/releases/download/v1.0.0/Pet_Android10_v1.0.0.apk) |
| Android 11 版 | Android 11 / API 30 | [Pet_Android11_v1.0.0.apk](https://github.com/VyskoyeNebo/DesktopPet-Android/releases/download/v1.0.0/Pet_Android11_v1.0.0.apk) |
| Android 12 版 | Android 12 / API 31，包含 12L | [Pet_Android12_v1.0.0.apk](https://github.com/VyskoyeNebo/DesktopPet-Android/releases/download/v1.0.0/Pet_Android12_v1.0.0.apk) |

四个版本共用源码、应用 ID 和签名，通过构建变体分别设置最低系统版本与应用名称，可相互覆盖安装。每个 APK 都通用于 ARM / ARM64 / x86 / x86_64，无需再分处理器位数。请按手机系统选择对应安装包；Android 9 可用 Android 8 版。

这是使用 Android 原生 Java 和系统控件制作的手机版，复用 Windows 桌宠的五形态素材。APK 不含 Python、Qt、浏览器或 Spine 运行引擎，没有联网权限。

## 安装与启动

1. 将对应系统的 APK 复制到手机并安装。发布页附有 SHA-256 校验文件。
2. 打开“桌宠 · 五形态”，点击“悬浮窗授权”，在系统设置中允许显示在其他应用上层。
3. Android 13 及以上可允许通知，便于使用通知栏控制；拒绝通知仍可从应用内恢复操作。
4. 返回应用，选择形态、动作和大小，点击“启动桌宠”。桌宠可显示在普通应用上方。

## 功能

- 五种形态：Q版蕾米、蕾米埃尔、星见雅、普罗米娅、仪玄。
- Q版默认长边 **192 dp**；其余形态默认 **384 dp**。保留 96、128、192、256、384、512 六档，每种形态独立记忆大小。
- Q版有 9 个动作选项，含静态姿势；蕾米埃尔有两种循环，其他形态各有一种循环。全部共 14 个动作、718 帧，12 fps，源帧最长边 1024 像素。
- **没有程序附加的整体上下浮动**，素材自身角色动作保留。
- 拖动调整位置；长按切换固定位置；轻点打开控制页。拖动、隐藏或熄屏锁屏时暂停动画，恢复时保持进度。
- 通知栏提供显示/隐藏、恢复触控、关闭；应用内“找回桌宠并恢复交互”也可找回位置和取消穿透。
- 点击穿透会让触摸落到下方应用。Android 12 及以上会将悬浮层略微变淡，以遵守系统对不可信悬浮窗的触摸规则；不会申请无障碍权限绕过限制。
- 屏幕旋转或窗口大于可用区域时，等比缩小并校正位置。形态、动作、固定、大小和位置保存在手机本地。

手机没有 Windows 托盘和 Ctrl+Alt+P 全局热键，相应恢复入口改为通知栏与应用控制页。系统界面或禁止悬浮窗的应用可能隐藏悬浮层；后台行为也受手机厂商的省电策略影响。程序不申请开机自启，不在后台反复拉起服务。

## 资源与内存

动画通过单独解码线程按需读取透明 WebP。每个播放器缓存最多 24 帧，并限制约 4 MiB 像素内存（至少保留一帧）；切换动作、尺寸时清理旧缓存。不将全部帧解码到内存，不持有唤醒锁。APK 体积主要来自高清素材，实际体积与文件校验见 `dist`。

`app/src/main/assets/forms` 保存从五个原始 .pet 包解出的帧和说明；`pet.png` 是异常时的静态备用图。不需要在手机上额外下载素材。

## 构建

本次构建：JDK 17、Gradle 8.13、Android Gradle Plugin 8.11.1、Android SDK Platform 35、Build Tools 35.0.0。最低 API 分别为 26、29、30、31，目标 API 均为 35。使用对应官方工具，不依赖其他项目的源码。

Android Studio 可直接打开此目录。命令行可运行：

```powershell
.\build.ps1 -JavaHome 'C:\Program Files\Java\jdk-17' -SdkPath 'C:\Android\Sdk'
```

本机 `local.properties` 已配置可用 SDK。若换电脑，用自己的 SDK 路径执行上述命令。构建脚本会为四个变体分别运行 6 项单元检查和 Release Lint，生成四个签名 APK 和 SHA-256。`local.properties`、构建缓存和本机 SDK 不上传。

首次构建会创建 `.signing/pet-release.jks` 和本地签名配置，以后沿用同一密钥。请自行备份此目录以便后续覆盖升级；源码 ZIP 不包含该私钥、密码、SDK、构建缓存或个人路径配置。

## 验证

四个版本分别验证最低 SDK、APK v2 签名、统一证书、包名与权限、全部 718 帧资源一致性、Release Lint 和 6 项单元检查（共 24 项执行）。详细记录见 [verification.json](verification.json)。

Android 12L / API 32 的 x86_64 模拟环境已安装 Android 12 版，实际验证：控制页启动、单一前台悬浮服务、五形态切换、拖动、长按固定、穿透标志及安全透明度、控制页恢复、通知栏隐藏/显示和关闭后的服务清理。整体窗口在播放时位置固定。

Android 8、10、11 目前完成构建与静态检查，未在这些系统实机运行；Android 12 的不同厂商真机及更高系统也需复核。

建议手机复核：悬浮授权、通知权限、五形态切换、拖动固定、穿透后恢复、横竖屏边界、熄屏唤醒与后台省电策略。若系统停止后台服务，请重新打开应用启动；关闭服务会清除点击穿透状态。

技术依据：[Android 前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)、[悬浮窗与点击穿透规则](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE)、[AGP 8.11 工具链要求](https://developer.android.com/build/releases/agp-8-11-0-release-notes)。
