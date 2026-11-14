# FIL Team Deathmatch · 1.0.5 更新说明

> 构建产物：`build/libs/filtdm-1.0.5.jar`（357,390 B）  
> 构建时间：2026-11-14 21:44  
> 版本号：`gradle.properties` → `mod_version=1.0.5`

---

## 一、战术地图（新功能，先内嵌在 filtdm，后续可拆独立 mod）

- 新增 `TeamCompetition/tactical/TacticalMapTracker.java`：服务端裁决可见性
  - 距离 ≤ 48 格（可配）
  - 视锥 110°（可配）
  - 方块射线遮挡（眼 → 胸/头，两条线任一条通即算看见）
  - 每 5 tick 同步一次
- 新增 `net/TcpTacticalMapS2CPacket`：每个玩家一份「他能看到的」光点列表
- 新增 `client/TcpTacticalMapHud`：右下角圆形雷达
  - 队友常驻显示（位置 + 朝向短线）
  - 队友死亡显示 ❌
  - 敌人只在「被看见」时出现，朝向线更长（11px）+ 末端亮点
  - 按视角旋转，皮肤 id 哈希成颜色区分
- 新增 `client/RadarTerrain`：**小地图地形层**
  - 2 格 = 1 像素采样最高方块地图色，按高度差做明暗
  - 圆形遮罩烘进纹理，旋转后仍是正圆
  - 每 8 tick 重建，异常全兜底（绝不崩客户端）
- 配置：`radar.enabled / scale / anchor / offsetX / offsetY / snapAboveHotbar / terrain`

## 二、自定义 HUD

### 物品栏（替换原版快捷栏）
- 右下角 **5 个等大格子**（1 步枪 / 2 手枪 / 3 投掷物 / 4 道具 / 5 刀）
- 左上键位号、中间图标（0.75 缩放）、**枪下方两行弹药**（弹匣/容量 + 备弹小字）
- 原版 HOTBAR、经验条、TaCZ 枪械数据面板全部隐藏
- `TcpHotbarLimiter`：选择锁死在 0~4（数字键 6-9 / 滚轮越界拉回第 5 格）
- 面板与槽位都做了圆角 / 渐变 / 阴影

### 血条
- 新增 `TcpStatusHud`：左下角 ❤ 数值 + 进度条（≤30% 变红）+ 🛡 护甲
- **饥饿值不显示**，原版血量/饥饿/护甲/坐骑血全部取消

### 其它 HUD
- 对局结束面板、击杀提示条、Tab 榜单全部改圆角卡片
- **打开任何界面时 HUD 自动隐藏**（修「挡住配件数据面板」）

## 三、修复

- **尸体**：修位移/旋转顺序（原来尸体飘在空中）；尸体存续期间隐藏死亡玩家本体（`TcpCorpsePlayerHider`）；渲染异常改为记日志不再静默
- **捡东西跑位**：`TeamCompetitionPickupGuard` 对局中取消拾取，避免物品塞进 6~9 号格
- **配置崩溃**：`Config.onLoad` 增加 SPEC 判断，修 `Cannot get config value before config is loaded`
- **RadarTerrain 崩溃**：`DynamicTexture` 会关闭旧 `NativeImage`，改为每次新建图 + 全兜底
- **ModernUI 线程崩溃**：视图重建改 `root.post(...)` 投递到 UI 线程
- **TaCZ HUD 遮挡**：按命名空间 `tacz` / `tac` 取消所有 overlay

## 四、设置页

- `Esc → FIL对战设置`（ModernUI 版本，可滚动）
- 分区：小地图（开关 / 大小 / 九宫格位置 / XY 偏移 / 地形开关）
- 改动即时生效，关闭时写回 `config/filtdm-client.toml`
- 面板按屏幕自适应缩放（0.45~1.0），小窗口也不出屏

## 五、已回退

- 配件编辑页、背包页：恢复原版界面（ModernUI 版本类保留在源码里但不再调用）
- `LoadoutConfig.attachmentUi` 默认 `CLASSIC`

---

## 已知事项

- 对局中不能改装配件（服务端 `canEditLoadout()` 拦截），只能在未开局时编辑；界面已给出 🔒 提示
- TaCZ 的 CIB 枪包有 `lrtactical:*` 配方依赖，未装该 mod 时会有配方解析报错（不影响启动）
