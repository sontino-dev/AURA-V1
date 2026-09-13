# Brody KillAura — AI-driven Fabric 1.21.1 Combat Mod

> `yo what's good big bro 😭✌️` — Brody

Kill aura mod cho **Minecraft 1.21.1 Fabric**, tổng hợp kỹ thuật từ các client
mã nguồn mở (ThunderHack-Recode, VCore-0.8, Catalyst) + hệ thống AI tự học.

## Tính năng

### KillAura (cốt lõi)
- **Rotation modes**: `TRACK` (DVD-logo legit aim, port từ ThunderHack), `GRIM`
  (gcd-quantize nhanh, port từ VCore), `SNAP` (bắn nhanh khi cần), `NONE`
- **GCD compliance**: mọi rotation đều lượng tử hoá theo mouse sensitivity —
  anticheat đọc replay sẽ thấy như người chơi chuột thật
- **SmartCrit**: quyết định crit theo cây quyết định (sức khoẻ, hiệu ứng,
  trạng thái lướt, nước, lava...) giống ThunderHack Aura
- **ShieldBreaker**: swap axim, đánh phá khiên, swap về — kỹ thuật HVH kinh điển
- **SprintReset (w-tap packet)**: drop sprint trước cú đánh, trả sprint sau —
  giữ damage bonus và đánh lừa reach check
- **DVD-logo aim point**: điểm ngắm chạy loạn trong hitbox như người thật

### AI System (mới - tự phát triển)
- **TargetPredictor**: ngoại suy vị trí mục tiêu N ticks với velocity damping
  (0.91 ground drag, 0.98 vertical) + học gia tốc từ delta lịch sử
- **HitTrainer (UCB1 bandit)**: học online cái CPS và tốc độ quay nào "ăn hit"
  trên server hiện tại, tự điều chỉnh sau mỗi cú đánh (hit/miss/flag)
- **FlagDetector**: đọc server chat, phát hiện flag của Vulcan/GrimAC/Matrix/
  Intave/Spartan/Karhu/Polar... → tự backoff CPS + rotation, sau 10s nhả dần
- **Brain persistence**: lưu vào `config/brody-aura-brain.json`, chạy lại càng
  ngày càng "khớp" với server

### HVH Mode
- **AutoTotemSwap**: totem rớt → tự swap totem vào offhand khi máu thấp
- **CrystalSnipe**: phát hiện end crystal gần đối thủ máu thấp hơn mình → nổ
- **AntiFireball / AntiProjectile**: đấm lại fireball/wither skull/shulker bullet
- **SmartFeint**: random pause đòn để bait shield/parry của đối thủ

### Anti-Anticheat (Vulcan-safe layer)
- GCD quantize cả 2 kiểu (classic pow^3 và Grim pow^1.5)
- Pre/Post attack move packet interleave (chống Post check)
- CPS variance + rotation jitter + humanized overshoot
- Attack gate: giữ đòn khi CPS > 20 avg hoặc rotation delta quá đều
- AI backoff khi bị flag (đây là phần "AI chống anticheat" — không phải bypass
  tuyệt đối, Vulcan update liên tục nhưng AI sẽ tự xuống tay để không kick)

### VulcanMode (mới — layer lấy ý tưởng từ CatLean, phát triển tốt hơn)
Đào ngược CatLean ghost client (jar Pan4ur/CatLean decompile bằng Vineflower)
để lấy kiến trúc `AddonAuraRotation` của nó (getPoint / getRotation(attackTick))
và triết lý "WhiskerAura — randomized reach + rotation curves đọc như người thật".
Rồi phát triển thêm:

**Lấy từ CatLean:**
- Point-picking trên hitbox mục tiêu (không phải lúc nào cũng nhắm giữa)
- Rotation curve có nhận thức attack-tick (drift lười khi CD còn, siết lại sát đòn)
- Randomized per-attack reach (mỗi đòn một reach riêng)
- Attribute-level reach — hook `getEntityInteractionRange()` (mẹo 1.20.5+ của
  ReachStateEvent trong CatLean) giữ crosshair picking khớp reach gửi đi
- Profile hệ LEGIT / BALANCED / RAGE (kiểu NineLives adaptive cloaking)

**Phát triển thêm (không có trong CatLean):**
- **Paranoia engine**: mỗi lần bị flag → paranoia +35 (max 100), reach tự co
  tới 25%, rotation clamp siết lại, CPS bị throttle xác suất — decay dần theo
  tick. CatLean chỉ có profile tĩnh, của mình tự chuyển trạng thái giữa trận
- **KB-compliance hold**: vừa ăn knockback/explosion → giữ đòn 1-3 tick random
  (đúng cái window mà check Velocity/Simulation của Vulcan soi)
- **Anti-metronome w-tap**: sprint reset chỉ trên ~70% số đòn (config được),
  packet stream sprint không bao giờ tuần hoàn đều
