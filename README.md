# FabricDBug

FabricDBug is the successor to MCPClient: a developer-only Fabric client mod
whose culling overlay and scripted input are controlled through SpacetimeDB.
It contains no MCP server, JSON-RPC tool registry, or local agent endpoint.

## Data and control

- Player positions are read from Hot `player_physics_state`.
- The overlay uses those authoritative positions rather than client entity
  packet positions.
- Agents enqueue custom commands through Hot's existing
  `command_intent_execute` reducer with `intent_type = 11`, `target_uuid` set
  to the debug client's UUID, and a JSON `payload`.

Supported payloads:

```json
{"action":"stop"}
{"action":"run_script","steps":[{"ticks":40,"keys":["FORWARD"]},{"ticks":1,"keys":[],"yaw":180}]}
{"action":"run_script","steps":[{"ticks":1,"keys":["ATTACK"]},{"ticks":10,"keys":[]}]}
{"action":"run_script","steps":[{"ticks":1,"keys":["USE"]},{"ticks":10,"keys":[]}]}
{"action":"freelook_start","perspective":"THIRD_PERSON_BACK"}
{"action":"freelook_stop"}
{"action":"zoom_start"}
{"action":"zoom_stop"}
{"action":"screenshot"}
{"action":"snapshot","request":"rotation-check"}
```

Snapshots report local and visible-player `yaw`, `pitch`, and `headYaw`
alongside positions through `fabricdbug_event`. Use a `run_script` step with
empty `keys` and explicit `yaw`/`pitch` to test stationary look replication,
then snapshot both clients. Compare angles modulo 360; network head yaw is
quantized to 1/256 of a turn (about 1.41 degrees).

Freelook is discovered by Fabric entrypoint at runtime, so FabricDBug still
builds without a hard dependency on its jar. Install Freelook 1.4.3 for
Minecraft 26.2/Fabric in the developer profile to enable those commands.

The screenshot command uses Minecraft's native render-target capture and saves
the PNG under the profile's `screenshots/` directory. FabricDBug reports the
scheduled/completed path through the `report_fabricdbug_event` reducer so an
agent can query the result from SpacetimeDB and open the local image.

The old `mcpclient/` project remains available for historical regression
fixtures, but it is not part of the FabricDBug test path. Run the deterministic
Hot smoke probe with a FabricDBug player UUID:

```powershell
pwsh .\scripts\probe-hot.ps1 -TargetUuid <player-uuid>
```

The probe exits `0` only after Hot health, SQL, and
`command_intent_execute(intent_type = 11)` succeed. It exits `2` with a JSON
blocker when no Hot endpoint is available.
