# YÖBF Otomatörü

Yüklenen hukuki belgeden (sözleşme, zeyilname, protokol, YK kararı, vekâletname, azilname…)
**Yönetici Özet Bilgilendirme Formu** ve türevlerini (.docx) dolduran, Windows'ta kurulum
gerektirmeden çalışan Java masaüstü uygulaması. Ayrıntılı spesifikasyon: [`CLAUDE.md`](CLAUDE.md).

## Derleme

Gereksinim: JDK 21, Maven 3.9+.

```bash
mvn verify                        # derleme + testler
java -jar target/yobf-otomator-1.0.0.jar   # repo kökünden çalıştırma (config/ burada)
```

## Paketleme (kurulumsuz)

```bash
bash packaging/paketle.sh         # jlink + jpackage --type app-image + zip → dist/
```

`jpackage` çapraz derleme yapmadığı için Windows `.exe` paketi Windows'ta üretilir:
`.github/workflows/windows-paket.yml` her push'ta `windows-latest` üzerinde
`dist/YOBF-Otomator-<sürüm>-windows.zip` dosyasını artifact olarak yükler (`v*` etiketlerinde
GitHub Release'e de ekler). Zip açılıp `YOBF-Otomator.exe` çalıştırılır; yönetici yetkisi gerekmez.

## Yapılandırma

| Dosya | İçerik |
|---|---|
| `config/ayarlar.json` | model adı, konu sınırı (sert/yumuşak), form türleri, departmanlar, imza bloğu, proxy |
| `config/sirketler.json` | grup şirketleri, takma adlar, logo eşlemesi ve yedek zinciri |
| `config/imzacilar.json` | imzacılar ve kanonik unvanlar (TR/EN) |
| `prompts/ozet_prompt.md` | modele verilen sistem talimatı |
| `assets/logos/*.png` | logolar (`kalyon-enerji.png` zorunlu varsayılan) |

Varsayılan model `claude-haiku-5-5`; `ayarlar.json → modeller` listesindeki modeller arayüzden seçilir.
Sağ panelde canlı önizleme vardır; onayda istenirse Word (yoksa LibreOffice) ile PDF de üretilir.

API anahtarı repoda tutulmaz: `ANTHROPIC_API_KEY` ortam değişkeni veya
`%APPDATA%\YOBF\anahtar.txt` (uygulamada *Ayarlar › API Anahtarı…*).

`ornekler/` ve `test-belgeler/` gerçek belgeler içerdiğinden `.gitignore`'dadır.

## Kod yapısı

```
src/main/java/legal/erk/yobf/
├─ App.java
├─ ui/       AnaPencere, DuzenlemePaneli, ImzaciPaneli, EkSatirlarPaneli, OnizlemePaneli
├─ extract/  PdfMetin, DocxMetin, Maskeleme, BelgeOkuyucu
├─ ai/       ClaudeIstemci, OzetSemasi, ApiAnahtari
├─ rules/    LogoCozumleyici, UnvanNormalizer, KarakterSayaci, Dogrulayici, FormHazirlayici, TurkSayi, TarihKontrol
├─ render/   FormYazici (Apache POI), PdfDonusturucu (Word/LibreOffice ile PDF)
├─ config/   ayarlar/şirket/imzacı kayıtları, uygulama klasörü
├─ model/    FormVerisi, SozlesmeGrubu, Bedel
└─ log/      IslemGunlugu
```
