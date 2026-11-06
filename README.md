# FIL Team Deathmatch —— FPSMatch 联动版构建文件（给向导生成的项目用）

适用：IDEA「Minecraft Development」向导生成的项目（MC 1.20.1 / Forge 47.4.23 / mod_id=filtdm / group=com.github.pyzlwmn）

## 为什么要换

向导给的是 **ForgeGradle** 骨架（`net.minecraftforge.gradle`）。FPSMatch 官方（作者的 BlockOffensive / GradleTemplate）用的是 **ModDevGradle 的 legacyforge 变体**，这个插件提供 `modImplementation`。
- 「`modImplementation` 是 Fabric 专有」只对老的 ForgeGradle 6 成立 → 那才要 `implementation fg.deobf(...)`
- 用 legacyforge + composite build 编译 FPSMatch 源码，dev 环境不加载生产 jar → 顺手绕开 1.2.5 时代的 mixin `@Shadow m_91277_` 崩溃

## 替换步骤

1. **建项目**：向导照你截图那样建（路径别填成 `E:\FilTdm` 又取名 FilTdm，会套两层）。Mixins 勾不勾都行。

2. **盖掉两个文件**（整个替换）：
   - `build.gradle` ← 本目录的 build.gradle
   - `settings.gradle` ← 本目录的 settings.gradle

3. **mods.toml**：把你的 `src/main/resources/META-INF/mods.toml` 用本目录的同名文件覆盖（或只把里面 `fpsmatch` / `ldlib2` / `modernui` 三个依赖块抄进去）——**不加这些，runClient 会报 Missing mandatory dependencies**。

4. **拿到 FPSMatch 源码**（composite build 必需，目录名必须叫 `FPSMatch`）：
   ```
   cd 项目根目录
   git submodule add https://github.com/PhasetransCrystal/FPSMatch.git FPSMatch
   git submodule update --init --recursive
   ```
   没 git：`git clone https://github.com/PhasetransCrystal/FPSMatch.git FPSMatch`

5. **（可选）Gradle 版本**：legacyforge 2.0.140 建议 Gradle ≥ 8.8。项目里跑：
   ```
   gradlew wrapper --gradle-version 8.14.5
   ```

6. **Sync + 运行**：
   - IDEA → Gradle 面板刷新（会下 moddev 插件、映射、Parchment）
   - `Tasks > runs > runClient`（或命令行 `gradlew.bat runClient`）
   - 首次很慢（10 分钟起），跑起来后进单人世界 `/fpsm help` 验证

## 勾了 Mixins 的话

放开 `build.gradle` 里被注释的 `mixin { ... }` 两行，并确认你的 `src/main/resources/filtdm.mixins.json` 里 `refmap` 写的是 `filtdm.refmap.json`。
自己写 mixin 时：`@Shadow` / `@Inject` 目标一律用 **Mojang 可读名**（如 `startUseItem`），**别写 SRG 名**（`m_91277_`）。

## 备选：不拉源码，直接吃生产 jar

删掉 `settings.gradle` 里的 `includeBuild('FPSMatch')` 整段，`build.gradle` 里换成：
```gradle
modImplementation 'curse.maven:fpsmatch-1331710:7109977'   // 1.2.5
// modImplementation files('libs/fpsmatch-1.20.1-1.3.0-forge-snapshot.jar')
```
⚠️ 这时 dev 跑 client 有 mixin 崩溃风险（1.2.5 需要 dev 修补 jar：https://ffsmcserver.top/fpsmatch-dev/fpsmatch-1.2.5-dev.jar）
