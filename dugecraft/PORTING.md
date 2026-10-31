# DugeCraft - Skript'ten tasima notlari

Kaynak script'ler (sunucudaki son halleri):

| Script | Satir | Modul | Durum |
|---|---|---|---|
| bloksinir.sk v4.0 | 1534 | `tr.dugecraft.blok` | Tasindi, CI'da derleniyor, sunucuda test bekliyor |
| sok.sk v11.62 | 5686 | `tr.dugecraft.tower` | Tasindi, CI'da derleniyor; claim modulu bitince devreye girecek (ClaimApi) |
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

### Claim modulu ilerleme (claim.sk satir araliklari)

Yazildi (derlenmedi, CI'a gonderilmedi - ClaimModule.enable/timers eksik):
- `ClaimConfig` (options 148-236), `Claim` + `ClaimStore` (1-606 veri), `Farmer` (f_keys/fiyat/isim, on load 4627-4734),
  `Persist` (variables.csv kalici degiskenler), `Session` (metadata + oyuncu degiskenleri), `util/Gui`.
- `ClaimModule` cekirdek (610-1880): sayaclar, yetkiler (canInteract/canBreakTower/canManageTower/canUseFarmer),
  log (alog/clog/flushAdminLog), limitler, enerji, sinir/mesafe, ESG fiyat, oto satis, farmerTake (item spawn govdesi),
  korumalar (protchunk Persist'te), enerji uyarisi, claim tasi + hologram, deleteClaim, placeholder koprusu.

Yazildi (2. tur): `ClaimMenus` (1879-3100), `ClaimActions` (3102-4309), `Homes` (4311-4616),
`ClaimChat` (5111-5216 + 6370-6611), `ClaimEvents` (5619-6366 + 8332-8374), `Session` alanlari.

Kalan (okunacak + yazilacak):
- 1879-2140 menuler (openMyClaimsMenu, cmItem, openClaimMenu, leave confirm) -> ClaimMenus
- 2163-2490 ciftci menuleri (fill*/open*), 2499-2617 chunk isareti, 2662-2968 harita + enerji menusu
- 2971-3101 placeholder + bolge giris actionbar, 3102-3520 bolge silme/topoloji/satin alma
- 3521-3707 satis AFK korumasi, 3708-4310 satin alma/silme/uye/devir/davet/yetki/isinlanma/enerji/patlama
- 4311-4616 homes (/sethome /home /delhome), 4620-5057 on load + zamanlayicilar, 5058-5615 join/sohbet/komutlar
- 5619-6366 olaylar (item spawn, korumalar, spawner, claimsiz yasak, tas), 6370-6611 sohbet girdileri
- 6615-7858 envanter tiklama (EN BUYUK), 7859-7985 drag/close, 7986-8040 bilgi API, 8041-8331 yukseltmeler,
  8332-8374 2x mob drop
