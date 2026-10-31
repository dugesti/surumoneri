# DugeCraft - Skript'ten tasima notlari

Kaynak script'ler (sunucudaki son halleri):

| Script | Satir | Modul | Durum |
|---|---|---|---|
| bloksinir.sk v4.0 | 1534 | `tr.dugecraft.blok` | Tasindi, CI'da derleniyor, sunucuda test bekliyor |
| sok.sk v11.62 | 5686 | `tr.dugecraft.tower` | Yaziliyor |
| claim.sk v15.16 | 8374 | `tr.dugecraft.claim` | Bekliyor |
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
