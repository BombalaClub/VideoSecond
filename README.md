# 先答一题 · VideoSecond

娃上三年级了，经常刷小红书停不下来。
想到他过几天要考试，于是做了这个。
打开小红书的时候，会先弹出一道奥数题，答对了才能进入小红书。刷够半小时的时候，又会再弹出一道题，答对了才能继续看。
和他商量了一下，他说挺好。
还真的认真做了题，做对了很兴奋。
（题确实不简单。哈哈）

## 你会被第一题拦住吗？

这是第一次开启守护后会遇到的题。图上有 14 枚黑色钉子，挑 4 枚做顶点，一共能围出多少个不同的正方形？

<img src="app/src/main/res/drawable-nodpi/ihc_q043.png" width="300" alt="方格纸上的四排黑点，从上到下分别有 3、4、4、3 枚，第二排向右多一枚，第三排向左多一枚。">

<details>
<summary>给我一点提示</summary>

先只看横平竖直的。边长为 1 格的有几个？边长为 2 格的呢？只有四个角都钉着黑点才算。

</details>

<details>
<summary>我数完了，揭晓答案</summary>

**14 个。** 横平竖直的有 8 个（6 小、2 大），另外 6 个是斜着的。如果你刚好数出 8 个，试着把手机歪一下，再看那些黑点。

</details>

## 装到手机之后

1. 在 Android 8.0 或以上的手机上安装下方步骤生成的 APK，打开「先答一题」。首页的 **试做一道题** 可以直接玩，没装小红书也行。
2. 想正式启用时，点 **开启小红书守护 → 同意，去设置**，在系统的「无障碍」里开启「先答一题」。回到首页会看到 **守护已开启**。
3. 回桌面，点平时的小红书图标。试着故意答错一次，看看提示，再答一次。也可以点 **回到桌面**，把这道题和小红书都先放一放。

如果 Android 13 或以上版本提示「受限制的设置」，可以在系统的「应用 → 先答一题 → 更多」里找到「允许受限制的设置」，然后再开启服务。各品牌菜单名称略有差异。[Android 帮助](https://support.google.com/android/answer/12623953?hl=zh-Hans)

## 半小时怎么算？

| 你在手机上做什么 | 「先答一题」会做什么 |
| --- | --- |
| 在小红书里从一篇笔记跳到另一篇 | 接着计时，放心翻页 |
| 停在一篇长笔记上，一直没滑屏 | 阅读时间照算，满 30 分钟出下一题 |
| 完全拉下通知面板，再收起来 | 暂停计时，收起后接着算 |
| 回桌面、切走应用，或者锁屏 | 本次使用结束，下次进小红书时重新出题 |

离线题库收录了 100 道三年级奥数题，其中 25 道有图，可点开放大。答错会保留原题，每题有两级提示。首页试做和正式守护各自抽题，一轮之内不会重复。

## 手机上能读到什么？

守护服务通过 Android 无障碍接口判断哪个应用在前台，到时间就把题目盖在小红书上。它只用窗口类型、焦点和应用标识来做判断。笔记正文、聊天和触摸记录都不采集，应用也没有申请联网权限。答题输入和倒计时只留在内存里，已出题号保存在本机。

目前适配主屏幕上全屏运行的小红书安卓客户端（`com.xingin.xhs`）。系统在小红书进入前台后才通知守护服务，因此偶尔会先闪过启动画面。分身、分屏、画中画和网页版没有适配。守护可以在系统设置里关闭。

## 自己编译

准备 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。可以用 Android Studio 打开仓库根目录，也可以在项目根目录运行：

```sh
./gradlew assembleDebug
```

调试包在 `app/build/outputs/apk/debug/app-debug.apk`。想在自家手机上长期覆盖升级，也可以用项目自带的脚本生成家庭签名包：

```sh
python3 scripts/build-apk.py
```

安装包在 `dist/VideoSecond.apk`。首次构建会在本机生成签名密钥，请自行备份 `.local-signing/`，以后用同一把密钥才能覆盖升级。

<details>
<summary>开发者入口与回归检查</summary>

- [GateService.java](app/src/main/java/cn/videosecond/gate/GateService.java) 负责识别前台窗口、显示和收起题目。
- [SessionGate.java](app/src/main/java/cn/videosecond/gate/SessionGate.java) 管理进入、离开、暂停和 30 分钟到期。
- [题库](app/src/main/assets/) 按题号保存题干、答案和两级提示。[OlympiadAnswers.java](app/src/main/java/cn/videosecond/gate/OlympiadAnswers.java) 处理多空答案和等价解。

以下命令检查题库、判题和计时逻辑。生成家庭签名包后，可在第一条命令末尾追加 `--apk dist/VideoSecond.apk` 核对打包内容。

```sh
python3 checks/check_olympiad.py
check_dir="$(mktemp -d)"
src=app/src/main/java/cn/videosecond/gate
javac -encoding UTF-8 -d "$check_dir" "$src/OlympiadAnswers.java" "$src/SessionGate.java" checks/*.java
java -cp "$check_dir" cn.videosecond.gate.OlympiadAnswersCheck
java -cp "$check_dir" cn.videosecond.gate.SessionGateCheck
```

</details>
