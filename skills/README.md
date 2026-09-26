# BlueMoonSkills

플레이어가 쓰는 **미스틱몹 문법 스킬**을 블록벤치 모델과 함께 만드는 Paper 1.21.4+ 플러그인입니다.
몹 플러그인(BlueMoon) 없이 단독으로 동작합니다. 둘 다 설치해도 서로 영향을 주지 않습니다.

블록벤치 모델을 쓰는 곳은 세 가지입니다.

| 용도 | 폴더 | 설명 |
| --- | --- | --- |
| 이펙트 모델 | `models/` | 검기·마법진·투사체. 애니메이션 **Timeline 키프레임에 적은 스킬 라인이 그 순간 실행**됩니다 |
| 소환수 | `models/` + `summons/` | 주인을 따라다니고, 주인이 싸우는 대상을 공격하고, 지속시간이 끝나면 사라집니다 |
| 무기 모델 | `weapons/` | 손에 드는 아이템 모델 (애니메이션 없음) |

## 설치

1. `BlueMoonSkills-<버전>.jar` 를 `plugins/` 에 넣고 서버를 켭니다. 예제가 설치됩니다.
2. `plugins/BlueMoonSkills/resourcepack.zip` 을 적용합니다.
   `config.yml` 의 `resource-pack.server.enabled: true` 로 내장 서버에서 자동 전송할 수도 있습니다.
3. 테스트: `/bms cast MoonSlash`, `/bms cast Fireball`, `/bms cast SummonWolf`, `/bms weapon moon_sword`

## 스킬 발동 (직접 연결)

발동 방식(키, 아이템, GUI 등)은 정하지 않았습니다. 아래 셋 중 편한 것으로 연결하세요.

**명령어**: 콘솔이나 다른 플러그인에서 실행해도 됩니다.
```
/bms cast <스킬> <플레이어> [-s]      # -s: 결과 메시지 숨김
```

**자바 API**: `plugin.yml` 에 `depend: [BlueMoonSkills]` 를 넣고 사용합니다.
```java
BlueMoonSkills api = BlueMoonSkills.get();
CastResult result = api.cast(player, "Fireball");     // SUCCESS, COOLDOWN, CONDITIONS, CANCELLED, UNKNOWN_SKILL
double left = api.cooldown(player, "Fireball");       // 남은 쿨타임(초)
List<ActiveMob> pets = api.summons(player);
ItemStack sword = api.weapons().item("moon_sword", Material.NETHERITE_SWORD);
String weaponId = api.weapons().weaponOf(itemInHand); // 무기 아이템이면 id, 아니면 null
```

**이벤트**: 마나, 직업 확인 같은 조건은 이벤트를 취소해서 막으면 됩니다. 쿨타임 검사가 먼저 이루어지고, 취소된 발동은 쿨타임이 돌지 않습니다.
```java
@EventHandler
public void onCast(PlayerSkillCastEvent e) {
    if (!hasMana(e.getPlayer(), e.getSkill())) e.setCancelled(true);
}
```

## 스킬 작성 (`skills/*.yml`)

문법은 BlueMoon과 같습니다: `메카닉{옵션} @타겟터 ~트리거 ?조건 확률`.
플레이어에게는 보통 `@target` 이 없으니 **`@crosshair` (조준점)** 을 쓰세요.

```yaml
MoonSlash:
  Cooldown: 1.5
  Skills:
  - modeleffect{m=slash;a=swing;f=1.4;y=1.1}
  - sound{s=entity.player.attack.sweep;p=1.3} @self
```

### 이펙트 모델 `modeleffect` (별칭 `vfx`)

| 옵션 | 설명 |
| --- | --- |
| `m` | 모델 이름 (`models/<이름>.bbmodel`) |
| `a` | 재생할 애니메이션 |
| `speed`, `mode=once/loop/hold` | 재생 속도 / 반복 방식 |
| `d` | 지속시간(틱). 생략하면 애니메이션 길이만큼 유지 |
| `f`, `y`, `side` | 시전자가 보는 방향 기준 앞/위/오른쪽 오프셋(블록) |
| `yaw=caster/target/none/숫자`, `yawoffset` | 모델이 바라볼 방향 |
| `pitch=true` | 시전자의 위아래 시선까지 따라감 |
| `follow=true` | 대상 엔티티를 따라다님 (`@self` 와 함께 쓰면 몸에 붙는 이펙트) |
| `scale` | 크기 |
| `bright=false` | 끄면 주변 밝기를 받음 (기본은 항상 밝게) |

