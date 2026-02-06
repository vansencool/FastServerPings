# FastServerPings

A client-side mod that rewrites the Minecraft server ping pipeline to scale properly with large server lists and make refreshes feel smoother.

Server pings are scheduled across the entire list instead of running strictly top to bottom, and results are handled using stale-while-revalidate so refreshes feel more responsive without showing invalid data.

---

## Comparison

### Vanilla
<img src="./assets/vanilla.gif" width="100%"/>

### FastServerPings
<img src="./assets/fastping.gif" width="100%"/>

### FastServerPings + SWR (stale-while-revalidate)
<img src="./assets/fastping_swr.gif" width="100%"/>

---

## Requirements

Fabric
Client-side only

---

## Modpacks and redistribution

You may use this mod in modpacks.
Please do not merge this mod into other clients or mods without permission.
If you want to do so, contact me first (`vansencool` on discord).