# BlueMoon

블록벤치(Blockbench) 모델을 그대로 불러와 **미스틱몹(MythicMobs) 스타일 스킬**로 움직이는 Paper 플러그인 모음입니다.

| 플러그인 | 용도 | 문서 |
| --- | --- | --- |
| **BlueMoon** | 블록벤치 모델 커스텀 몹 + 몹 스킬 | 이 문서 |
| **BlueMoonSkills** | 플레이어 스킬 (이펙트 모델, 소환수, 무기 모델) | [skills/README.md](skills/README.md) |

두 플러그인은 서로 독립적이며, 공통 엔진(`core`)을 각자 jar 안에 포함합니다.

ModelEngine 같은 별도 모델 플러그인 없이, `.bbmodel` 파일을 폴더에 넣으면 리소스팩 생성 → 디스플레이 엔티티 렌더링 → 애니메이션 → 스킬 실행까지 한 번에 처리합니다.

- `.bbmodel` 직접 읽기 (큐브, 그룹(본), 로케이터, 내장 텍스처, 애니메이션, Molang 키프레임)
- 리소스팩 자동 생성 (`plugins/BlueMoon/resourcepack.zip`) + 선택적 내장 HTTP 배포
- 본마다 `ItemDisplay` 하나, 매 틱 애니메이션 보간, 애니메이션 간 블렌딩(fade in/out)
- 미스틱몹 문법: `메카닉{옵션} @타겟터{옵션} ~트리거 ?조건 확률`, 메타스킬, `delay`, 쿨타임
- **블록벤치 연동**
  - `animation{a=slam}` 으로 블록벤치 애니메이션 재생
  - 블록벤치 애니메이션의 **Timeline 키프레임 스크립트에 스킬 라인을 적으면 그 시점에 실행**
  - Sound / Particle 키프레임도 그대로 재생 (파티클은 로케이터 위치에서)
  - `@ModelPart{bone=right_hand}` 로 본/로케이터의 실제 월드 좌표를 타겟으로 사용
  - `idle` / `walk` / `attack` / `hurt` / `death` / `spawn` 상태 애니메이션 자동 재생, `head` 본은 시선 추적

## 요구 사항

- Paper **1.21.4 이상** (item_model 컴포넌트와 `items/` 모델 정의를 사용합니다)
- Java 21

## 빌드

```bash
./gradlew build
# bluemoon/build/libs/BlueMoon-<버전>.jar
# skills/build/libs/BlueMoonSkills-<버전>.jar
```

GitHub Actions(`.github/workflows/build.yml`)가 푸시마다 빌드하고 두 jar 를 `plugins` 아티팩트로 올립니다.

## 빠르게 써 보기

1. jar 를 `plugins/` 에 넣고 서버를 켭니다. 처음 실행하면 예제가 설치됩니다.
   - `plugins/BlueMoon/models/golem.bbmodel` – 예제 골렘 (idle, walk, attack, slam, hurt, death, spawn)
   - `plugins/BlueMoon/mobs/example_mobs.yml` – `StoneGolem`
   - `plugins/BlueMoon/skills/example_skills.yml` – 내려찍기/돌 던지기/분노
2. `plugins/BlueMoon/resourcepack.zip` 을 적용합니다. (아래 "리소스팩" 참고)
3. 게임에서 `/bm spawn StoneGolem`

`golem.bbmodel` 은 블록벤치로 열어서 그대로 수정해 볼 수 있습니다. `slam` 애니메이션의 0.7초 Timeline 키프레임에 스킬 라인이 들어 있습니다.

## 리소스팩

서버 시작과 `/bm reload`, `/bm pack` 때마다 `plugins/BlueMoon/resourcepack.zip` 이 새로 만들어집니다.

- **직접 배포**: zip 을 웹에 올리고 `server.properties` 의 `resource-pack` / `resource-pack-sha1` 에 등록합니다. (sha1 은 reload 메시지에 나옵니다)
- **내장 서버**: `config.yml` 에서 `resource-pack.server.enabled: true`, `public-url` 을 플레이어가 접속 가능한 주소로 설정하면 접속 시 자동 전송됩니다.
- 다른 리소스팩과 합치려면 파일을 `plugins/BlueMoon/pack-extra/` 에 넣으세요 (`assets/...` 구조 그대로). 생성 시 함께 들어갑니다. 커스텀 사운드도 여기에 넣으면 됩니다.

