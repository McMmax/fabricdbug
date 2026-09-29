"""Unit tests for the FabricDBug Python verification harness."""
from __future__ import annotations

import json
import tempfile
from pathlib import Path
import unittest

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "reports"))

from align import Capture, AlignedTick
from movement_diff import analyze_capture, run as movement_diff_run
from spacetime import SpacetimeClient


class TestAlign(unittest.TestCase):
    def test_aligned_join(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            p = Path(tmpdir)
            # Write synthetic tick records
            ticks = [
                {"tick": 100, "pos": [10.0, 64.0, 20.0], "vel": [0.0, 0.0, 0.0], "on_ground": True},
                {"tick": 101, "pos": [10.5, 64.0, 20.0], "vel": [0.5, 0.0, 0.0], "on_ground": True},
            ]
            outbound = [
                {"tick": 100, "packet_type": "ServerboundMovePlayerPacket", "x": 10.0, "y": 64.0, "z": 20.0},
            ]
            inbound = [
                {"tick": 101, "id": 1, "x": 10.5, "y": 64.0, "z": 20.0, "dx": 0.0, "dy": 0.0, "dz": 0.0},
            ]

            (p / "ticks.jsonl").write_text("\n".join(json.dumps(r) for r in ticks) + "\n", encoding="utf-8")
            (p / "outbound.jsonl").write_text("\n".join(json.dumps(r) for r in outbound) + "\n", encoding="utf-8")
            (p / "inbound.jsonl").write_text("\n".join(json.dumps(r) for r in inbound) + "\n", encoding="utf-8")

            cap = Capture.load(p)
            self.assertEqual(len(cap.ticks), 2)
            self.assertEqual(cap.tick_range(), (100, 101))

            aligned = cap.aligned()
            self.assertEqual(len(aligned), 2)
            self.assertEqual(aligned[0].tick, 100)
            self.assertEqual(len(aligned[0].outbound), 1)
            self.assertEqual(len(aligned[0].inbound), 0)

            self.assertEqual(aligned[1].tick, 101)
            self.assertEqual(len(aligned[1].outbound), 0)
            self.assertEqual(len(aligned[1].inbound), 1)


class TestMovementDiff(unittest.TestCase):
    def test_no_corrections_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            p = Path(tmpdir)
            ticks = [
                {"tick": 50, "pos": [0.0, 64.0, 0.0], "vel": [0.0, 0.0, 0.0], "on_ground": True, "input": {}},
            ]
            # Inbound pos identical to client pos
            inbound = [
                {"tick": 50, "id": 10, "x": 0.0, "y": 64.0, "z": 0.0, "dx": 0.0, "dy": 0.0, "dz": 0.0},
            ]
            (p / "ticks.jsonl").write_text("\n".join(json.dumps(r) for r in ticks) + "\n", encoding="utf-8")
            (p / "inbound.jsonl").write_text("\n".join(json.dumps(r) for r in inbound) + "\n", encoding="utf-8")

            cap = Capture.load(p)
            incidents = analyze_capture(cap, threshold=0.1)
            self.assertEqual(len(incidents), 0)
            self.assertEqual(movement_diff_run(p, threshold=0.1, context=2), 0)

    def test_correction_beyond_threshold_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            p = Path(tmpdir)
            ticks = [
                {"tick": 50, "pos": [0.0, 64.0, 0.0], "vel": [0.0, 0.0, 0.0], "on_ground": True, "input": {"fwd": 1, "sprint": True}},
            ]
            # Inbound pos diverged by 0.5 blocks on Y (setback)
            inbound = [
                {"tick": 50, "id": 11, "x": 0.0, "y": 64.5, "z": 0.0, "dx": 0.0, "dy": 0.0, "dz": 0.0},
            ]
            (p / "ticks.jsonl").write_text("\n".join(json.dumps(r) for r in ticks) + "\n", encoding="utf-8")
            (p / "inbound.jsonl").write_text("\n".join(json.dumps(r) for r in inbound) + "\n", encoding="utf-8")

            cap = Capture.load(p)
            incidents = analyze_capture(cap, threshold=0.1)
            self.assertEqual(len(incidents), 1)
            self.assertAlmostEqual(incidents[0].delta, 0.5)
            self.assertEqual(incidents[0].keys, ["forward", "sprint"])
            self.assertEqual(movement_diff_run(p, threshold=0.1, context=2), 1)


class TestSpacetimeClient(unittest.TestCase):
    def test_intent_format(self) -> None:
        client = SpacetimeClient(uri="http://127.0.0.1:13002", database="testdb")
        # Intercept call_reducer to inspect arguments
        captured_args = []
        client.call_reducer = lambda reducer, args: captured_args.append((reducer, args))  # type: ignore

        res = client.send_command_intent(
            target_uuid="test-uuid-1234",
            payload={"action": "stop"},
            intent_type=11,
        )
        self.assertEqual(len(captured_args), 1)
        reducer, args = captured_args[0]
        self.assertEqual(reducer, "command_intent_execute")
        self.assertEqual(len(args), 15)
        self.assertEqual(args[8], 11)  # intent_type
        self.assertEqual(args[10], "test-uuid-1234")  # target_uuid
        self.assertEqual(args[13], '{"action": "stop"}')  # payload


if __name__ == "__main__":
    unittest.main()
