#!/usr/bin/env python3
"""
Offline Q-Learning trainer for Hytale AI Companion.
Generates base_qtable.json that ships with the mod.

Run:  python tools/train_qtable.py
Then: cp base_qtable.json app/src/main/resources/

State space (48 states):
  playerHpBucket : 0=<25%  1=25-50%  2=50-75%  3=75%+
  enemyCountBucket: 0=none  1=one     2=2-3     3=four+
  distanceBucket : 0=<6blk  1=6-15blk  2=>15blk

Actions (5):
  FOLLOW=0  ATTACK=1  HEAL=2  FLEE=3  TALK=4
"""

import random, json, math, sys

# ── Hyperparameters ────────────────────────────────────────────────────────────
ALPHA          = 0.10          # learning rate
GAMMA          = 0.90          # discount factor
EPSILON_START  = 0.40          # initial exploration rate
EPSILON_MIN    = 0.05          # minimum exploration rate
EPSILON_DECAY  = 0.000002      # per-decision decay (slower → more thorough coverage)
NUM_DECISIONS  = 5_000_000     # total training steps
STEPS_PER_EP   = 30            # max steps before episode reset
OUTPUT_PATH    = "base_qtable.json"

# ── State helpers ──────────────────────────────────────────────────────────────
HP_BUCKETS, ENEMY_BUCKETS, DIST_BUCKETS = 4, 4, 3
NUM_STATES  = HP_BUCKETS * ENEMY_BUCKETS * DIST_BUCKETS   # 48
NUM_ACTIONS = 5
FOLLOW, ATTACK, HEAL, FLEE, TALK = 0, 1, 2, 3, 4
ACTION_NAMES = ["FOLLOW", "ATTACK", "HEAL", "FLEE", "TALK"]

def s_idx(hp, en, dist):
    return hp * 12 + en * 3 + dist

def hp_ratio(bucket):
    return [0.12, 0.37, 0.62, 0.87][bucket]

# ── Transition + reward model ──────────────────────────────────────────────────
# Mirrors the logic in CompanionCombatSystem.computeReward() as closely as
# possible, plus a plausible stochastic transition model.

def step(hp, en, dist, action):
    """Returns (next_hp, next_en, next_dist, reward)."""
    n_hp, n_en, n_dist = hp, en, dist
    reward = 0.05  # base survival reward

    # ── Passive damage (enemies deal damage each tick regardless of action) ──
    if en > 0:
        if dist == 0:
            dmg = random.uniform(8, 18) * en * 0.5   # up to ~18 per close enemy
            reward -= dmg / 10.0
            if dmg > 12 and n_hp > 0:
                n_hp -= 1
        elif dist == 1:
            dmg = random.uniform(3, 8)
            reward -= dmg / 10.0
            if dmg > 7 and n_hp > 0:
                n_hp -= 1

    # ── Critical HP event penalty ──
    if n_hp == 0 and hp > 0:
        reward -= 15.0

    # ── Action effects ────────────────────────────────────────────────────────
    if action == FOLLOW:
        if en > 0 and dist > 0 and random.random() < 0.35:
            n_dist = dist - 1          # enemies close in while following
        if dist == 0 and en > 0:
            reward += 0.3              # +0.3 staying close in combat

    elif action == ATTACK:
        if en == 0:
            reward -= 1.0              # attacking nothing
        elif dist == 2:
            reward -= 1.5              # sprint-aggro penalty
            n_dist = max(0, dist - 1)
            if random.random() < 0.55 and n_hp > 0:  # exposed while sprinting
                n_hp -= 1
                reward -= 0.5
        else:
            # Effective melee range
            kill_p = 0.55 if dist == 0 else 0.30
            if random.random() < kill_p and n_en > 0:
                n_en -= 1
                reward += 0.8          # killed an enemy

    elif action == HEAL:
        ratio = hp_ratio(hp)
        if ratio < 0.50:               # player below 50% HP
            n_hp = min(3, hp + 1)
            reward += 3.0
        elif ratio > 0.80:             # healing when almost full
            reward -= 2.0
        else:
            n_hp = min(3, hp + 1)
            reward += 0.5

    elif action == FLEE:
        if en == 0:
            reward -= 1.0              # fleeing nothing
        else:
            n_dist = min(2, dist + 1)  # gain distance
            if hp == 0 and en >= 2:
                reward += 2.0          # smart strategic retreat
            elif hp <= 1:
                reward += 0.5          # reasonable when low HP

    elif action == TALK:
        if hp == 0 and en == 0:
            reward += 2.0              # cry for help when down, no threat
        elif hp == 3:
            reward -= 1.0              # pointless chatter at full HP

    return n_hp, n_en, n_dist, reward