## 블록벤치 모델 만들기

| 항목 | 규칙 |
| --- | --- |
| 형식 | **Generic Model**(free) 권장. Java Block 형식도 읽습니다. |
| 본 | 블록벤치의 **그룹 = 본**. 그룹의 피벗이 회전 중심입니다. 그룹 안의 큐브가 한 덩어리로 렌더링됩니다. |
| 방향 | 모델 앞쪽이 블록벤치의 **North(-Z)** 를 보게 만드세요. 원점(0,0,0)이 몹의 발밑입니다. |
| 큐브 회전 | 한 축 & 22.5° 단위(±45° 이내)는 그대로 사용. 그 외 회전은 **자동으로 전용 본을 만들어** 정확히 표현합니다. |
| 크기 | 본 피벗에서 24픽셀(1.5블록)보다 멀리 있는 큐브도 자동 축소/확대로 처리됩니다. |
| 텍스처 | bbmodel 안에 저장된 텍스처를 사용합니다 (블록벤치에서 텍스처를 "저장"하지 않은 외부 파일은 같은 폴더에서 찾습니다). 여러 장, 애니메이션 텍스처 지원. |
| `head` / `h_` 본 | 몹이 바라보는 방향을 따라 회전합니다. |
| `hitbox` 본 | 렌더링하지 않습니다. |
| 로케이터 | `@ModelPart{bone=로케이터}`, Particle 키프레임 위치로 쓰입니다. |
| 메쉬 | 지원하지 않습니다 (큐브만). |

### 애니메이션

- 채널: Rotation / Position / Scale, 보간: Linear / Catmull-Rom / Step / Bezier
- 루프 모드(`once`/`loop`/`hold`)는 블록벤치 설정을 따르고, 스킬에서 덮어쓸 수 있습니다.
- 키프레임 값에 Molang 사용 가능: `math.sin(q.anim_time * 360) * 10`
  - `query.anim_time`, `query.life_time`, 사칙연산, 비교, `? :`, `math.sin/cos/abs/clamp/lerp/min/max/pow/sqrt/floor/ceil/round/random...`
- 상태 애니메이션 이름(몹 설정의 `Animations` 로 바꿀 수 있음)

| 상태 | 재생 시점 |
| --- | --- |
| `idle` | 멈춰 있을 때 (반복) |
| `walk` | 이동 중 (반복) |
| `attack` | 몹이 공격할 때 |
| `hurt` | 피격 시 (모델도 붉게 깜빡임) |
| `death` | 사망 시 (애니메이션이 끝난 뒤 모델 제거) |
| `spawn` | 소환 시 |

### 이펙트 키프레임 (블록벤치 Animation → Effects)

| 채널 | 동작 |
| --- | --- |
| Sound | `Effect` 칸의 사운드 ID 재생 (예: `entity.generic.explode`) |
| Particle | `Effect` 칸의 파티클(예: `flame`)을 `Locator` 위치에 생성 |
| Timeline | **스크립트 칸의 각 줄을 스킬 라인으로 실행** (`#` 로 시작하면 주석) |

```
# slam 애니메이션 0.7초 Timeline 키프레임
damage{a=10} @EntitiesInRadius{r=4}
throw{v=1.2;vy=0.7} @EntitiesInRadius{r=4}
particlering{p=explosion;r=3;points=10} @selflocation
```

## 몹 설정 (`mobs/*.yml`)

```yaml
StoneGolem:
  Type: ZOMBIE            # 히트박스/AI 용 바닐라 몹 (모델이 있으면 투명 처리)
  Display: '&7돌 골렘'
  Health: 150
  Damage: 8
  Armor: 4
  MovementSpeed: 0.22
  KnockbackResistance: 0.8
  FollowRange: 24
  HitboxScale: 1.0        # 바닐라 히트박스 크기 배율 (scale 속성)
  Model:
    Id: golem             # models/golem.bbmodel
    Scale: 1.0
  Animations:             # 상태 → 애니메이션 이름
    walk: walk
  Options:
    Silent: true
    PreventSunburn: true
    Despawn: false
    ShowNameplate: false
    PreventOtherDrops: false
  Drops:
  - cobblestone 2-5       # 아이템 수량 [확률]
  - iron_ingot 1-2 0.5
  - exp 20
  Skills:
  - skill{s=GolemSlam} @target ~onTimer:100 ?~distance{max=5}
```

