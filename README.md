# Monster Conquest

**语言 / Language:** [中文](README.md) | [English](README_EN.md)]

[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/ohhapple/Monster-Conquest/total?color=161616&label=Github%20downloads)](https://github.com/ohhapple/Monster-Conquest/releases)
[![CurseForge](https://img.shields.io/curseforge/dt/1745982?label=CurseForge%20Downloads&color=orange)](https://www.curseforge.com/minecraft/mc-mods/monster-conquest)
[![Modrinth](https://img.shields.io/modrinth/dt/AUNtGJFE?label=Modrinth%20Downloads)](https://modrinth.com/mod/monster-conquest)

Minecraft 1.21.1 / NeoForge 的阵营战争模组：招募士兵、组建军队，用指挥工具和 RTS 视角带兵打仗。

## 环境要求

- Minecraft 1.21.1
- NeoForge 21.1.251 或更高（21.x 系列）
- 客户端与服务端都需要安装

## 玩法概览

- **阵营**：亡灵 / 村民 / 灾厄 / 下界 / 末地 / 怪物 / 中立，以及每位玩家独立的玩家阵营。
  阵营之间可以结盟或敌对（默认关系写在配置里，也可以在游戏内界面里改）；
  同阵营的单位会互相支援，被攻击时整支军队会一起锁敌。
- **招募与军费**：募兵站方块右键打开界面，按等级招募 / 升级士兵，价格按兵种逐个配置；
  列表包含恼鬼、悦灵等固定单等级飞行士兵；
  交易站方块出售弹药、枪械和战术装备。两个方块都能直接读取玩家背包里的物品。
- **指挥**：控制工具（中键选中单位，右键前往 / 攻击，低头右键＝让选中的单位跟随你）、
  R 键指挥轮盘、V 键 RTS 模式（框选部队后右键下达命令，数字键换阵型，Z / C 上下马）。
- **兵种**：近战、弓手、枪手、投掷、魔法师、双形态、飞行。
- **枪械与战术装备**：步枪 / 霰弹枪 / 狙击枪 / 火箭筒与各自弹药（开镜、弹道下坠、炮弹溅射）；
  战术装备栏可放防弹衣、保暖衣、弹药箱和背包（背包也能直接装子弹）。
- **战马染色**：手持染料、潜行右键战马或骑兵坐骑，可用该染料颜色覆盖马匹染色；骑兵下马再上马后仍保留颜色，直到再次染色。
- **怪物征伐自走棋**：完整玩法、流程、弈子、装备、羁绊、经济与状态规则见 [怪物征伐自走棋玩法图鉴](怪物征伐自走棋玩法图鉴.md)。
- **单位 AI**：自研寻路与阵型系统负责阵型排布、行军跟队、迷宫绕行、开门、上下梯子和涉水；
- **教程书**：合成一本教程书，里面按章节讲解指挥、招募、交易等内容。

## 默认按键

均可在原版「选项 → 按键控制」中修改。

| 按键 | 作用 |
| --- | --- |
| N | 打开阵营关系界面 |
| R（按住） | 指挥轮盘 |
| V | 切换 RTS 模式 |
| 1 ~ 5 | RTS：线列 / 盾墙 / 楔形 / 散兵 / 自由站位 |
| Z / C | RTS：上马 / 下马 |
| X | 打开自走棋大厅 |
| H | 打开战术装备栏 |
| B | 打开背包（放在战术装备栏时） |

## 命令

- `/monsterc faction GUI`：打开阵营关系界面。
- `/monsterc spawnpoints（on/off）`：全局开关所有刷怪点刷怪。
- `/monsterc arena shutdown`：仅 OP 可执行，强制关闭自走棋竞技子服并解除当前房间运行态。
- `/monsterc arena history clear <玩家>`：OP 可清除指定玩家的怪物征伐自走棋历史战绩，支持玩家名自动补全和离线玩家。

## 配置

首次启动后会在 `config/monster_conquest/` 下生成模块化配置，招募价格、阵营关系、单位属性、AI 和其他规则可在对应 TOML 中调整。

自走棋配置位于 `arena-common.toml`，其中 `[childServer].enabled` 控制是否启动竞技子服。
需要只对外开放一个 TCP 端口时，可启用内置网关：

```toml
[network]
embeddedGatewayRouting = true
publicHost = "公网IP或域名"

[network.gateway]
gatewayPort = 26565
gatewayMaxConnections = 256

[network.arena]
arenaServerPort = 24565
maxPlayers = 128
```

网关基于 Netty 事件循环转发，`gatewayMaxConnections` 限制同时经过网关的 TCP 连接数，不再按每名玩家创建转发线程。容量应覆盖实际经过网关的主服和竞技服玩家、重连峰值与状态查询。
启用网关后，玩家连接 `公网IP或域名:26565`，防火墙只需放行 `gatewayPort`。未启用时，需要分别开放主服与竞技子服端口。端口映射必须保证客户端看到的公开端口与 `gatewayPort` 相同。
公网、端口映射或跨网段部署时，应把 `publicHost` 填为玩家实际可达的地址，不能使用回环地址或仅内网可达的局域网地址。
竞技转服使用 60 秒可重试租约；主服生成对局密钥和每名玩家的票据随机数，竞技子服完成真实登录后通过 HMAC 回执确认。确认后的租约支持普通登录重连，同时会把返回主服的转服意图交回主服。离线服以主服实际登录产生的离线 UUID 为准，客户端正版 UUID 不会直接覆盖离线身份。

可在 `general-common.toml` 中把 `updateCheckEnabled` 设为 `false` 关闭更新检查。

## 构建

需要 JDK 21。
从 detached HEAD 构建时可用 `-PgitBranch=<分支名>` 指定更新分支；无法确定时分支会记为 `unknown`，
构建照常完成，但客户端禁用更新检查。

```bash
# 打包
./gradlew build          # Windows: gradlew.bat build
# 产物：build/libs/

# 开发运行
./gradlew runClient
./gradlew runServer
```

## 灵感来源与致谢

本模组的创作思路和设计灵感部分来源于作者玩过或看过的游戏、模组、数据包、整合包及其他作品。
这些作品仅作为灵感来源，不代表它们与本模组存在隶属、合作、赞助或背书关系。

## 许可及说明

允许免费游玩、录制视频/直播、免费再分发（包括放入整合包）和修改，但**禁止任何形式的商业用途**。修改版也必须保持非商业。

学习或借鉴本模组时，必须注明来源 Monster Conquest 和原作者 ohhapple。

任何由非原作者进行的对本模组或修改版的分发，必须：

- 清楚注明：作者 ohhapple · https://github.com/ohhapple · 出处 Monster Conquest；
- 显著标注为“再分发版 / 非官方分发版”，无论是否经过修改；
- 显著附带 Monster Conquest 官方下载链接。

修改版还必须显著标注为“修改版 / 非官方版”，不得暗示原作者背书。分发修改版时，还必须免费公开提供完整源代码、在显著位置完整保留本声明原文、附带官方下载链接，并标注修改者信息及修改日期。修改版和衍生作品无论修改多少次，都必须继续遵守本声明，且不得改用允许商业使用的条款。

本模组按“现状”提供，作者不承担因下载、安装或使用本模组产生的任何责任，使用风险自负。本项目不是 Minecraft 官方产品，与 Mojang / Microsoft 无关。

此处说明仅为方便阅读；如仍有歧义或疑惑，请以 [LICENSE](LICENSE) 中的完整声明为准。

作者：ohhapple · https://github.com/ohhapple
