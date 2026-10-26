# AGENTS.md — Lootr 探测之杖（Lootr Prospector）开发说明

> 这是项目级 agent 指令。任何 agent 在修改本仓库前，先完整阅读本文件。
> **当前状态：v0.1.0 已实现并编译。** 代码、资源、Lootr 依赖均已落地；开发服务器启动与配方解析已通过，客户端实机玩法测试待完成。构建产物：`build/libs/lootrprospector-0.1.0.jar`。
> 本文件使用中文书写；代码标识符、路径、命令保持英文。若本文件与代码不一致，以代码为准，并同步修正本文件。

## 1. 结论

**可以完成。** 不需要修改 Lootr，也不需要 Mixin/反射读取 Lootr 的内部数据；Lootr 已经提供了按玩家查询“是否开启过”的公开 API，Forge 1.20.1 也提供了服务端扫描方块实体和客户端描边渲染所需的接口。

### 1.1 可行性对照

| 需求 | 是否可行 | 依据 |
|---|---|---|
| 判断“这个玩家是否开启过这个 Lootr 容器” | 可以 | `IHasOpeners#getOpeners()` 返回 `Set<UUID>`，Lootr 0.7.35.90 中已验证 |
| 区分不同玩家 | 可以 | openers 是按玩家 UUID 记录的，不是全局布尔值 |
| 扫描玩家周围 64 格 | 可以（仅已加载区块） | 遍历 `LevelChunk#getBlockEntities()`，不会加载新区块 |
| 找出箱子、陷阱箱、木桶、潜影盒 | 可以 | 方块标签 `lootr:containers` 覆盖这些方块 |
| 找出 Lootr 箱子矿车 | 可以（可选） | `LootrChestMinecartEntity` 实现 `ILootCart extends IHasOpeners` |
| 服务器重启/区块卸载后仍记得开启状态 | 可以 | `LootrOpeners` 写入方块实体/实体 NBT，加载时读回 |
| 扫描未加载区块里的箱子 | **本方案不做** | 需要强制加载区块或维护全服持久化索引，代价大且不适合一个工具物品 |
| 不安装 Lootr 也能使用 | **不应该** | 本 mod 依赖 Lootr API，必须在 `mods.toml` 声明 mandatory 依赖 |

### 1.2 当前实现状态（v0.1.0）

已落地：

- mod id `lootrprospector`、包名 `com.example.lootrprospector`、物品 `lootrprospector:divining_rod`。
- Lootr 依赖：`libs/lootr-forge-1.20-0.7.35.90.jar`，`flatDir` + `fg.deobf`。
- 服务端扫描、openers 过滤、距离排序、action bar / 聊天栏 / 音效反馈。
- S -> C 位置包、客户端穿墙线框高亮、过期/换维度清理。
- 配置、创造栏、无序合成配方、中英文 lang、16×16 贴图。
- 开发服务器启动通过，Lootr 与 lootrprospector 均被识别，配方解析无 `Unknown item` 错误。

待用户实机验证：

- 实际打开过的 Lootr 容器是否被正确排除。
- 多人环境下“别人开过、自己没开过”的容器是否仍然出现。
- 穿墙线框的视觉效果与手感。
- 低视距/未加载区块提示是否符合预期。

### 1.3 原始开工前提（已满足）

1. 把 Lootr 0.7.35.90 的 jar 放到项目的 `libs/`，并按 §4.4 配置 Gradle 依赖。
2. 确认 mod id、显示名、包名：`lootrprospector` / `Lootr Prospector` / `com.example.lootrprospector`。
3. 确认物品获取方式：紫水晶碎片 + 指南针 + 木棍无序合成。
4. 接受“只扫描已加载区块”的限制；正常视距下 64 格都在加载范围内，但低视距服务器可能缺一部分。
5. 准备一个安装 Lootr 的测试世界（村庄、地牢，或用 `/setblock` 放置 Lootr 箱子）。

### 1.4 明确不承诺的事

- 不读取箱子里的物品，不读取战利品表，不预测战利品。
- 不自动打开/搜刮箱子，不修改 Lootr 的 openers。
- 不绕过 Lootr 的每玩家独立机制。
- 不做全图扫描、跨维度扫描、未加载区块扫描。
- 不做寻路，不画从玩家到箱子的路线。

## 2. 项目概览

| 项 | 值 |
|---|---|
| 游戏版本 | Minecraft **1.20.1** |
| 加载器 | **Forge 47.4.28**（当前 `gradle.properties`） |
| 映射 | official 1.20.1 |
| Java | 17 |
| 构建 | Gradle 8.8 + ForgeGradle 6.0.54 |
| mod id | `lootrprospector`（暂定） |
| mod 名称 | `Lootr Prospector` / 中文“探测之杖” |
| 主包 | `com.example.lootrprospector`（暂定） |
| 核心依赖 | **Lootr 0.7.35.90**，本地 jar：`libs/lootr-forge-1.20-0.7.35.90.jar` |
| 目标 | 一个探测之杖：扫描玩家周围 64 格内、该玩家尚未开启的 Lootr 容器，并给出数量、距离、坐标和高亮 |