- **Rotation-delta gate**: đòn bị giữ nếu delta quay của tick đó vượt clamp —
  hoàn thành cú quay trước, đòn chạy trên delta nhỏ hợp lệ (chống KillAura A/C)
- **Overshoot-correction pair**: vượt mục tiêu rồi tick sau kéo về (GCD-compliant
  cả hai bước) — pattern sửa tâm mà người thật luôn có, CatLean không làm cặp này

Keybind: `V` bật/tắt VulcanMode. Profile chỉnh trong `vulcanProfile` (config).

### ClickGUI + HUD (mới)
Toàn bộ UI vẽ tay bằng DrawContext (không dùng vanilla widget) — kiểu HVH client:

- **ClickGUI** mở bằng **Right Shift** (đổi được trong config `guiBind`)
  - Tab **Combat**: danh sách module — click tên để bật/tắt, click ô `[phím]`
    bên phải để **setbind** (nhấn phím bất kỳ, DEL xóa bind, ESC hủy,
    right-click ô bind cũng xóa)
  - Tab **Config**: sliders (Reach / Min CPS / Max CPS / Rotation Speed /
    Max Reach Soft), cyclers (Vulcan Profile LEGIT→BALANCED→RAGE / Target
    Sort), toggles (Vulcan Safe / Humanized Aim / GCD Compliant / AI / HUD)
  - Panel kéo thả được bằng header, vị trí lưu config; đóng GUI = tự save
- **HUD overlay** (bật/tắt trong Config tab hoặc `hudEnabled`)
  - Watermark + danh sách module đang bật kèm phím bind
  - Khi KillAura chạy: Target (tên + khoảng cách), AI CPS, Paranoia % +
    profile VulcanMode, số flag gần nhất
- **Bind động**: mỗi module một bind riêng (mặc định R/H/V), edge-detect mỗi
  client tick, không bị dính khi đang mở GUI; bind lưu trong
  `config/brody-killaura.json` (`"bind:KillAura": 82` ...)

### Tối ưu (chống lag)
- Không allocate trong tick loop (Object reuse, primitive arrays)
- Raytrace grid sample co giãn theo khoảng cách (0.15f step giống ThunderHack)
- Không dùng stream trong hot path (for loop thuần như VCore)
- Mixin inject Head/TAIL minimal, không Overwrite

## Cấu trúc code

```
src/main/java/com/brody/aura/
├── KillAuraMod.java            # entrypoint, keybinds, tick pump
├── config/AuraConfig.java      # json config, mọi knob ở đây
├── ai/
│   ├── AuraAI.java             # AI brain chính (hook mọi thứ)
│   ├── HitTrainer.java         # UCB1 bandit học CPS/rotation
│   └── TargetPredictor.java    # ngoại suy vị trí mục tiêu
├── module/
│   ├── Module.java / ModuleManager.java
│   ├── KillAura.java           # aura chính
│   ├── HvhMode.java            # totem/crystal/anti-fireball/feint
│   ├── VulcanMode.java         # CatLean-inspired adaptive anti-anticheat layer
│   ├── AutoSprint.java
│   └── VelocityFix.java
├── rotation/
│   ├── RotationManager.java    # TRACK/GRIM/SNAP engine + aimOverride + overshoot-correction
│   ├── AttackPlanner.java      # CatLean point-picking + attack-tick-aware planner
│   ├── GcdUtil.java            # mouse sensitivity GCD math
│   └── RaytraceUtil.java       # checkRtx + hitbox scan
├── target/TargetManager.java   # tìm + sort mục tiêu
├── anticheat/AnticheatManager.java  # vulcan-safe timing gate
├── ui/
│   ├── BrodyScreen.java        # ClickGUI: tabs, toggle, setbind, sliders
│   └── BrodyHUD.java           # HUD overlay: modules, target, paranoia
├── util/TickScheduler.java
└── mixin/                      # packet hooks + silent rotation inject
    ├── ClientPlayNetworkHandlerMixin.java
    ├── ClientPlayerEntityMixin.java
    ├── ClientPlayerInteractionManagerMixin.java
    ├── PlayerEntityMixin.java  # attribute-level reach (CatLean ReachStateEvent style)
    └── PlayerMoveC2SPacketAccessor.java
```

## Build

### Yêu cầu
- JDK 21 (Temurin/OpenJDK)
- Gradle (dùng `./gradlew` wrapper)

### Build local
```bash
chmod +x gradlew
./gradlew build
# jar output: build/libs/brody-killaura-1.0.0.jar
```

### Build qua GitHub Actions
1. Tạo repo trên GitHub, push toàn bộ folder này
2. Vào tab **Actions** → workflow `Build Brody KillAura` sẽ chạy tự động
3. Mỗi push = 1 build mới, tải jar ở **Artifacts**