모델을 스킬로 붙일 수도 있습니다: `- model{mid=golem} @self ~onSpawn`

### 보스바 (`BossBar`)

```yaml
  BossBar:
    Enabled: true          # 섹션이 있으면 기본 true
    Title: '&c&l<caster.name>'   # 기본값 Display. <caster.hp>, <caster.mhp>, <target.name> 등 사용 가능
    Color: RED             # PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE
    Style: SEGMENTED_10    # SOLID, SEGMENTED_6, SEGMENTED_10, SEGMENTED_12, SEGMENTED_20
    Range: 48              # 이 거리 안의 플레이어에게 표시
    DarkenSky: false
    PlayMusic: false       # 보스 음악
    CreateFog: false
```

### 양식 상속 (`Template`)

`Template: 이름` 으로 다른 몹 설정을 물려받습니다 (`Template: A,B` 처럼 여러 개, 뒤가 우선). 다른 파일에 있어도 됩니다.

- 값(`Health`, `Display` ...)은 몹 쪽이 덮어쓰고, 섹션(`Options`, `BossBar`, `Model` ...)은 키 단위로 합쳐집니다.
- `Skills`, `Drops` 목록은 양식 뒤에 몹의 목록이 이어 붙습니다.
- `Abstract: true` 인 양식은 상속 전용이라 소환/목록에 나오지 않습니다. (`Abstract` 는 상속되지 않습니다)

### 보스 양식

`mobs/boss_templates.yml` 에 등급별 양식이 들어 있고, 공통 연출 스킬은 `skills/boss_skills.yml` 에 있습니다.

| 양식 | 체력 | 보스바 | 페이즈 | 알림 | 특징 |
| --- | --- | --- | --- | --- | --- |
| `MidBoss` 중간 보스 | 400 | 노랑, 6칸, 32블록 | 50% 흥분 | 주변 32블록 | |
| `Boss` 보스 | 1500 | 빨강, 10칸, 48블록, 하늘 어둡게, 보스 음악 | 66% / 33% | 주변 48블록 타이틀 | 등장·페이즈 전환 시 잠깐 무적 |
| `FieldBoss` 필드 보스 | 6000 | 보라, 20칸, 96블록, 안개 | 50% / 20% 광폭화 | 월드 전체 (좌표 포함) | 싸우는 상대가 없으면 체력 회복 |

```yaml
GolemKing:
  Template: Boss          # 체력/보스바/페이즈/알림/보상은 양식에서
  Type: ZOMBIE
  Display: '&c골렘 왕'
  Model:
    Id: golem
    Scale: 2.0
  Drops:                  # 양식의 exp 300 뒤에 추가
  - diamond 1-3
  Skills:                 # 양식의 페이즈 스킬 뒤에 추가
  - skill{s=GolemSlam} @target ~onTimer:80 ?~distance{max=6}
```

예시: `mobs/example_bosses.yml` – `GolemCaptain`(중간 보스), `GolemKing`(보스), `AncientGolem`(필드 보스)

## 스킬 (`skills/*.yml`)

```yaml
GolemRock:
  Cooldown: 4                 # 초
  Conditions:                 # 시전자 조건
  - hastarget
  TargetConditions:           # 상속된 타겟 조건
  - isplayer
  Skills:
  - animation{a=attack} @self
  - delay 5                   # 틱
  - projectile{onTick=Rock_Tick;onHit=Rock_Hit;v=18;hr=1;md=30}
```

라인 문법: `메카닉{키=값;키=값} @타겟터{키=값} ~트리거 ?조건 ?~타겟조건 ?!부정조건 확률`

