"""A throw-away identity for contract runs (step 12, ADR0012): never used by a deployed service.

Creates an RSA key, writes its JWKS to <out>/jwks.json (serve that directory, point
LARDER_JWKS_URI at it) and prints a bearer token for Lena with both scopes and the roles
cook and community, valid for three hours.

Usage: python3 test_identity.py <out-dir>
"""

import json
import sys
import time
from pathlib import Path

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from jwt.algorithms import RSAAlgorithm

LENA = "f64e07f0-f9e7-4b7a-8695-e7a8b3ef5074"
KEY_ID = "contract-run"


def main(out_dir: str) -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_jwk = json.loads(RSAAlgorithm.to_jwk(key.public_key()))
    public_jwk.update({"kid": KEY_ID, "use": "sig", "alg": "RS256"})
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / "jwks.json").write_text(json.dumps({"keys": [public_jwk]}))
    now = int(time.time())
    claims = {
        "iss": "https://larder.org",
        "aud": "cooking-assistance",
        "sub": LENA,
        "roles": ["cook", "community"],
        "premium": False,
        "scope": "help:read help:write",
        "iat": now,
        "exp": now + 3 * 3600,
    }
    print(jwt.encode(claims, key, algorithm="RS256", headers={"kid": KEY_ID}))


if __name__ == "__main__":
    main(sys.argv[1])