### Cài vào game
1. Tải [Fabric Loader](https://fabricmc.net/use/installer/) 0.16.9+
2. Copy `brody-killaura-1.0.0.jar` vào `.minecraft/mods/`
3. Cài [Fabric API](https://modrinth.com/mod/fabric-api) 0.104.0+1.21.1
4. Vào game:
   - `Right Shift` = mở **ClickGUI** (Combat tab: bật/tắt + setbind; Config tab: tuning)
   - Mặc định: `R` = KillAura, `H` = HvhMode, `V` = VulcanMode — **đổi được
     hết trong GUI** (click ô `[phím]` cạnh tên module)
   - Chi tiết tinh chỉnh JSON trong `config/brody-killaura.json`

## Config mẫu (`config/brody-killaura.json`)

```json
{
  "reach": 3.0,
  "wallRange": 3.0,
  "minCps": 6.0,
  "maxCps": 13.0,
  "rotateSilent": true,
  "rotationSpeed": 12.0,
  "gcdCompliant": true,
  "aiEnabled": true,
  "aiAdaptiveCps": true,
  "aiPredictVelocity": true,
  "vulcanSafe": true,
  "sprintReset": true,
  "vulcanProfile": "BALANCED",
  "vulcanPointPick": true,
  "vulcanReachMin": 2.35,
  "vulcanReachJitter": 0.22,
  "vulcanSprintResetChance": 0.7,
  "vulcanKbHoldMax": 3,
  "vulcanParanoiaDecay": 1.2,
  "guiBind": 344,
  "hudEnabled": true,
  "hvhShieldBreaker": true,
  "hvhAutoCrystal": false,
  "hvhSmartFeint": true,
  "targetSort": "DISTANCE"
}
```

## Cách hoạt động (kiến trúc kỹ thuật)

```
ClientTick ──> ModuleManager.tick()
              │
              ├─> KillAura.onTick()
              │     ├─> TargetManager.updateTarget()  ← skipEntity filter + sort
              │     ├─> autoCrit() decision tree
              │     ├─> RotationManager.rotate()
              │     │     ├─> computeTrackAimPoint() (DVD-logo drift)
              │     │     ├─> GcdUtil.applyGrimQuantize() / applyGcd()
              │     │     └─> RaytraceUtil.checkRtx() → lookingAtHitbox
              │     ├─> AnticheatManager.gateAttack()
              │     ├─> shieldBreaker() → axe swap
              │     ├─> attack() → interactionManager.attackEntity()
              │     │     └─> ClientPlayNetworkHandlerMixin patches rotation
              │     └─> AI feedback loop
              │           ├─> notifyDamageConfirmed() → onHitSuccess
              │           ├─> timeout → onHitMiss
              │           └─> onServerChat → onFlagDetected → backoff
              │
              └─> HvhMode.onTick()
                    ├─> autoTotemSwap()
                    ├─> antiProjectile()
                    └─> crystalSnipe()
```

## Changelog

### v1.0.1 — Fix crash khi khởi động (mixin targets)
Crash cũ: `InvalidInjectionException ... could not find any targets matching
'Lnet/minecraft/class_634;sendPacket(...)'`. Nguyên nhân + cách sửa (đã verify
từng target bằng cách decompile jar 1.21.1):

1. **`sendPacket` dời class cha** — từ 1.20.2 method `sendPacket` nằm ở
   `ClientCommonNetworkHandler` (class cha của `ClientPlayNetworkHandler`),
   không còn trong `ClientPlayNetworkHandler`. → Tách silent-rotation hook
   sang mixin mới `ClientCommonNetworkHandlerMixin` (vẫn bắt đủ mọi move
   packet vì `ClientPlayerEntity.sendMovementPackets()` route qua
   `networkHandler.sendPacket(...)` kế thừa).
2. **`attackEntity` thừa tham số** — 1.21.1 chỉ có
   `attackEntity(PlayerEntity, Entity)` (2 params, không có `Hand`). → Sửa
   handler signature trong `ClientPlayerInteractionManagerMixin`.
3. **Accessor ghi field `final`** — `PlayerMoveC2SPacket.yaw/pitch` là
   `protected final float`. → Thêm `@Mutable` cho 2 setter trong
   `PlayerMoveC2SPacketAccessor` (Mixin sẽ gỡ `final` của field đích).

## Legal

-educational purposes only. Dùng trên server có quy định cho phép hoặc server
riêng của bạn. Không có bypass nào là vĩnh viễn — anticheat update liên tục,
AI layer giúp hạn chế flag nhưng không phải là bất khả chiến bại.

## Credits

- **ThunderHack-Recode** (Pan4ur) — Aura, smartCrit, shieldBreaker, wallsBypass
- **VCore-0.8** (ftl2ndofficial) — Rotation Manager (Track/Grim/Snap), GCD math
- **Catalyst** (Pr3roxDLC) — KillAura structure, cooldown sync
- **Brody** — tổng hợp, AI layer, HVH mode, anticheat gate, tối ưu 😭✌️
