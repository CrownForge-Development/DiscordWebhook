# DiscordWebhook

A flexible Java/Minecraft library and plugin for sending and deleting messages via Discord webhooks.
Can be used as a standalone plugin or as a dependency in your own Java projects!

---

## Features

- Send messages to Discord channels using webhooks (via URL or config key)
- Support for multiple webhooks with custom keys in `config.yml`
- Java API for sending, deleting and editing webhook messages
- Fallback to default webhook if sending fails
- Can be used as a standalone plugin **or** as a package in your own plugin

---

## Usage as a Plugin

1. **Download the latest JAR** from [GitHub Releases](https://github.com/mathijswouters/discordwebhook/releases).
2. Place it in your server's `plugins/` folder.
3. Start/reload your server.
4. Edit `config.yml` to add your webhooks (see below).

```yaml
webhooks:
  default: "https://discord.com/api/webhooks/xxx/yyy"
  staff: "https://discord.com/api/webhooks/aaa/bbb"
  admin: "https://discord.com/api/webhooks/ccc/ddd"
```
---

Status: Updated