이펙트의 타임라인 스크립트는 **시전자(플레이어)가 쓴 스킬**로 실행됩니다.
- `@Origin` 은 이펙트 위치입니다. 예: `damage{a=8} @EntitiesNearOrigin{r=3}`
- `@ModelPart{bone=tip}` 은 이펙트 모델의 본/로케이터 위치입니다.

### 투사체 모델

`projectile{model=fireball;anim=spin;modelscale=1;...}` 로 투사체에 모델을 붙입니다. 모델은 날아가는 방향을 바라봅니다(모델 앞면 = 블록벤치 North).

### 소환수 (`summons/*.yml` + `summon` 메카닉)

```yaml
SpiritWolf:
  Type: WOLF
  Health: 30
  Damage: 5
  Model:
    Id: spirit_wolf
  Summon:
    Duration: 600         # 틱, 0 = 주인이 접속해 있는 동안
    FollowOwner: true
    FollowDistance: 5
    TeleportDistance: 24
    AssistOwner: true     # 주인이 때리거나 맞은 상대를 공격
  Skills:
  - message{m="&b늑대 등장"} @owner ~onSpawn
```

- `summon{type=SpiritWolf;max=2;duration=600} @forward{f=2}`
  - `max`: 시전자당 최대 수. 넘으면 오래된 것부터 사라집니다.
  - `owner=false`: 주인 없는 일반 몹으로 소환합니다.
- 주인과 소환수, 같은 주인의 소환수끼리는 서로 공격하지 않습니다. 스킬의 `damage`/`ignite`/`throw`/`pull` 도 아군은 건너뜁니다.
- 소환수는 월드에 저장되지 않습니다. 주인이 나가거나 죽거나 지속시간이 끝나면 `despawn` 애니메이션(없으면 `death`)을 재생하고 사라집니다.
- 타겟터: `@owner` (소환수 → 주인), `@summons` (시전자의 소환수 전부)
- `/bms dismiss [플레이어]` 로 소환수를 해제합니다.

### 무기 모델 (`weapons/*.bbmodel`)

- 블록벤치 **Java Block/Item** 형식으로 만들고, Display 탭에서 손에 쥔 모양을 맞추세요. 설정이 없으면 바닐라 도구 자세를 씁니다.
- 그룹 회전은 큐브에 합쳐집니다. 최종 회전은 **한 축, 22.5° 단위(±45°)** 만 가능하고, 나머지는 경고와 함께 빠집니다.
- 아이템 모델 ID: `bmskills:weapon/<이름>`
  - `/bms weapon <이름> [플레이어] [재질]` 로 받을 수 있습니다.
  - 다른 플러그인에서 `item_model` 컴포넌트로 직접 지정해도 됩니다.
- 지급된 아이템에는 `bmskills:weapon` 태그로 무기 이름이 저장됩니다. 무기별 스킬 발동에 활용하세요.

## 명령어 (`/bmskills`, `/bms`, 권한 `bmskills.admin`)

| 명령어 | 설명 |
| --- | --- |
| `/bms cast <스킬> [플레이어] [-s]` | 스킬 사용 |
| `/bms weapon <무기> [플레이어] [재질]` | 무기 아이템 지급 |
| `/bms dismiss [플레이어]` | 소환수 해제 |
| `/bms list [skills\|models\|mobs]` | 목록 (`mobs` = 소환수 종류) |
| `/bms reload` | 전체 다시 불러오기 + 리소스팩 재생성 |
| `/bms pack` | 리소스팩만 재생성 후 전송 |

메카닉·타겟터·조건의 전체 목록은 [루트 README](../README.md#스킬-skillsyml) 를 참고하세요.