# ── Q-table ────────────────────────────────────────────────────────────────────
q = [[0.0] * NUM_ACTIONS for _ in range(NUM_STATES)]

def best_q(s):
    return max(q[s])

def choose(s, eps):
    if random.random() < eps:
        return random.randrange(NUM_ACTIONS)
    return max(range(NUM_ACTIONS), key=lambda a: q[s][a])

# ── Training loop ──────────────────────────────────────────────────────────────
eps      = EPSILON_START
total    = 0
ep       = 0

print(f"Training: {NUM_DECISIONS:,} decisions  α={ALPHA}  γ={GAMMA}")
print("-" * 60)

while total < NUM_DECISIONS:
    ep += 1
    # Sample starting state; 70% chance of a combat situation
    if random.random() < 0.70:
        hp, en, dist = random.randrange(4), random.randint(1, 3), random.randrange(3)
    else:
        hp, en, dist = random.randrange(4), 0, random.randrange(3)

    for _ in range(STEPS_PER_EP):
        total += 1
        eps = max(EPSILON_MIN, EPSILON_START - total * EPSILON_DECAY)

        s  = s_idx(hp, en, dist)
        a  = choose(s, eps)

        n_hp, n_en, n_dist, reward = step(hp, en, dist, a)
        ns = s_idx(n_hp, n_en, n_dist)

        q[s][a] += ALPHA * (reward + GAMMA * best_q(ns) - q[s][a])

        hp, en, dist = n_hp, n_en, n_dist
        if total >= NUM_DECISIONS:
            break
        # Terminal: player dead mid-combat → reset
        if hp == 0 and en > 0:
            break

    if ep % 50_000 == 0:
        pct = 100 * total / NUM_DECISIONS
        print(f"  {pct:5.1f}%  ep={ep:,}  decisions={total:,}  ε={eps:.4f}")

print(f"\nDone. Episodes: {ep:,}  Decisions: {total:,}  Final ε={eps:.4f}")

# ── Print learned policy ───────────────────────────────────────────────────────
print("\n=== LEARNED POLICY ===")
header = f"{'hp':>4} {'en':>4} {'dist':>5}  {'best':>7}  " + "  ".join(f"{n:>8}" for n in ACTION_NAMES)
print(header)
print("-" * len(header))
for hp in range(3, -1, -1):
    for en in range(4):
        for dist in range(3):
            s = s_idx(hp, en, dist)
            best = max(range(NUM_ACTIONS), key=lambda a: q[s][a])
            vals = "  ".join(f"{q[s][a]:8.3f}" for a in range(NUM_ACTIONS))
            print(f"  {hp:2d}   {en:2d}    {dist:2d}  {ACTION_NAMES[best]:>7s}  {vals}")

# ── Sanity checks ──────────────────────────────────────────────────────────────
print("\n=== SANITY CHECKS ===")
checks = [
    ("hp=3 en=1 dist=0 → ATTACK?",  s_idx(3,1,0), ATTACK),
    ("hp=3 en=1 dist=2 → FOLLOW?",  s_idx(3,1,2), FOLLOW),
    ("hp=0 en=1 dist=0 → FLEE?",    s_idx(0,1,0), FLEE),
    ("hp=1 en=0 dist=0 → HEAL?",    s_idx(1,0,0), HEAL),
    ("hp=3 en=0 dist=0 → FOLLOW?",  s_idx(3,0,0), FOLLOW),
    ("hp=0 en=3 dist=0 → FLEE?",    s_idx(0,3,0), FLEE),
]
all_ok = True
for desc, s, expected_action in checks:
    actual = max(range(NUM_ACTIONS), key=lambda a: q[s][a])
    ok = "✓" if actual == expected_action else "✗"
    if actual != expected_action:
        all_ok = False
    print(f"  {ok} {desc}  (got {ACTION_NAMES[actual]})")

if not all_ok:
    print("\n⚠  Some checks failed. Consider increasing NUM_DECISIONS or tweaking the reward model.")
else:
    print("\n✓ All sanity checks passed.")

# ── Save ───────────────────────────────────────────────────────────────────────
save_data = {
    "qTable": q,
    "epsilon": EPSILON_MIN,
    "totalDecisions": total
}
with open(OUTPUT_PATH, "w") as f:
    json.dump(save_data, f, indent=2)

print(f"\nSaved: {OUTPUT_PATH}")
print("Next step: cp base_qtable.json app/src/main/resources/")
