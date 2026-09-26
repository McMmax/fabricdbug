"""Build two isolated Modrinth imports for FabricDBug validation."""
import argparse
import hashlib
import json
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def offline_uuid(username: str) -> str:
    raw = bytearray(hashlib.md5(f"OfflinePlayer:{username}".encode()).digest())
    raw[6] = (raw[6] & 0x0F) | 0x30
    raw[8] = (raw[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(raw)))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fabricdbug-jar", type=Path, required=True)
    parser.add_argument("--fabric-api-jar", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)

    profiles = []
    for label, username in (("fabricdbug-a", "StomzyA"), ("fabricdbug-b", "StomzyB")):
        index = {
            "formatVersion": 1,
            "game": "minecraft",
            "versionId": "fabricdbug-stomzy-v3-1.0.0",
            "name": label,
            "summary": "FabricDBug SpacetimeDB-controlled Stomzy v3 test client",
            "files": [],
            "dependencies": {"minecraft": "26.2", "fabric-loader": "0.19.3"},
        }
        pack = args.output / f"{label}.mrpack"
        with zipfile.ZipFile(pack, "x", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("modrinth.index.json", json.dumps(index, indent=2))
            archive.writestr(
                "overrides/options.txt",
                "pauseOnLostFocus:false\nrenderDistance:6\nsimulationDistance:5\nmaxFps:60\n",
            )
            archive.write(args.fabricdbug_jar, f"overrides/mods/{args.fabricdbug_jar.name}")
            archive.write(args.fabric_api_jar, f"overrides/mods/{args.fabric_api_jar.name}")
        profiles.append({
            "profile": label,
            "offline_username": username,
            "offline_uuid": offline_uuid(username),
            "server": "127.0.0.1:25568",
            "spacetime_uri": "http://127.0.0.1:13002",
            "spacetime_database": "stomzydevhot",
            "package": pack.name,
            "sha256": digest(pack),
        })

    manifest = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "minecraft": "26.2",
        "fabric_loader": "0.19.3",
        "fabricdbug_sha256": digest(args.fabricdbug_jar),
        "fabric_api_sha256": digest(args.fabric_api_jar),
        "profiles": profiles,
    }
    (args.output / "profiles.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
