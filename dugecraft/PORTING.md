# DugeCraft - Skript'ten tasima notlari

Kaynak script'ler (sunucudaki son halleri):

| Script | Satir | Modul | Durum |
|---|---|---|---|
| bloksinir.sk v4.0 | 1534 | `tr.dugecraft.blok` | Tasindi, CI'da derleniyor, sunucuda test bekliyor |
| sok.sk v11.62 | 5686 | `tr.dugecraft.tower` | Tasindi; claim modulu ile birlikte baslar (ClaimApi) |
| claim.sk v15.16 | 8374 | `tr.dugecraft.claim` | Tasindi, sunucuda test bekliyor |
| buyulutas.sk v2.12 | 3251 | (Skript'te kalir) | Sadece `emiciReduction` koprusu |

## Degismez kurallar

1. **Veri dosyalari ayni yol ve formatta** okunur/yazilir. Plugin kaldirilip
   script'ler geri konursa her sey kaldigi yerden devam etmeli.
   - `plugins/DugeCraft/chunklimits_v3.yml` (blok)
   - `plugins/Skript/yaml/sok_kuleleri/<fcid>.yml` + `_yedek/` (kule)
   - `plugins/Skript/yaml/claims/<cid>.yml`, `homes/<uuid>.yml`, `claim-backups/` (claim)
2. **Ayni olay oncelikleri**, ayni mesajlar (renk kodlari dahil), ayni sesler.
3. Skript'te kalan script'lerin okudugu bellek degiskenleri yazilmaya devam eder
   (`SkriptBridge`): `{-bilgi::api::*}`, `{-claim_plc::*}`, gerekirse `{-claims::*}`.
4. Eski script hala yukluyse ilgili modul BASLAMAZ (ayni dosyaya iki yazici olmasin).
5. Folia: dunya/entity islemleri sadece sahibi olan bolge thread'inde.
   Oyuncu islemleri oyuncunun scheduler'inda, disk isi async.

## Moduller arasi bagimlilik

- sok.sk, claim.sk fonksiyonlarini cagirir: canInteract, canBreakTower,
  canManageTower, isRaidActive, chunkProtectLeft, newProtectLeft,
  chunkNightLeft, fmtDur, stoneNearTowerBase, reserveChunk, releaseChunk,
  clog, alog, ltxt, altxt; degiskenler {-claims::*}, {-all_claims::*}, {-force_raid}.
- claim.sk, sok.sk verisini okur: {-towers::data::<k>::loc} (towerNearLoc),
  {-towers::combat::<uuid>} (inSokCombat), {-towers::dirtyfcids} (claim silinince).
- Bu yuzden kule ve claim modulleri BIRLIKTE devreye girer (`ClaimApi` arayuzu).
- bloksinir modulu tek basina devreye girebilir (claim verisini Skript'ten okur).

## Dis eklentiler (sunucu listesi)

Vault + EssentialsC (ekonomi), EconomyShopGUI (ciftci fiyatlari), PvPManager
(savas etiketi, v3/v4 API'si reflection ile), FancyNpcs (magaza NPC'si konsol
komutu `sokmagaza <oyuncu>` ile acar), LuckPerms (group.vip/dvip/dvip+ izinleri),
PlaceholderAPI + TAB (claim placeholder'lari {-claim_plc::*} uzerinden),
Farmer (ayri eklenti; ItemSpawnEvent dinliyor olabilir).

## Kule modulu notlari (tamamlandi)

- `TowerStore`: veri + indeksler (byCid, byFcid, byChunk 3x3, byRange menzil), flush async ve kilitli.
- `TowerModule`: motor (shockCheck/doShock), akim, isin, savas etiketi, kazma, bakim, bilgi API.
- `TowerUi`: menu, magaza, esya onarimi. `TowerListener`: tum olaylar. `TowerCommands`.
- `EmiciBridge`: once Skript `emiciReduction` (ch.njol.skript.lang.function.Functions), yoksa Java formulu.
- Esya adi/aciklamasi legacy § metin (Skript ile ayni yol). Stacking icin isSimilar.
- Komutlar DynCommands ile modul basinca kaydedilir (plugin.yml'de komut YOK).
- claim modulunun kuleye ihtiyaci: `isTowerAt` (towerNearLoc), `combatUntil` (inSokCombat),
  `markFcidDirty` (claim silinince).

## Claim modulu plani (claim.sk v15.16)

Veri modeli (`{-claims::<cid>::*}` -> `Claim` nesnesi), dosya `plugins/Skript/yaml/claims/<cid>.yml`:
- KAYIT: dosya once OKUNUR, alanlar ustune yazilir (bilinmeyen anahtarlar korunur).
  Metin alanlari Skript gibi: unset -> "<none>" (main_cid, created_text, owner_name...).
  Gecici dosya `<cid>.yml.<threadId>.ctmp`, acilista 10 sn'den eski .ctmp kurtarilir.
- Silme: dosya `claim-backups/deleted/<yyyy-MM-dd_HH-mm-ss>_<cid>.yml`'ye tasinir.
- Bozuk dosya (owner yok/"<none>"/""): `claim-backups/corrupt/<stamp>_<dosya>`.
- Ciftci depo: AtomicLong (fstock "mc|k"), oto satis alacagi kurus (fpay), 3 sn'de odeme.
- Enerji tembel hesap (energy, energy_time, expiry turetilir), kilit reserveChunk("en|mc").
- isRaidActive() her zaman true (7/24 baskin).
- Homes: `plugins/Skript/yaml/homes/<uuid>.yml`.
- variables.csv kalici degiskenler: protchunk, claim_kick_notice, claim_lastalive,
  claim_paypending, claimprot_used -> Skript'ten okunup devralinacak.

### Claim modulu dosyalari (tamamlandi)

- `ClaimConfig`, `Claim`, `ClaimStore`, `Farmer`, `Persist` (claim_kalici.yml), `Session`
- `ClaimModule`: cekirdek + acilis/kapanis + zamanlayicilar (1sn oyuncu tiki entity scheduler'da,
  1dk/3sn/5sn/5dk/15dk global, 10sn kayit turu ve admin log async)
- `ClaimMenus`, `ClaimActions`, `ClaimClicks` (tiklama/surukleme/kapanis), `Upgrades`, `ClaimChat`,
  `ClaimEvents`, `Homes`, `ClaimCommands` (/claim /claimtp /claimadmin /sethome /home /homes /delhome + async TAB)
- `ModuleFarmClaims`: bloksinir buyume urunu yere dusmeden ciftciye (ayni olum korumasi, ayni depo/oto satis)

Kalici degiskenler (variables.csv'deki {protchunk::*}, {claimprot_used::*}, {claim_kick_notice::*},
{claim_paypending::*}, {claim_lastalive}) ilk acilista Skript'ten devralinir, sonra
`plugins/DugeCraft/claim_kalici.yml`'de tutulur ve geri donus icin Skript degiskenlerine de yazilir.