> 命名是暂定的。改 mod id / 包名会牵连 `gradle.properties`、`mods.toml`、资源路径、配方和网络通道名；实现开始后不要随意改名。如果用户有偏好，先确认再写代码。

## 3. 需求定义与验收

### 3.1 术语

- **Lootr 容器**：方块标签 `lootr:containers` 中的方块。Lootr 0.7.35.90 的标签内容是：
  - `#lootr:barrels`（`lootr:lootr_barrel`）
  - `#lootr:chests`（`lootr:lootr_chest`、`lootr:lootr_inventory`）
  - `#lootr:trapped_chests`（`lootr:lootr_trapped_chest`）
  - `#lootr:shulkers`（`lootr:lootr_shulker`）
  - 以及可选的 `LootrChestMinecartEntity`（Lootr 箱子矿车）。
- **未开启**：该容器的 `IHasOpeners#getOpeners()` 不包含当前玩家的 UUID。
  - 语义是“这个玩家打开过”，不是“里面一定还有战利品”。
  - 玩家打开后即使什么都没拿，也算已开启，这是本 mod 的预期行为。
- **半径**：以玩家 `blockPosition()` 为球心，3D 欧氏距离 `<= 64` 格；默认只扫描玩家当前维度。
- **所有**：所有**已加载区块**中的匹配项；未加载区块不算在内。扫描不触发区块加载。

### 3.2 MVP 验收标准

- [ ] 物品在创造模式“工具与实用物品”栏出现；可用配方合成；中英文名称和 tooltip 正常。
- [ ] 右键空气或方块触发扫描；扫描在服务端执行；有冷却；客户端不能靠改包绕过。
- [ ] 64 格内所有“本人未开启”的 Lootr 容器都能被找到。
- [ ] 本人已开启的容器不出现；其他玩家开启过的容器仍然出现（多人环境）。
- [ ] 64 格外的容器不出现；其他维度的容器不出现。
- [ ] 结果按距离从近到远排序；action bar 显示总数和最近距离。
- [ ] 可选地在聊天栏列出最近 N 个坐标；点击坐标可复制（可选增强）。
- [ ] 客户端高亮持续约 15 秒，穿墙可见；再次扫描、换维度、过期后清除。
- [ ] 退出重进/区块卸载重载后，已开启状态仍然正确。
- [ ] 专用服务器可启动；没有客户端类加载错误；客户端收到包后能正常渲染。
- [ ] 在容器密集区域单次扫描不造成明显卡顿（目标 < 5 ms，实测记录）。

### 3.3 非目标

- 不做自动搜刮、自动寻路、箱子内容预览。
- 不修改 Lootr 的方块实体数据，不清理/重置 openers。
- 不扫描非 Lootr 的普通箱子（普通箱子没有每玩家状态，且不是本 mod 的目标）。
- 不做全服箱子索引、后台常驻扫描、跨维度探测。
- 不为了扫描而强制加载/生成区块。

## 4. 技术依据（源码结论）

### 4.1 Lootr 的按玩家开启状态

Lootr 0.7.35.90 的 API 提供了：

```java
package noobanidus.mods.lootr.api;

public interface IHasOpeners {
    Set<UUID> getOpeners();
}
```

- `ILootBlockEntity extends IHasOpeners`，所有 Lootr 战利品方块实体都实现它。
- `LootrChestBlockEntity`、`LootrBarrelBlockEntity`、`LootrShulkerBlockEntity` 都实现了 `ILootBlockEntity`；`LootrInventoryBlockEntity` 继承 `LootrChestBlockEntity`。
- `LootrChestMinecartEntity` 实现 `ILootCart`，而 `ILootCart extends IHasOpeners`。
- 方块实体在 `saveAdditional` 中把 openers 写成 NBT 键 `LootrOpeners`（`ListTag`，每个 UUID 是 `IntArrayTag`）；`load` 时读回并填充 `openers`。箱子矿车的 NBT 同样读写 `LootrOpeners`。
- `LootrAPI.shouldDiscardIdAndOpeners` 只在结构模板保存期间被 `MixinStructureTemplate` 临时设为 `true`，保存完立即设回 `false`；正常游戏运行时该标志为 `false`，不会因为这个标志丢弃 openers。

因此服务端可以只做一次只读判断：

```java
if (blockEntity instanceof IHasOpeners openers
        && !openers.getOpeners().contains(player.getUUID())) {
    // 这个玩家还没开启过这个容器
}
```

**不要**去找 `LootrAPI.isLooted(player, pos)` 之类的方法——0.7.35.90 没有这个 API。
**不要**用 `DataStorage.getContainerData(...)` / `ChestData` 来做扫描判断：那是战利品库存的持久化数据，可能创建/加载 SavedData，属于有副作用的读写，不适合高频只读扫描。

### 4.2 扫描已加载区块

