# 登录重定向插件
> 仅支持velocity

类似wbc中的插件:https://www.spigotmc.org/resources/premiumconnector-bungeecord.20957/
根据玩家的登录类型例如:`在线玩家`，`离线玩家`，以及使用`geysermc的BE玩家`分别将他们重定向到在线服务器和离线服务器。**BE玩家被归入在线玩家**
你可以在离线服务器子服中安装auth插件为离线玩家设置认证，而在线玩家会被重定向到在线服务器 不需要进行认证流程。
> 详细的测试流程可以查看`测试项目.md`
> 详细的插件流程可以查看`流程图.vsdx`
**注意此插件并未经过详细的测试流程，还是bate版本**

## 使用方法:
下载jar文件放置velocity的plugins文件夹中，重启服务器释放插件目录，包括config.yml和player.json文件，根据注释需求修改config.yml

