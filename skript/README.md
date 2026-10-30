# Çekiliş (Skript)

`cekilis.sk` — sadece OP'lerin başlatabildiği, 10 saniyelik geri sayımlı çekiliş.

```
/cekilis <isim>   → geri sayım başlar, sonunda rastgele bir oyuncu "Kazanan:" olarak duyurulur
/cekilis iptal    → devam eden çekilişi iptal eder
/cekilis admin    → admin modu aç/kapat (açık: çekilişi sadece OP'ler görür, kapalı: herkes)
```

## Gereksinimler

- Paper veya Folia 1.20+ (1.21.11 hedeflendi)
- Skript (Folia'da Folia destekli Skript sürümü/fork'u)
- [skript-reflect](https://github.com/SkriptLang/skript-reflect)

## Kurulum

`cekilis.sk` dosyasını `plugins/Skript/scripts/` içine at, `/sk reload cekilis`.

Admin modu sunucu yeniden başlasa da korunur. Açıkken kazanan yine tüm
oyuncular arasından seçilir; OP olmayan kazanana özel mesaj gitmez.

## Ayarlar (`options:`)

| Ayar          | Varsayılan | Açıklama                          |
|---------------|------------|-----------------------------------|
| `sure`        | `10`       | Geri sayım (saniye)               |
| `oplar-haric` | `false`    | `true` → OP'ler kazanamaz         |
| `max-isim`    | `40`       | Çekiliş isminin en fazla uzunluğu |
| `renk1/renk2` | turuncu    | Tema gradyanı (diyezsiz hex)      |

## Ödül

Ödül verilmez; `cekilisBitir` fonksiyonunun sonunda işaretli boş bir yer var.
Folia'da orası global bölge thread'inde çalıştığı için ödülü
`execute console command "..."` ile vermek güvenlidir.