- `ServerLevel#getChunkSource()` 返回 `ServerChunkCache`。
- `ServerChunkCache#getChunkNow(int chunkX, int chunkZ)` 只返回**已经加载**的 `LevelChunk`，不会加载/生成区块；未加载时返回 `null`。
- `LevelChunk#getBlockEntities()` 返回 `Map<BlockPos, BlockEntity>`，直接遍历即可，不需要逐格 `getBlockState`。
- 用 `BlockTags.create(ResourceLocation.fromNamespaceAndPath("lootr", "containers"))` 取得标签，再用 `state.is(tag)` 过滤。
- 按精确区块范围计算，64 格半径最多覆盖 9×9 个区块（81 个），每个区块只遍历方块实体表，成本可控。

**禁止**在扫描里调用 `level.getChunk(x, z)` 或 `level.getChunk(x, z, ChunkStatus.FULL, true)`，那会加载/生成区块，可能造成卡顿甚至连锁加载。

### 4.3 不采用的方案

| 方案 | 不采用的原因 |
|---|---|
| 逐格遍历 `BlockPos.betweenClosed` 再查方块 | 129³ ≈ 214 万次检查，太慢 |
| 客户端扫描并上报 | 客户端拿不到完整/可信的 openers，且容易被改包；必须服务端权威 |
| 读 `ChestData` 判断 | 有 SavedData 创建/加载副作用，且语义是库存而不是“开启过” |
| 反射读 `openers` 字段 | 不必要，`IHasOpeners` 是公开 API；反射易随版本失效 |
| 常驻后台扫描所有玩家 | 浪费性能；本物品是“使用时扫描一次” |
| 强制加载 64 格内所有区块 | 一个工具物品不值得改变世界加载状态；低视距下应提示而不是硬加载 |

### 4.4 构建依赖

Lootr 的 jar 是 SRG 名称的生产 jar。ForgeGradle 的 `fg.deobf(files(...))` **不会**对 `files(...)` 依赖做 deobf（会在日志里警告并直接使用混淆版本），所以必须用 `flatDir` 仓库 + 外部模块坐标：

```groovy
repositories {
    flatDir {
        dirs 'libs'
    }
}

dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"

    // 文件名必须是 libs/lootr-forge-1.20-0.7.35.90.jar
    implementation fg.deobf("blank:lootr-forge-1.20:${lootr_version}")
}
```

`gradle.properties` 增加：

```properties
lootr_version=0.7.35.90
lootr_version_range=[0.7.35,)
```

`mods.toml` 增加（放在 Forge/Minecraft 依赖之后）：

```toml
[[dependencies.${mod_id}]]
    modId="lootr"
    mandatory=true
    versionRange="${lootr_version_range}"
    ordering="AFTER"
    side="BOTH"
```

`processResources` 的 `replaceProperties` 要加入 `lootr_version_range`，否则 `mods.toml` 里的占位符不会被替换。

Lootr 带 Mixin。开发环境运行 `runClient` 时，和 Curios 一样需要 refmap 重映射参数，否则可能出现 `No candidates were found` 之类的 Mixin 错误：

```groovy
runs.configureEach {
    property 'mixin.env.remapRefMap', 'true'
    property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
}
```

如果开发环境仍因 Lootr Mixin 启动失败，先用生产实例（Forge 47.4.28 + Lootr 0.7.35.90）验证 mod 逻辑，**不要**为了跑通而删掉 Lootr 依赖或关闭 Mixin 检查。

## 5. 玩法与交互设计

### 5.1 物品

- 注册 id：`lootrprospector:divining_rod`。
- 名称：`Divining Rod` / `探测之杖`。
- 堆叠：1；稀有度：`RARE`。
- 无耐久、无经验消耗；用冷却限制频率。
- 右键空气或方块：服务端扫描；**拿着法杖右键容器不会打开容器，而是扫描**（`useOn` 消费点击），避免误开。
- 潜行不影响行为；旁观者模式不触发。
- 冷却：默认 100 tick（5 秒），由服务端 `ItemCooldowns` 校验并同步给客户端。

### 5.2 获取方式

