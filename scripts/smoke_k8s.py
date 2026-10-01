import base64
import hashlib
import hmac
import json
import secrets
import struct
import sys
import time
import urllib.error
import urllib.request


BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:8088"


def call(method, path, body=None, cookie=None, fingerprint=None):
    headers = {"Content-Type": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    if fingerprint:
        headers["X-Device-Fingerprint"] = fingerprint
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            payload = response.read()
            return response.status, json.loads(payload) if payload else {}, response.headers
    except urllib.error.HTTPError as error:
        raise AssertionError(f"{method} {path}: HTTP {error.code}") from None


def totp(secret):
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    counter = struct.pack(">Q", int(time.time()) // 30)
    digest = hmac.new(key, counter, hashlib.sha1).digest()
    offset = digest[-1] & 15
    return f"{(struct.unpack('>I', digest[offset:offset + 4])[0] & 0x7fffffff) % 1000000:06d}"


def create_user(label):
    phone = "90" + f"{secrets.randbelow(100000000):08d}"
    fingerprint = "resource-lane-smoke-" + label
    status, registered, _ = call("POST", "/api/auth/register", {"userName": label, "phoneNumber": phone})
    assert status == 200
    status, _, headers = call("POST", "/api/auth/verify", {
        "phoneNumber": phone,
        "totp": totp(registered["secretKey"]),
        "deviceFingerprint": fingerprint,
    })
    assert status == 200, status
    cookie = headers["Set-Cookie"].split(";", 1)[0]
    _, user_id, _ = call("GET", "/api/auth/getUserId", cookie=cookie, fingerprint=fingerprint)
    status, wallet, _ = call("GET", "/api/wallet/getWallet", cookie=cookie, fingerprint=fingerprint)
    assert status == 200 and float(wallet["balance"]) == 0 and wallet["units"] == 0
    status, _, _ = call("POST", "/api/auth/verify", {
        "phoneNumber": phone,
        "totp": totp(registered["secretKey"]),
        "deviceFingerprint": fingerprint,
    })
    assert status == 200
    return {"id": user_id, "cookie": cookie, "fingerprint": fingerprint}


def user_call(user, method, path, body=None):
    return call(method, path, body, user["cookie"], user["fingerprint"])


buyer = create_user("SmokeBuyer")
seller = create_user("SmokeSeller")
assert user_call(buyer, "POST", "/api/wallet/addMoney", {"amount": 100})[0] == 200
assert user_call(seller, "POST", "/api/wallet/addUnits", {"quantity": 3})[0] == 200
assert user_call(seller, "POST", "/api/trade/createOrder", {
    "quantity": 3, "price": 10.0, "side": "SELL", "type": "LIMIT",
})[0] == 202
assert user_call(buyer, "POST", "/api/trade/createOrder", {
    "quantity": 2, "price": 10.0, "side": "BUY", "type": "LIMIT",
})[0] == 202

deadline = time.monotonic() + 45
while time.monotonic() < deadline:
    _, buy_wallet, _ = user_call(buyer, "GET", "/api/wallet/getWallet")
    _, sell_wallet, _ = user_call(seller, "GET", "/api/wallet/getWallet")
    if (round(float(buy_wallet["balance"]), 2), buy_wallet["units"],
            round(float(sell_wallet["balance"]), 2), sell_wallet["units"]) == (80, 2, 20, 1):
        break
    time.sleep(1)
else:
    raise AssertionError("Trade did not settle within 45 seconds")

_, buyer_trades, _ = user_call(buyer, "GET", "/api/trade/getPastTrades")
_, seller_trades, _ = user_call(seller, "GET", "/api/trade/getPastTrades")
assert len(buyer_trades.get("pastTrades", [])) == 1
assert len(seller_trades.get("pastTrades", [])) == 1

# The remaining ask is 10. A market buy capped at 9 must cancel and release its hold.
assert user_call(buyer, "POST", "/api/trade/createOrder", {
    "quantity": 1, "price": 9.0, "side": "BUY", "type": "MARKET",
})[0] == 202
deadline = time.monotonic() + 45
while time.monotonic() < deadline:
    _, orders, _ = user_call(buyer, "GET", "/api/trade/getOrders")
    if not orders.get("orders"):
        break
    time.sleep(1)
else:
    raise AssertionError("Market buy above its price cap was not cancelled")
assert user_call(buyer, "POST", "/api/wallet/withdrawMoney", {"amount": 80})[0] == 200
assert user_call(buyer, "POST", "/api/wallet/addMoney", {"amount": 20})[0] == 200

# A market buy capped at 11 can match the same resting ask at 10.
assert user_call(buyer, "POST", "/api/trade/createOrder", {
    "quantity": 1, "price": 11.0, "side": "BUY", "type": "MARKET",
})[0] == 202
deadline = time.monotonic() + 45
while time.monotonic() < deadline:
    _, buy_wallet, _ = user_call(buyer, "GET", "/api/wallet/getWallet")
    _, sell_wallet, _ = user_call(seller, "GET", "/api/wallet/getWallet")
    if (round(float(buy_wallet["balance"]), 2), buy_wallet["units"],
            round(float(sell_wallet["balance"]), 2), sell_wallet["units"]) == (10, 3, 30, 0):
        break
    time.sleep(1)
else:
    raise AssertionError("Capped market buy did not settle within 45 seconds")

print("PASS: TOTP login, wallet creation, limit matching, settlement, trade history, market-buy cap and reservation release")
print(f"buyer_id={buyer['id']} seller_id={seller['id']}")
