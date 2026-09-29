# FabricDBug Verification & Telemetry Harness

A zero-dependency verification and telemetry suite for driving deterministic game clients, aligning tick-indexed network/physics streams, and asserting movement authority and orientation invariants.

## Architecture & Systems Design

### 1. Declarative Batch Scheduling (No RPC in the 50ms Tick Loop)
A common anti-pattern when pairing agent/debug protocols with game engines is issuing individual RPC calls per simulation frame. At 20 TPS (50ms tick interval), network jitter and IPC latency inevitably desync the client.

Instead, the harness uses **declarative batch schedules**:
```python
# A single RPC or SpacetimeDB transaction enqueues a multi-second deterministic maneuver:
STEPS = [
    {"ticks": 30, "keys": ["forward", "sprint"]},
    {"ticks": 6, "keys": ["forward", "sprint", "jump"]},
    {"ticks": 20, "keys": ["forward", "sprint"]},
    {"ticks": 10, "keys": ["forward", "sprint"], "yaw": 90.0},
    {"ticks": 6, "keys": ["forward", "sprint", "jump"]},
    {"ticks": 20, "keys": ["forward", "sprint"]},
    {"ticks": 10, "keys": []},  # settle/standstill
]
```
The Fabric client executes these frames synchronously on the client tick thread without mid-circuit network round-trips.

### 2. Zero-Lag Tick-Ordering Contract
The client implements a strict phase-separated ordering contract between input actuation, engine physics, and telemetry capture:
1. **`START_CLIENT_TICK`**: `InputScriptEngine.tick()` applies active key bindings and look angles before physics calculations run.
2. **`Client Tick Physics`**: Minecraft simulates entity movement, block collisions, and dispatches outbound movement packets (`ServerboundMovePlayerPacket`).
3. **`END_CLIENT_TICK`**: `TelemetryBuffer.sample()` records final player position/velocity and drains packet taps (`CaptureWriter.onTick()`).

Because inputs for tick $N$ directly produce tick $N$'s resulting position and velocity, offline analysis in `align.py` requires zero clock-offset estimation or index shifting.

### 3. Stream Alignment & Telemetry Ingestion (`align.py`)
Single-client captures produce three companion JSONL streams sharing the same monotonic tick counter:
- `ticks.jsonl`: Client-side ground truth (position, velocity, bounding box, block contact, inputs).
- `outbound.jsonl`: Sent move packets captured via Netty send mixin (`ServerboundMovePlayerPacket`).
- `inbound.jsonl`: Server position corrections captured via Netty receive mixin (`ClientboundPlayerPositionPacket`).

`align.py` loads these streams and performs an exact tick-indexed group join into `AlignedTick` structures:
```python
from align import Capture

cap = Capture.load("captures/movement_circuit-20261015-120000")
for tick in cap.aligned():
    if tick.inbound:
        print(f"Server correction at tick {tick.tick}: {tick.inbound}")
```

### 4. Movement Authority Regression Evaluator (`reports/movement_diff.py`)
Detects server setbacks and desync corrections. When an inbound position packet arrives, `movement_diff` computes Euclidean divergence:
$$\Delta = \sqrt{(x_{\text{server}} - x_{\text{client}})^2 + (y_{\text{server}} - y_{\text{client}})^2 + (z_{\text{server}} - z_{\text{client}})^2}$$

If $\Delta > \text{threshold}$ (default: $0.1$ blocks), the incident is flagged, and a configurable $\pm N$-tick context window is printed detailing player velocity, ground state, input keys, outbound packets, and inbound corrections:
```
[1/1] Correction at Tick 81250:
  Delta: 0.4521 blocks (dx=-0.0210, dy=+0.4510, dz=+0.0120)
  Client pos: (9.310, 126.000, 1.440)
  Server pos: (9.289, 126.451, 1.452)  Teleport ID: 18
  Client vel: (+0.000, -0.078, +0.210)  On ground: True

  Context window (ticks 81240 .. 81260):
     Tick     | Client Position             | Velocity               | Grd | Keys           | Net
  -- -------- | --------------------------- | ---------------------- | --- | -------------- | --------------------
    81248     |    9.310, 125.922,    1.230 | +0.000, -0.078, +0.210 | T   | fwd,sprint     | out:1
    81249     |    9.310, 126.000,    1.440 | +0.000,  0.000, +0.210 | T   | fwd,sprint     | out:1
  * 81250     |    9.310, 126.000,    1.440 | +0.000, -0.078, +0.210 | T   | fwd,sprint     | IN:CORR(id=18)
    81251     |    9.289, 126.451,    1.452 | +0.000,  0.000, +0.000 | T   | fwd,sprint     | out:1
```

### 5. Architectural Shift: MCP to SpacetimeDB
- **`mcpclient` (Local HTTP JSON-RPC)**: Communicated with an embedded HTTP server running on `127.0.0.1:47800` via `mcpc.McpClient`.
- **`fabricdbug` (Distributed Reactive Database)**: FabricDBug eliminated the embedded HTTP server in favor of SpacetimeDB reducers and tables. `spacetime.SpacetimeClient` dispatches custom intent 11 transactions directly to SpacetimeDB (`command_intent_execute`), which FabricDBug consumes from SQL on the tick thread.

---

## Directory Structure

```
harness/
├── align.py                           # Tick-indexed stream joiner (ticks/outbound/inbound)
├── mcpc.py                            # Stdlib JSON-RPC 2.0 MCP client (for mcpclient captures)
├── spacetime.py                       # SpacetimeDB wire client (intent 11 dispatch & event queries)
├── reports/
│   └── movement_diff.py               # Movement authority regression diff & context reporter
├── scenarios/
│   ├── movement_stair_plaza.py        # Scripted sprint-jump circuit regression scenario
│   └── spacetime_rotation_check.py    # SpacetimeDB look replication & quantization check
└── tests/
    └── test_harness.py                # Unit tests verifying alignment, diff, and SpacetimeDB payloads
```

---

## Usage

### Run Unit Tests
```bash
python -m unittest harness/tests/test_harness.py
```

### Run Trace Alignment on a Capture Directory
```bash
python harness/align.py path/to/capture_dir
```

### Evaluate Movement Diff
```bash
python harness/reports/movement_diff.py path/to/capture_dir --threshold 0.1 --context 10
```

### Probe SpacetimeDB & FabricDBug
```bash
python scripts/probe-hot.py --target-uuid <player-uuid>
```

### Run Rotation Replication Check over SpacetimeDB
```bash
python harness/scenarios/spacetime_rotation_check.py --target-uuid <player-uuid> --yaw 180.0 --pitch 15.0
```
