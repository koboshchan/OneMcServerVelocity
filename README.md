# OneMcServerVelocity

Simple Velocity proxy plugin for one Minecraft server setup.

## What it does

- Routes players by domain name
- Sends players to a limbo server first
- Lets premium players pass through
- Lets cracked players use `/register` and `/login`
- Stores player data in MongoDB
- Can block old client versions

## Player flow

- Player joins with a domain like `play.example.com`
- Plugin finds the target server for that domain
- Premium players are allowed and moved on
- Cracked players go to limbo and must log in
- After auth, the player is sent to the real server

## Need

- Java 21
- Docker and Docker Compose

## Build

```bash
./gradlew build
```

The jar will be in `build/libs/`.

## Run with Docker

```bash
docker compose up --build
```

This starts:

- `velocity`
- `mc`
- `mongo`

## Main files

- `docker-compose.yml` - start all services
- `build.gradle.kts` - build setup
- `velocitydata/velocity.toml` - Velocity config
- `plugins/onemcservervelocity/config.json` - plugin config after first start
- `src/main/` - plugin code

## Plugin config

The plugin writes a `config.json` file on first start.

Main keys:

- `servers` - list of domains and target servers
- `limbo_server` - host and port for the limbo server
- `mongodb_connection_string` - MongoDB URL
- `mongodb_database` - MongoDB DB name
- `kick_legacy` - block old Minecraft versions
- `translations` - text shown to players

Example:

```json
{
  "servers": [
    {
      "host": "play.example.com",
      "transfer_to": ["mc", 25565],
      "cracked_players": true
    }
  ],
  "limbo_server": ["mc", 25565],
  "mongodb_connection_string": "mongodb://mongo:27017",
  "mongodb_database": "onemcserver",
  "kick_legacy": false
}
```

## Installation

Make sure all back end servers have [OneGuard](https://github.com/koboshchan/OneGuard) installed and configured with the public key generated from this plugin.

1. Start the plugin one time.
2. Open `config.json` in the plugin data folder.
3. Add your domain in `servers`.
4. Set `transfer_to` to your backend server host and port.
5. Set `cracked_players` to `true` if cracked users are allowed.
6. Set MongoDB values if you changed them.
7. Restart Velocity.

## Auth commands

- `/register <password>` - make a new cracked account
- `/login <password>` - log in as a cracked player
- `/changepass <old> <new>` - change cracked player password