- 创造模式：加入 `CreativeModeTabs.TOOLS_AND_UTILITIES`。
- 生存模式配方（暂定，无序合成）：

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "minecraft:amethyst_shard" },
    { "item": "minecraft:compass" },
    { "item": "minecraft:stick" }
  ],
  "result": {
    "item": "lootrprospector:divining_rod"
  }
}
```

路径：`src/main/resources/data/lootrprospector/recipes/divining_rod.json`（1.20.1 用 `recipes` 复数目录）。
配方设计意图：指南针代表“寻找”，紫水晶代表“探测水晶”，木棍是杖身。数值/材料可再平衡。

### 5.3 结果反馈

扫描完成后：

1. **Action bar**：
   - 有结果：`探测到 %d 个你尚未开启的 Lootr 容器（半径 %d 格，最近 %d 格）`
   - 无结果：`半径 %d 格内没有你尚未开启的 Lootr 容器`
2. **聊天栏**（可选，默认最近 5 个）：
   - 标题：`探测结果（最近 %d 个）：`
   - 每行：`1. [x, y, z] · 距离 17 格`
   - 坐标行可点击复制 `x y z`（用 `ClickEvent.Action.COPY_TO_CLIPBOARD`，可选增强）。
3. **音效**：
   - 有结果：`SoundEvents.AMETHYST_BLOCK_CHIME`，音调略高。
   - 无结果：`SoundEvents.NOTE_BLOCK_BASS`，低音。
4. **高亮**（见 §5.4）。
5. **提示**：
   - 范围内有未加载区块时，追加“部分区块未加载，结果可能不完整”。
   - 结果超过 `maxHighlights` 时，追加“仅高亮最近 N 个”。

聊天列表默认只列最近 5 个，避免刷屏；`chatListLimit=0` 可完全关闭聊天列表。

### 5.4 客户端高亮

- 服务端把最近 `maxHighlights` 个位置发给扫描者，客户端画金色线框，持续 `highlightTicks`（默认 300 tick = 15 秒）。
- 线框穿墙可见（关闭深度测试），带轻微脉冲透明度。
- 同时只保留最近一次扫描的结果；再次扫描替换旧结果。
- 换维度、退出世界、过期后清除。
- 高亮是“扫描时的快照”：箱子之后被打开/破坏，线框不会实时消失，直到过期或再次扫描；这是可接受的简化。
- 如果 `enableHighlight=false`，服务端不发高亮包，只给文字/音效结果。

## 6. 配置

配置类型：`ModConfig.Type.COMMON`，文件 `config/lootrprospector-common.toml`。服务端读取这些值；客户端 tooltip 读取本地值，仅作展示，实际以服务端为准。

| 键 | 默认值 | 范围 | 含义 |
|---|---:|---|---|
| `scanRadius` | 64 | 8–128 | 扫描球半径（格） |
| `cooldownTicks` | 100 | 20–1200 | 使用冷却（tick） |
| `highlightTicks` | 300 | 20–1200 | 客户端高亮持续时间（tick） |
| `maxHighlights` | 128 | 16–512 | 单次最多高亮/发包的位置数 |
| `chatListLimit` | 5 | 0–20 | 聊天栏坐标列表条数；0 = 关闭 |
| `scanMinecarts` | true | bool | 是否扫描 Lootr 箱子矿车 |
| `enableHighlight` | true | bool | 是否允许客户端线框高亮 |
| `playSound` | true | bool | 是否播放扫描反馈音效 |
| `showUnloadedHint` | true | bool | 有未加载区块时是否提示 |
| `debugLogScan` | false | bool | 是否记录扫描耗时和结果数量 |

配置值在 `ModConfigEvent.Loading` / `Reloading` 时刷新到静态缓存；不要在代码里硬编码 64、100、300 这些数字。

## 7. 代码结构

```
src/main/java/com/example/lootrprospector/
├── LootrProspector.java              模组入口：注册物品、配置、网络、创造栏
├── Config.java                       COMMON 配置 + 缓存
├── item/
│   ├── ModItems.java                 DeferredRegister<Item>
│   └── DiviningRodItem.java          use/useOn、冷却、tooltip、调用扫描
├── scan/
│   ├── ContainerScanner.java         服务端扫描（方块 + 可选矿车）
│   └── ScanResult.java               record：总数、位置、未加载区块数、是否截断
├── network/
│   ├── ModNetwork.java               SimpleChannel 注册与发送
│   └── ScanResultPacket.java         S -> C：位置列表、维度、半径、高亮时长
└── client/
    ├── ClientProspectingState.java   客户端位置列表、维度、过期时间
    ├── ClientEvents.java             tick / 退出时清理
    └── ProspectingRenderer.java      RenderLevelStageEvent 线框渲染
