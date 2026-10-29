# SürümÖneri

Sunucunun kendi sürümünden farklı bir istemciyle giren **Java** oyuncularına
sohbette kısa ve şık bir öneri gösterir:

```
 ▍ SÜRÜM ÖNERİSİ
 ▍ En sorunsuz deneyim için 1.21.11 kullanın. (Sizinki: 1.21-1.21.1)
```

## Uyumluluk

| Çekirdek       | Durum |
|----------------|-------|
| Purpur 1.20.6  | ✅ |
| Purpur 1.21.x  | ✅ |
| Folia 1.21.11  | ✅ (`folia-supported: true`, oyuncu scheduler'ı kullanılır) |

Java 21 gerekir (1.20.6+ zaten bunu ister).

## Nasıl çalışır?

- **Sunucu sürümü** API'den okunur (`Bukkit.getMinecraftVersion()` ve protokol numarası).
  Elle yazmak istersen `config.yml` → `onerilen-surum`.
- **Oyuncu sürümü** ViaVersion API'sinden reflection ile okunur (hard bağımlılık yok,
  ViaVersion 4.x ve 5.x desteklenir). Karşılaştırma protokol numarasıyla yapılır, yani
  aynı protokolü paylaşan sürümler (örn. 1.21 / 1.21.1) gereksiz yere uyarılmaz.
- **Bedrock oyuncuları atlanır**: Floodgate API (`isFloodgatePlayer`), Geyser API
  (`isBedrockPlayer`) ve Floodgate UUID formatı (`00000000-0000-0000-…`) ile.
  İsim ön ekine **bakılmaz**.
- Mesaj, girişten `gecikme-tick` sonra oyuncunun kendi scheduler'ında bir kez gönderilir.
  Oyuncu bu sürede çıkarsa görev kendiliğinden iptal olur. Eşya/envanter işlemi
  yoktur, tekrar eden görev yoktur → dupe ya da lag riski yoktur.

## Kurulum

1. [Actions](../../actions) sekmesinden son başarılı derlemenin `SurumOneri` artefaktını indir
   (veya `mvn package` → `target/SurumOneri-1.0.0.jar`).
2. `plugins/` klasörüne at, sunucuyu yeniden başlat.
3. Sunucuda **ViaVersion** (+ isteğe bağlı ViaBackwards/ViaRewind) kurulu olmalı. ViaVersion
   yoksa farklı sürümle giriş zaten mümkün değildir; eklenti sessizce pasif kalır.

> ViaVersion yalnızca proxy'de (Velocity/BungeeCord) kuruluysa backend oyuncunun gerçek
> sürümünü göremez; bu durumda ViaVersion'ı backend sunucuya da kurun.

## Komutlar ve yetkiler

| Komut | Açıklama |
|-------|----------|
| `/surumoneri yenile` | Config'i yeniden yükler |
| `/surumoneri test` | Mesajı kendine gönderir (önizleme) |
| `/surumoneri kontrol <oyuncu>` | Oyuncunun sürümünü, Bedrock olup olmadığını ve uyarı alıp almayacağını gösterir |

| Yetki | Varsayılan | Açıklama |
|-------|------------|----------|
| `surumoneri.admin` | op | Komutu kullanma |
| `surumoneri.bypass` | yok | Uyarıyı hiç görmez |

## Ayarlar

`config.yml` içinde: `uyari-modu` (`hepsi` / `eski` / `yeni`), gecikme, ses ve
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatında mesaj.
Yer tutucular: `<sunucu_surumu>`, `<istemci_surumu>`, `<oyuncu>`.

## Neden Skript değil de plugin?

Skript + skript-reflect Folia'da güvenilir çalışmıyor (bölge thread'leri ve scheduler
farkı). Küçük bir plugin üç çekirdekte de aynı jar ile, sıfır ek bağımlılıkla çalışır.
