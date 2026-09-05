# Aether — Minecraft: Java Edition launcher

Open-source desktop launcher for **licensed** Minecraft: Java Edition.

- Official Microsoft OAuth (PKCE, RFC 8252) → Xbox Live → XSTS → Minecraft Services
- Entitlement verified via `/entitlements/mcstore` before launch — **no offline or cracked login**
- Game files downloaded from Mojang metadata endpoints, verified by SHA-1
- No ads, analytics or third-party SDKs; outbound hosts restricted by a fixed allowlist, enforced by a test
- No game modification: no javaagent, no injection, no bundled cheats
- Material Design 3 UI, Kotlin + Compose Multiplatform, Windows