```

资源：

```
src/main/resources/
├── META-INF/mods.toml                         修改 mod 元数据 + lootr 依赖
├── assets/lootrprospector/lang/en_us.json     英文名称、tooltip、消息
├── assets/lootrprospector/lang/zh_cn.json     中文名称、tooltip、消息
├── assets/lootrprospector/models/item/divining_rod.json
├── assets/lootrprospector/textures/item/divining_rod.png  16×16 占位贴图
└── data/lootrprospector/recipes/divining_rod.json
```

`pack.mcmeta` 的 `pack_format` 保持 15（1.20.1）。

### 7.1 类职责

- `LootrProspector`
  - `MODID = "lootrprospector"`.
  - `DeferredRegister<Item> ITEMS`。
  - 构造器里 `ITEMS.register(modEventBus)`、`context.registerConfig(COMMON, Config.SPEC)`。
  - `FMLCommonSetupEvent` 里 `enqueueWork(ModNetwork::register)`。
  - `BuildCreativeModeTabContentsEvent` 里加入工具栏。
- `ModItems`
  - 只注册 `DIVINING_ROD`，属性 `stacksTo(1).rarity(Rarity.RARE)`。
- `DiviningRodItem`
  - `use`（右键空气）和 `useOn`（右键方块）都走同一个 `tryScan`。
  - 检查旁观者、冷却、服务端侧；客户端只返回 swing 结果，不扫描。
  - 服务端：`ContainerScanner.scan(player, Config.scanRadius)` → 反馈 → 发 `ScanResultPacket` → `player.getCooldowns().addCooldown(this, Config.cooldownTicks)`。
  - `appendHoverText` 显示半径、冷却和“只显示你尚未开启的容器”。
- `ContainerScanner`
  - 纯服务端工具类；输入 `ServerPlayer` + 半径，输出 `ScanResult`。
  - 只读：不修改 openers、不加载区块、不打开容器。
- `ScanResult`
  - `record ScanResult(int radius, int total, int unloadedChunks, boolean truncated, List<BlockPos> positions)`。
  - `positions` 是最近 `maxHighlights` 个的不可变副本。
- `ScanResultPacket`
  - `record ScanResultPacket(List<BlockPos> positions, ResourceLocation dimension, int radius, int highlightTicks, boolean truncated)`。
  - `encode` / `decode` 用 `FriendlyByteBuf.writeBlockPos` / `readBlockPos`。
- `ClientProspectingState`
  - 保存位置、维度、过期 game time；`accept` 时校验当前客户端维度。
  - 只被客户端类引用。
- `ProspectingRenderer`
  - `RenderLevelStageEvent` + `Stage.AFTER_TRANSLUCENT_BLOCKS`。
  - 穿墙线框；渲染后恢复深度测试/深度写入状态。
- `ClientEvents`
  - `ClientTickEvent` 清理过期状态；`ClientPlayerNetworkEvent.LoggingOut` 清空。

## 8. 扫描算法

### 8.1 伪代码

```java
public static ScanResult scan(ServerPlayer player, int radius) {
    ServerLevel level = player.serverLevel();
    BlockPos center = player.blockPosition();
    long radiusSq = (long) radius * radius;

    List<BlockPos> found = new ArrayList<>();
    int unloadedChunks = 0;

    int minChunkX = (center.getX() - radius) >> 4;
    int maxChunkX = (center.getX() + radius) >> 4;
    int minChunkZ = (center.getZ() - radius) >> 4;
    int maxChunkZ = (center.getZ() + radius) >> 4;

    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                unloadedChunks++;
                continue;
            }
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be.isRemoved()) continue;
                if (!(be instanceof IHasOpeners openers)) continue;
                if (!be.getBlockState().is(LOOTR_CONTAINERS)) continue;
                if (openers.getOpeners().contains(player.getUUID())) continue;

                BlockPos pos = be.getBlockPos();
                if (center.distSqr(pos) > radiusSq) continue;
                found.add(pos.immutable());
            }
        }
    }

    if (Config.scanMinecarts) {
        AABB area = player.getBoundingBox().inflate(radius);
        for (Entity entity : level.getEntities(player, area, e -> e instanceof ILootCart)) {
            ILootCart cart = (ILootCart) entity;
            if (cart.getOpeners().contains(player.getUUID())) continue;
            BlockPos pos = entity.blockPosition();
            if (center.distSqr(pos) > radiusSq) continue;
            found.add(pos.immutable());
        }
    }

    found.sort(Comparator.comparingDouble(pos -> center.distSqr(pos)));
    int total = found.size();
    boolean truncated = total > Config.maxHighlights;
    List<BlockPos> positions = truncated
            ? List.copyOf(found.subList(0, Config.maxHighlights))
            : List.copyOf(found);

    return new ScanResult(radius, total, unloadedChunks, truncated, positions);
}
```

### 8.2 实现细节

- `LOOTR_CONTAINERS` 用 `BlockTags.create(ResourceLocation.fromNamespaceAndPath("lootr", "containers"))` 创建一次，静态缓存。
- 区块范围用 `(center - radius) >> 4` 到 `(center + radius) >> 4`，半径 64 时最多 9×9=81 个区块；负坐标的算术右移等价于向下取整，可以放心用。不要用 `(radius >> 4) + 1` 这种保守写法，它会多扫一圈无用区块。
- 距离用 `center.distSqr(pos)`（方块角到方块角）；排序和结果展示用同一套距离，避免“显示 63 格但实际差一点”的不一致。
- `positions` 用 `BlockPos.immutable()`，避免引用方块实体的可变位置对象。
- 单次扫描的候选数量加一个硬上限（例如 4096），超过就停止收集并标记截断；正常 64 格内不会达到。
- 扫描在主线程执行；**不要**为了“性能”把方块实体访问丢到异步线程，Minecraft 的 Level/Chunk 不是线程安全的。
- 只有服务端能决定“是否开启过”；客户端只接收位置，不参与判断。

### 8.3 为什么只扫已加载区块

64 格通常小于服务器视距，但低视距（例如 2 个区块）时，半径边缘的区块可能没加载。此时：

- 不调用 `getChunk` 强行加载；
- 统计 `unloadedChunks`，通过 action bar 提示“结果可能不完整”；
- 玩家可以靠近后再扫一次。

这是有意的取舍：一个工具物品不应该改变世界加载状态，也不应该因为扫描造成服务器卡顿。

## 9. 网络与客户端渲染

### 9.1 通道

- 通道名：`lootrprospector:main`，协议版本 `"1"`。
- 只注册一个 S → C 包：`ScanResultPacket`。
- 没有 C → S 包；扫描由服务端 item use 触发，客户端无法伪造结果。

注册模式（Forge 常规 SimpleChannel 用法）：

```java
CHANNEL.messageBuilder(ScanResultPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(ScanResultPacket::encode)
        .decoder(ScanResultPacket::decode)
        .consumerMainThread(ScanResultPacket::handle)
        .add();
```

发送：

```java
ModNetwork.CHANNEL.send(
        PacketDistributor.PLAYER.with(() -> player),
        new ScanResultPacket(result.positions(), level.dimension().location(),
                result.radius(), Config.highlightTicks, result.truncated()));
```

### 9.2 客户端隔离

- `ScanResultPacket` 在服务端也会被加载；handler 里必须用 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` 延迟到客户端类，不能直接 `new ClientProspectingState()` 或直接调用客户端渲染类。
- `ClientProspectingState`、`ClientEvents`、`ProspectingRenderer` 放在 `client` 包，并用 `@Mod.EventBusSubscriber(value = Dist.CLIENT, ...)` 或 `DistExecutor` 保护。
- 专用服务器启动时不能出现 `NoClassDefFoundError: net/minecraft/client/...`。

### 9.3 线框渲染

- 事件：`RenderLevelStageEvent`，阶段 `AFTER_TRANSLUCENT_BLOCKS`。
- 相机平移：

```java
PoseStack pose = event.getPoseStack();
Vec3 camera = event.getCamera().getPosition();
pose.pushPose();
pose.translate(-camera.x, -camera.y, -camera.z);
```

- 画线：使用立即模式（`Tesselator` + `BufferBuilder`），不要用 `RenderType.lines()` 的 batch。原因：`RenderType.lines()` 的 `CompositeState` 默认带 `LEQUAL_DEPTH_TEST`，`endBatch()` 里的 `setupRenderState()` 会重新启用深度测试，覆盖掉 flush 前的 `disableDepthTest()`，结果就是线框被墙挡住。

```java
RenderSystem.disableDepthTest();
RenderSystem.depthMask(false);
RenderSystem.enableBlend();
RenderSystem.defaultBlendFunc();
RenderSystem.disableCull();
RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
RenderSystem.lineWidth(2.0F);

Tesselator tesselator = Tesselator.getInstance();
BufferBuilder buffer = tesselator.getBuilder();
buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
for (BlockPos pos : positions) {
    LevelRenderer.renderLineBox(pose, buffer,
            new AABB(pos).inflate(0.01),
            1.0F, 0.82F, 0.20F, alpha);
}
tesselator.end();

RenderSystem.lineWidth(1.0F);
RenderSystem.enableCull();
RenderSystem.depthMask(true);
RenderSystem.enableDepthTest();
RenderSystem.disableBlend();
```

- 透明度脉冲：`alpha = 0.35F + 0.35F * (0.5F + 0.5F * Mth.sin(gameTime * 0.2F))`。
- 如果线框不显示，优先检查：
  1. `Tesselator.end()` 之前是否已经 `disableDepthTest()`，以及 `RenderType`/shader 是否又把深度测试打开；
  2. `LevelRenderer.renderLineBox` 的 `PoseStack` 是否已经平移到相机空间；
  3. `RenderSystem` 状态是否在异常时也能恢复（用 try/finally 或成对调用）。
- 只在 `positions` 非空、`Minecraft.getInstance().level != null`、客户端维度等于包里的维度时绘制。

## 10. 实施顺序

> 实现进度：Phase 0–3 已完成，Phase 4 的构建、开发服务器启动、开发客户端启动预检已完成；下面的复选框保留为验收清单，未勾选不代表未实现，实际游戏内扫描/高亮仍待用户测试。

### Phase 0 — 工程骨架（先让空项目编译）

- [ ] 复制 Lootr jar 到 `libs/`（文件名 `lootr-forge-1.20-0.7.35.90.jar`）。
- [ ] 修改 `gradle.properties`：mod id/name/group/version、`lootr_version`、`lootr_version_range`。
- [ ] 修改 `build.gradle`：`flatDir`、`fg.deobf("blank:lootr-forge-1.20:...")`、Mixin refmap 参数、`processResources` 新占位符。
- [ ] 修改 `mods.toml`：mod 元数据 + `lootr` mandatory 依赖。
- [ ] 删除/替换 MDK 示例内容：`ExampleMod.java`、示例 `Config.java`、示例方块/物品/食物/创造栏。
- [ ] 创建 `LootrProspector.java`、`ModItems.java`、空的 `Config.java`。
- [ ] 运行 `.\gradlew.bat compileJava --console=plain`，确认 Lootr API 能找到、能编译。

**Phase 0 完成标志**：`build` 成功，`runClient` 能进主菜单，Mods 列表能看到 `Lootr Prospector` 和 `Lootr`。

### Phase 1 — 服务端 MVP（无高亮）

- [ ] `DiviningRodItem`：右键扫描、冷却、旁观者检查。
- [ ] `ContainerScanner`：方块 + 可选矿车；标签过滤；openers 判断；距离排序；未加载区块统计。
- [ ] Action bar / 聊天栏 / 音效反馈。
- [ ] 创造栏 + 基础中英文 lang + tooltip。
- [ ] 在测试世界放置若干 Lootr 箱子，验证“未开启/已开启/其他玩家开启/超过 64 格”四种情况。

**Phase 1 完成标志**：不装客户端渲染也能准确报出数量、距离和坐标；多人测试通过。

### Phase 2 — 高亮

- [ ] `ScanResultPacket` + `ModNetwork`。
- [ ] `ClientProspectingState` + `ClientEvents`。
- [ ] `ProspectingRenderer` 线框、穿墙、脉冲、过期清理。
- [ ] 验证专用服务器无客户端类加载。

**Phase 2 完成标志**：扫描后 15 秒内能看到穿墙金色线框；再次扫描替换；换维度清除；多人各自只看到自己的结果。

### Phase 3 — 打磨

- [ ] 配方 JSON、创造栏排序、贴图（16×16 占位图）。
- [ ] 配置项全部接入，`config` 重载生效。
- [ ] 未加载区块/截断提示。
- [ ] 可选：点击聊天坐标复制。
- [ ] 可选：`/lootrprospector scan` 调试命令（仅 OP，方便测试）。
- [ ] 更新 `AGENTS.md` 的“当前状态”和实现清单。

**Phase 3 完成标志**：生存模式可合成；配置可调；文档与代码一致。

### Phase 4 — 验收

- [ ] 按 §11 测试清单逐项验证。
- [ ] 记录一次密集区域的扫描耗时。
- [ ] 构建 `build/libs/lootrprospector-0.1.0.jar`。
- [ ] 在生产实例（Forge 47.4.28 + Lootr 0.7.35.90）做一次冒烟测试。

## 11. 测试清单

### 11.1 基础

- [ ] `.\gradlew.bat compileJava --console=plain` 通过。
- [ ] `.\gradlew.bat build --console=plain` 通过，产物在 `build/libs/`。
- [ ] `runClient` 能进入主菜单，Mods 列表显示本 mod 和 Lootr。
- [ ] 创造模式工具栏能拿到探测之杖。
- [ ] 生存模式按配方能合成。
- [ ] 中英文 lang 无 missing key；tooltip 正常。

### 11.2 功能

- [ ] 周围没有 Lootr 容器：提示“没有”，有低音反馈，不报错。
- [ ] 放 3 个未开启的 Lootr 箱子：全部找到，数量正确，按距离排序。
- [ ] 自己打开其中一个再扫描：该箱子不再出现，另外两个仍出现。
- [ ] 第二个玩家打开另一个箱子：第一个玩家再扫描，该箱子仍出现（因为对第一个玩家来说未开启）。
- [ ] 在 64 格边界外放箱子：不出现。
- [ ] 在另一个维度放箱子：不出现。
- [ ] 木桶、陷阱箱、潜影盒、`lootr_inventory`：按标签被找到。
- [ ] 箱子矿车（`scanMinecarts=true`）：被找到；设为 false 后不出现。
- [ ] 连续右键：冷却生效，不会重复发包/刷屏。
- [ ] 旁观者模式：不触发。
- [ ] 右键容器本身：不打开容器，触发扫描（`useOn` 消费点击）。

### 11.3 持久化

- [ ] 打开一个箱子 → 退出世界 → 重进 → 扫描：仍视为已开启。
- [ ] 打开一个箱子 → 走远让区块卸载 → 回来 → 扫描：仍视为已开启。
- [ ] 服务器重启后：仍视为已开启。

### 11.4 客户端

- [ ] 高亮穿墙可见，颜色/透明度正常。
- [ ] 15 秒后自动消失。
- [ ] 再次扫描替换旧高亮，不叠加。
- [ ] 换维度后旧高亮清除。
- [ ] 退出世界后状态清空。
- [ ] `enableHighlight=false`：只显示文字/音效，无客户端报错。

### 11.5 兼容/边界

- [ ] 专用服务器 `runServer` 可启动，无客户端类加载。
- [ ] 不安装 Lootr 时，Forge 给出缺失依赖提示，而不是 `NoClassDefFoundError`。
- [ ] 低视距/未加载区块：不崩溃，提示“结果可能不完整”。
- [ ] 双箱子/相邻箱子：不重复计数（如发现重复，记录并修正）。
- [ ] 密集结构（村庄/地牢）：扫描耗时记录，目标 < 5 ms。
- [ ] 其他模组添加的 `lootr:containers` 方块：被正确识别（标签驱动）。

## 12. 已知限制与边界

1. **只扫描已加载区块。** 这是本方案最重要的限制；未加载区块不会被加载，也不会被找到。
2. **openers 的语义是“打开过”**，不是“里面还有没有战利品”。玩家打开后没拿东西也算已开启。
3. **Lootr 的 refresh/decay 功能可能影响语义。** 如果服务器启用了自动 refresh，`openers` 可能仍保留“曾经开过”的记录；此时“未开启”与“还能再拿”可能不一致。默认按字面“未开启”判断；如果用户要“还有战利品”语义，另开需求，先验证 Lootr 的 refresh 行为。
4. **`lootr:containers` 包含 `lootr:lootr_inventory`。** 如果只想找传统箱子，后续可以加过滤配置。
5. **矿车高亮是快照。** 矿车移动后线框不会跟随；文字坐标是扫描时的位置。
6. **不扫描普通箱子。** 普通箱子没有 Lootr 的每玩家状态，不在目标内。
7. **不保证 100% 覆盖。** 区块未加载、世界边界、标签被数据包修改、Lootr 版本差异都可能影响结果。
8. **Lootr 版本范围是 `[0.7.35,)`。** 当前只在 0.7.35.90 上验证；升级 Lootr 后要重新跑测试，必要时收窄范围。
9. **服务端/客户端配置可能不一致。** 实际半径、冷却、高亮时长以服务端发包/校验为准；客户端 tooltip 只是展示。
10. **高亮是客户端效果。** 专用服务器不加载渲染类；如果客户端没装本 mod，收不到包，也看不到高亮。

## 13. Agent 工作约定

1. **先读本文件，再读相关源码。** 不要凭记忆猜 Forge/Lootr API；优先用 `javap`、源码或 jar 字节码确认。
2. **服务端权威。** 扫描、openers 判断、冷却、结果数量都在服务端完成；客户端只负责显示。
3. **客户端隔离。** 所有 `net.minecraft.client.*` 引用放在 `client` 包，用 `Dist.CLIENT` / `DistExecutor` 保护；改完必须跑 `runServer` 验证。
4. **只读 Lootr。** 不修改 `getOpeners()` 返回的集合，不写 Lootr 的 NBT，不调用会创建 SavedData 的 `ChestData` 接口。
5. **不加载区块。** 扫描只用 `getChunkNow`；不要用 `getChunk` / `ChunkStatus` 强制加载。
6. **不硬编码数值。** 半径、冷却、高亮时长、上限都走 `Config`。
7. **不改 mod id/包名。** 当前是 `lootrprospector` / `com.example.lootrprospector`；要改先问用户。
8. **不把 Lootr jar 打进 mod jar。** `libs/` 只用于编译和开发运行；发布 jar 不应包含 Lootr 类。
9. **修改后必须编译。** 至少 `.\gradlew.bat compileJava --console=plain`；涉及资源/注册/网络再跑 `build`。
10. **更新文档。** 玩法、默认值、目录结构、命令变化后，同步更新本文件；实现完成后把“当前状态”改成实际状态。
11. **性能有疑问先测量。** 在密集区域记录扫描耗时；不要为了“看起来快”引入异步线程访问世界。
12. **测试多人。** 这个 mod 的核心语义是“按玩家独立”，单人测试只能覆盖一半；至少用两个玩家验证一次。
13. **DeferredRegister 的条目类必须初始化。** 本 mod 在 `LootrProspector` 构造器里调用 `ModItems.init()` 强制类初始化；如果只声明 `ITEMS` 而不触发 `ModItems` 类初始化，物品不会注册，配方会报 `Unknown item 'lootrprospector:divining_rod'`（开发服务器上已经踩过这个坑）。
14. **每次改动都要 commit。** 完成一个可工作的改动后，立刻 `git add` + `git commit`，不要攒大量未提交改动；commit 前先 `git status` 确认没有把 `run/`、`build/`、`libs/*.jar` 等本地/第三方文件加进去。
15. **push 必须等用户指令。** 默认只 commit，不执行 `git push`；只有用户明确说“push / 上传 / 同步到 GitHub”时才推送。若用户没有说，最多在回复里提醒“本地已 commit，尚未 push”。

## 14. 后续可选

- 只扫描传统箱子的配置（排除 `lootr_inventory`、木桶、潜影盒）。
- 客户端高亮颜色/线宽/时长独立配置。
- 矿车高亮跟随实体（需要传实体 ID，而不是固定坐标）。
- 扫描结果 HUD 列表（距离、方向箭头）。
- 点击聊天坐标复制/传送到附近（后者需要权限，不建议默认）。
- `/lootrprospector scan` 管理命令，便于服主排查。
- 对 Lootr refresh 语义做专门适配（需要先确认 Lootr 的行为）。
- 成就/进度（首次探测、一次找到 N 个箱子）。
- 正式贴图/模型/音效。