- 타겟터가 없는 라인은 부모 `skill{}` 에서 **상속된 타겟**을 쓰고, 없으면 메카닉 기본 타겟터를 씁니다.
- 모델 메카닉(`animation`, `tint` 등)은 상속 타겟을 무시하고 시전자에게 적용됩니다.
- `damage`, `ignite`, `throw`, `pull`, 해로운 `potion` 은 시전자의 아군(주인·소환수)을 건너뜁니다.
- `skill{}`/`repeat{}` 을 위치 하나로 부르면 그 위치가 불린 스킬의 `@Origin` 이 됩니다.
- 플레이스홀더: `<caster.name>`, `<target.name>`, `<trigger.name>`, `<caster.hp>`, `<caster.mhp>`, `<target.x>` ...
- 색 코드: `&c`, `&l` ...

### 메카닉

| 메카닉 (별칭) | 옵션 | 기본 타겟 |
| --- | --- | --- |
| `damage` (`d`) | `amount/a`, `ignorearmor/ia` | @target |
| `heal` | `amount/a` | @self |
| `ignite` | `ticks/t` | @target |
| `potion` | `type/t`, `duration/d`(틱), `level/l`(1부터), `particles`, `icon` | @self |
| `lightning` / `effect:lightning` | – (effect 는 연출만) | @target |
| `summon` | `type/t`(바닐라 또는 BlueMoon 몹), `amount/a`, `radius/r`, `owner`(기본 true), `duration`, `max` | @selflocation |
| `command` (`cmd`) | `c` (콘솔 실행, 플레이스홀더 가능) | @self |
| `remove` | – | @self |
| `particle` (`effect:particle`, `e:p`) | `p`, `a`, `hs`, `vs`, `s`, `y`, `color`, `size`, `m`(블록 파티클 재질) | @self |
| `particlering` (`e:pr`) | 위 옵션 + `r`, `points` | @self |
| `particleline` (`e:pl`) | 위 옵션 + `distance`, `fromorigin` | @self |
| `sound` (`e:s`) | `s`, `v`, `p` | @self |
| `message` (`msg`) | `m` | @trigger |
| `actionmessage` (`actionbar`) | `m` | @trigger |
| `sendtitle` (`title`) | `title`, `subtitle`, `fadein`, `stay`, `fadeout` | @trigger |
| `throw` | `v`, `vy` (블록/틱), `fromorigin` | @target |
| `pull` | `v`, `toorigin` | @target |
| `leap` (`jump`) | `vy`, `v`(최대 수평 속도) | @target |
| `lunge` | `v`, `vy` | @target |
| `velocity` | `mode=set/add/multiply`, `x`, `y`, `z` | @self |
| `teleport` (`tp`) | – | @target |
| `skill` (`metaskill`, `skill:이름`) | `s` | 상속 |
| `randomskill` | `skills=A,B,C` | 상속 |
| `repeat` | `s`, `times`, `i`(틱) — 같은 타겟/원점으로 반복 실행 | 상속 |
| `invulnerable` (`iframes`) | `ticks` — 잠시 모든 피해 무시 | @self |
| `projectile` | `onStart`, `onTick`, `onHit`, `onEnd`, `v`(블록/초), `i`, `hr`, `md`, `syo`, `tyo`, `g`, `sb`, `hp`, `hnp`, `pierce`, `model`, `anim`, `modelscale`, `homing`(0~1), `so`(옆 오프셋) | @target |
| `model` | `mid`, `scale`, `remove` | @self |
| `animation` (`anim`, `state`) | `a`, `speed`, `mode=once/loop/hold`, `fadein`, `fadeout`, `priority`, `restart` | @self |
| `stopanimation` (`stopanim`) | `a` (`*` = 전부), `fadeout` | @self |
| `tint` | `color`(hex), `duration`(틱, 0=유지) | @self |
| `bonevisibility` | `bone`, `visible` | @self |
| `modeleffect` (`vfx`) | `m`, `a`, `speed`, `mode`, `d`, `f`, `y`, `side`, `yaw`, `pitch`, `follow`, `scale`, `bright` — [자세히](skills/README.md#이펙트-모델-modeleffect-별칭-vfx) | @self |

### 타겟터

| 타겟터 | 설명 |
| --- | --- |
| `@self` `@caster` | 시전자 |
| `@target` | 몹의 현재 공격 대상 (플레이어가 시전하면 바라보는 엔티티) |
| `@trigger` | 스킬을 발동시킨 엔티티 |
| `@owner` | 소환수의 주인 |
| `@summons` | 시전자의 소환수 전부 |
| `@Crosshair{r;entities;ground}` (`@aim`) | 시전자가 조준하는 지점 (처음 맞는 엔티티/블록, 없으면 사거리 끝, `ground=true` 면 바닥으로) |
| `@PlayersInRadius{r}` `@PIR` | 반경 내 플레이어 |
| `@EntitiesInRadius{r}` `@EIR` | 반경 내 생물 (시전자 제외) |
| `@MobsInRadius{r;types}` `@MIR` | 반경 내 BlueMoon 몹 |
| `@NearestPlayer{r}` | 가장 가까운 플레이어 |
| `@PlayersInWorld` | 월드의 모든 플레이어 |
| `@EntitiesNearOrigin{r}` `@ENO`, `@PlayersNearOrigin{r}` `@PNO` | 원점(투사체 위치 등) 주변 |
| `@SelfLocation`, `@TargetLocation`, `@TriggerLocation`, `@Origin` | 위치 |
| `@Forward{f;y}` | 시전자 앞 f 블록 |
| `@Ring{r;points}` | 시전자 주변 원형 위치들 |
| `@RLNC{a;r;minr}` | 시전자 주변 무작위 위치 |
| `@ModelPart{bone}` (`@bone`, `@locator`) | 모델 본 피벗/로케이터의 현재 위치 |

### 조건

`chance{c}`, `healthpercent{min;max}`(0~1), `health{min;max}`, `hastarget`, `isplayer`, `onground`, `distance{min;max}`, `day`, `night`, `raining`, `entitytype{types}`, `mobtype{types}`, `playinganimation{a}`, `lineofsight`, `undead`, `behind{angle}`, `holding{weapon}`(BlueMoonSkills)

### 트리거

`~onSpawn`, `~onDeath`, `~onCombat`(기본값, 공격+피격), `~onAttack`, `~onDamaged`, `~onTimer:틱`, `~onInteract`, `~onTarget`, `~onKill`

## 명령어 (`/bluemoon`, `/bm`, 권한 `bluemoon.admin`)

| 명령어 | 설명 |
| --- | --- |
| `/bm reload` | 모델/스킬/몹 다시 읽기 + 리소스팩 재생성 |
| `/bm spawn <몹> [수량]` | 바라보는 블록 위에 소환 |
| `/bm list [mobs\|skills\|models]` | 목록 |
| `/bm cast <스킬>` | 자신이 시전자가 되어 스킬 실행 (테스트용) |
| `/bm anim <애니메이션>` | 가장 가까운 모델 몹에게 애니메이션 재생 |
| `/bm pack` | 리소스팩만 재생성 후 접속자에게 재전송 |
| `/bm killall` | BlueMoon 몹 전부 제거 |

## 구조

```
core/      공통 엔진 (두 플러그인 jar 에 포함)
bluemoon/  몹 플러그인 (BlueMoon)
skills/    플레이어 스킬 플러그인 (BlueMoonSkills)

core/src/main/java/dev/bluemoon
├── BlueMoonPlugin    공통 플러그인 뼈대
├── model/bbmodel     .bbmodel 파서
├── model/molang      Molang 식 계산기
├── model/animation   키프레임 샘플링, 애니메이션 레이어, 본 행렬 계산
├── model/pack        리소스팩 생성 / 무기 모델 / HTTP 배포
├── model/effect      스킬 이펙트 모델
├── model/runtime     본 블루프린트, ItemDisplay 모델 인스턴스
├── skill             스킬 라인 컴파일/실행, 타겟터, 조건
├── skill/mechanics   메카닉 구현
├── mob               몹 정의, 활성 몹, 이벤트 → 트리거
└── command           공통 명령어
```

좌표 변환: 본 행렬은 블록벤치 미리보기와 같은 방식(ZYX 오일러, 애니메이션 X/Y 회전과 X 위치 부호 반전)으로 계산하고, 아이템 디스플레이가 아이템을 Y축 180° 돌려 그리는 점을 보정합니다.
