# ZRemote

**Z.ai Code 的手机遥控端 / Remote console for Z.ai Code**

在电脑上的 Z.ai Code 生成一个二维码，用手机扫一扫，即可在手机上给电脑的 AI 发消息——躺沙发上也能遥控写代码。

## 工作原理

Z.ai Code 会在网页控制台展示一个二维码，ZRemote 扫码后在内置 WebView 中打开该控制台页面，手机与电脑之间通过 WebSocket 长连接通信。

- **扫码连接**：自定义扫码页（微信风格取景框），支持手电筒；也可以手动粘贴控制台链接
- **会话保活**：前台服务 + WakeLock，息屏后长连接不易断开，通知栏可随时断开保活
- **记忆重连**：记住上一次的连接地址，重开 App 直接重连
- **黑 / 白主题**：默认深色，界面风格与 ZCode 一致

## 系统要求

- Android 8.0（API 26）及以上
- 电脑端：支持二维码远程控制台的 Z.ai Code

## 构建

用 Android Studio（Hedgehog 及以上）打开项目直接构建，或命令行：

```bash
./gradlew assembleRelease
```

> 仓库未包含 Gradle Wrapper，首次构建可用本机 Gradle 7.5+ 生成：`gradle wrapper`
>
> release 包默认使用 debug 签名，方便直接安装体验；正式发布请替换为自己的 keystore。

产物位于 `app/build/outputs/apk/release/zremote-release.apk`。

## 使用

1. 电脑上打开 Z.ai Code 的远程控制台，显示出二维码
2. 手机安装 ZRemote，从屏幕右侧向左滑出设置面板，点「扫码连接」
3. 扫描二维码，连接成功后即可在手机上给电脑的 AI 发消息
4. 建议打开「保活」开关，息屏也能保持连接

## License

[MIT](LICENSE)
