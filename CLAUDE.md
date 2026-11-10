# YÖBF Otomatörü — Proje Spesifikasyonu

Bu dosya Claude Code için ana talimattır. Proje, yüklenen bir hukuki belgeden (sözleşme, zeyilname, protokol, YK kararı, vekâletname, azilname vb.) Kalyon grubunun **Yönetici Özet Bilgilendirme Formu (YÖBF)** ve türevlerini otomatik dolduran, Windows'ta **kurulum gerektirmeden** çalışan bir Java masaüstü uygulamasıdır.

Referans formlar: `ornekler/` klasöründeki üç PDF (toplam 14 form). Üretilen çıktı bu örneklerle görsel ve üslup olarak birebir uyumlu olmalıdır.

---

## 1. Teknoloji yığını

| Katman | Seçim | Not |
|---|---|---|
| Dil / JDK | Java 21 (LTS) | |
| Derleme | Maven | |
| Arayüz | Swing + FlatLaf | jlink ile küçük imaj, ek runtime yok |
| PDF okuma | Apache PDFBox 3.x | Metin katmanı yoksa kullanıcıya "taranmış belge" uyarısı ver (OCR v1'de kapsam dışı) |
| Word okuma/yazma | Apache POI 5.x (XWPF) | .docx girdi + çıktı |
| Yapay zekâ | Anthropic resmi Java SDK (`com.anthropic:anthropic-java`) | Model adı `config/ayarlar.json` içinden okunur, koda gömülmez |
| JSON | Jackson | |
| Paketleme | `jlink` + `jpackage --type app-image` | Çıktı: içinde runtime ve `.exe` bulunan klasör → zip. **Kurulum ve yönetici yetkisi gerektirmez.** |
| CI | GitHub Actions `windows-latest` | jpackage çapraz derleme yapmadığı için Windows paketi burada üretilir |

API anahtarı: `config/ayarlar.json` dışında, kullanıcı profilinde (`%APPDATA%\YOBF\anahtar.txt`) veya `ANTHROPIC_API_KEY` ortam değişkeninde tutulur. Asla koda veya repoya yazılmaz. Kurumsal proxy ayarı (`https.proxyHost/Port`) ayarlar dosyasından okunabilmelidir.

---

## 2. Akış

1. Kullanıcı belgeyi sürükle-bırak ile ekler (PDF / DOCX; birden fazla belge = tek forma bağlanan birden fazla sözleşme, bkz. §5.3).
2. Metin çıkarılır → **maskeleme** uygulanır (TCKN, IBAN, telefon, e-posta → `[TCKN]` vb.; maskelenen değerler yerel olarak saklanır, modele gitmez).
3. Model çağrısı (§6) → yapılandırılmış JSON döner.
4. **Doğrulama katmanı** (§7) çalışır, uyarılar ekranda sarı/kırmızı işaretlenir.
5. **Düzenleme ekranı:** Tüm alanlar düzenlenebilir; Konu alanının yanında canlı karakter sayacı (`312 / 400`).
6. Kullanıcı onayı → `.docx` üretilir (çıktı adı: `YOBF_<BelgeTürü>_<KarşıTaraf>_<GGAAYYYY>.docx`).
7. Her işlem `kayit/islem_gunlugu.csv` dosyasına bir satır olarak eklenir (tarih, belge adı, form türü, taraflar, bedel, kullanıcı).

Kural: Program hiçbir alanı kullanıcı onayı olmadan nihai çıktıya yazmaz.

---

## 3. Form türleri

`config/ayarlar.json → form_turleri` listesinden seçilir; belge türüne göre otomatik önerilir.

| Kod | Başlık | Belgenin Konusu bloğu |
|---|---|---|
| `YOBF` | YÖNETİCİ ÖZET BİLGİLENDİRME FORMU | Taraflar + Konu + (Bedel) + (Not) |
| `YOBF_TR_EN` | YÖNETİCİ ÖZET BİLGİLENDİRME FORMU / EXECUTIVE SUMMARY INFORMATION FORM | Tüm etiketler TR/EN; Konu iki dilde (önce TR, sonra EN) |
| `AZILNAME` | AZİLNAME ONAY FORMU | Azledilecek Kişiler, Azleden Şirketler, Konu, (Not) |
| `VEKALETNAME` | VEKALETNAME ONAY FORMU | Vekaletname Verilecek Kişiler, Vekaletname Veren Şirket, Sona Erme Tarihi, Konu, (Not) |

Ortak üst alanlar (tüm türlerde): Tarih, Belge Türü, Belgeyi Talep Eden Departman, Belgeyi İmzaya Sunan Departman.
Opsiyonel alan (tüm türlerde): **İmzasına Sunulacak Yetkili** (bir veya birden fazla satır, her satıra parantez içinde açıklama eklenebilir: `Sn. Murathan KALYONCU (Kalyon İşletme için)`).

---

## 4. Logo kuralı (form oluşturulurken ilk çalışan kural)

Logolar `assets/logos/<sirket_id>.png` olarak uygulama klasöründe durur. Şirket kayıtları `config/sirketler.json` içindedir.

Çözümleme sırası:
1. Kullanıcı arayüzde logoyu elle seçtiyse → o logo.
2. Taraflar (veya Azleden / Vekaletname Veren Şirket) içinden **grup şirketleri** tespit edilir: her taraf adı normalize edilir (büyük/küçük harf, Türkçe karakter, `Tic.`↔`Ticaret`, `San.`↔`Sanayi`, `Ltd. Şti.`, `A.Ş.` varyasyonları, fazla boşluk/nokta) ve `sirketler.json` içindeki `unvan` + `takma_adlar` ile eşleştirilir.
3. Tek grup şirketi bulunduysa → `logo` alanındaki dosya.
4. Birden fazla grup şirketi bulunduysa (ör. O&M: YEKA GES 5 + Kalyon İşletme; azilnamede 4–7 şirket) → farklı logolar çıkıyorsa kullanıcıya seçim sorulur, ilk eşleşen ön seçili gelir. Hepsi aynı logoya düşüyorsa sorulmaz.
5. Logo dosyası yoksa → `logo_yedek` zinciri izlenir → son çare `varsayilan_logo` (`kalyon-enerji.png`).
6. Hiç grup şirketi bulunamadıysa → varsayılan logo + uyarı ("Taraflarda grup şirketi tespit edilemedi").

Logo, başlığın üstünde ortalanır; yükseklik sabit (≈ 2,2 cm), en-boy oranı korunur.

---

## 5. İçerik kuralları

### 5.1 Taraflar
- Tam ticaret unvanı belgede geçtiği şekliyle alınır; grup şirketi `sirketler.json`'da kayıtlıysa **kanonik unvan** yazılır (örneklerde aynı şirket hem "Ticaret A.Ş." hem "Tic. A.Ş." olarak geçiyor; bu tutarsızlık giderilmelidir).
- Sıralama: grup şirket(ler)i önce, karşı taraf(lar) sonra.
- Format: `1) …`, `2) …` (numaralı, girintili). Kısaltma belgede tanımlıysa parantez içinde korunur: `UK Grid Solutions Limited (GE UK)`.
- Gerçek kişi taraflarda unvan korunur: `Av. Hamdi Bildirici`.
- Taraf sayısı sınırsızdır (örnekte 3 taraflı VO var).

### 5.2 Konu (özet)
- **Sınır:** varsayılan **400 karakter**, yalnızca harf ve rakam sayılır (boşluk ve noktalama hariç). Sayım deterministik olarak kodda yapılır: `\p{L}` ve `\p{N}` eşleşmeleri.
- Sınır aşılırsa model en fazla 2 kez "kısalt" talimatıyla yeniden çağrılır; hâlâ aşıyorsa kullanıcıya kırmızı uyarıyla gösterilir.
- **Dikkat:** Mevcut örneklerin 3'ü bu sınırı aşıyor (Efectis 482, Aydıntepe 466, Green Diamond 546). Sınırın sert mi yumuşak mı olduğu `ayarlar.json → konu_sinir_sert` ile belirlenir.
- Üslup (örneklerden çıkarılmıştır, `prompts/ozet_prompt.md` içinde örneklerle verilmiştir):
  - Edilgen yapı; "şirketimiz", "projemiz", "iştirakimiz" kullanımı.
  - Bağlam önce (proje adı, dayanak sözleşme/protokol ve tarihi), işlem sonra.
  - Kapanış kalıbı: "… amacıyla hazırlanan [belge adı] onay ve imzanıza sunulmaktadır." Alternatifler: "… ekte onay ve imzanıza arz edilmektedir.", "… onay ve imzanıza sunulmasından ibarettir."
  - Tarihler `GG.AA.YYYY`.
  - Bedel Konu metnine yazılmaz; ayrı alana gider.
  - Belgede olmayan hiçbir bilgi eklenmez.

### 5.3 Bedel
- Ayrı alan; etiketi belge türüne göre: `Bedel`, `Sözleşme Bedeli`, `Zeyilnameye Konu Tutar`.
- Türk sayı biçimi: `3.570.000 TL + KDV`, `497.746,00 USD`, `145.000 EUR + KDV`. Vergi notu belgedeki gibi korunur: `(KDV ve stopaj dahil değildir.)`.
- **Çoklu sözleşme:** Birden fazla belge tek forma bağlanıyorsa Taraflar ve Bedel numaralı gruplar halinde yazılır: `1. Sözleşme Taraflar:` / `2. Sözleşme Taraflar:` ve `1. Sözleşme Bedeli:` / `2. Sözleşme Bedeli:`; Konu tek ve ortak olur (Geocell örneği).

### 5.4 Not
- Opsiyonel; etiket seçilebilir: `Not:` veya `Müşavirliğimizin Notu:`. İtalik.
- Model bu alanı **doldurmaz**; yalnızca ayrı bir panelde "dikkat edilmesi önerilen hususlar" listesi önerebilir (ödeme koşulu, süre, cezai şart, fesih, sorumluluk sınırı, uygulanacak hukuk). Not metnini hukukçu yazar.

### 5.5 Vekaletname / Azilname alanları
- Kişi listeleri virgülle tek satırda (`Eren Taşkıran, Beyhan Ülker, …`).
- Şirket listeleri alt alta, her satırda bir şirket, kanonik unvanla.
- Sona Erme Tarihi `GG.AA.YYYY`; geçmiş bir tarihse uyarı.

---

## 6. İmzacı yapısı (1–5 + 1)

Form altındaki **UYGUNDUR** bloğu:

- **Ana imzacılar: 1 ile 5 arası slot.** Her slot: Ad SOYAD (kalın) + unvan (italik, alt satır) + imza çizgisi.
  - Yerleşim: 1–3 imzacı → tek satır, eşit aralıklı; 4 → 2+2; 5 → 3+2 (alt satır ortalanır).
  - Sıralama kullanıcı tarafından sürükle-bırak ile değiştirilebilir.
- **+1 Ek onay / uygunluk bloğu (opsiyonel, açılır-kapanır).** Ana bloğun altında ayrı bir başlıkla (varsayılan "ONAY", `ayarlar.json`'dan değiştirilebilir) tek imzacı, ortalanmış.
- İmzacılar `config/imzacilar.json` listesinden açılır menüyle seçilir; listede olmayan kişi elle girilebilir.
- **Departman ön ayarları:** "Talep Eden Departman" seçildiğinde varsayılan imzacılar otomatik dolar (`ayarlar.json → departman_on_ayarlari`). Ör. HESS-Q → [Murat YAĞCI, Av. Gökhan SÜMER]; İş Geliştirme → [Av. Gökhan SÜMER, Beyhan ÜLKER].
- Unvanlar her zaman `imzacilar.json`'daki kanonik halinden gelir (örneklerde aynı kişi için "Kıdemli HESS-Q Müdürü" / "HESS-Q Kıdemli Müdürü" ve "Kalyon Enerji GMY - Operasyonlar" / "Genel Müdür Yardımcısı – Operasyonlar" tutarsızlıkları var).
- `YOBF_TR_EN` türünde unvanlar iki dilli yazılır (`unvan_en` alanı).
- İmzacı slotu ile "İmzasına Sunulacak Yetkili" alanı farklıdır; karıştırılmaz.

---

## 7. Model çağrısı

- Sistem talimatı: `prompts/ozet_prompt.md` (dosyadan okunur; uygulamayı yeniden derlemeden düzenlenebilir).
- Yapılandırılmış çıktı için tool use / JSON şeması zorunlu tutulur. Dönen şema:

```json
{
  "belge_turu": "Sözleşme | Zeyilname | Protokol | Gizlilik Sözleşmesi | Variation Order | Yönetim Kurulu Kararı | Vekaletname | Azilname | Avukatlık Ücret Sözleşmesi | Diğer",
  "form_turu_onerisi": "YOBF | YOBF_TR_EN | AZILNAME | VEKALETNAME",
  "taraflar": [{"unvan": "", "rol": "", "belgedeki_ifade": ""}],
  "konu_tr": "",
  "konu_en": "",
  "bedel": [{"etiket": "", "tutar": "", "para_birimi": "", "vergi_notu": ""}],
  "kisiler": [],
  "sona_erme_tarihi": "",
  "dikkat_hususlari": [""],
  "guven": {"taraflar": 0.0, "bedel": 0.0}
}
```

- Belge çok uzunsa (ör. > 150 sayfa) önce ilk sayfalar, tanımlar, bedel ve imza bölümleri gönderilir.

## 8. Doğrulama katmanı (kodda, modelden bağımsız)

1. Her taraf unvanı (normalize edilerek) kaynak metinde geçiyor mu? Geçmiyorsa kırmızı uyarı.
2. Bedeldeki rakam kaynak metinde geçiyor mu? (Türk/İngiliz sayı biçimleri ikisi de denenir.)
3. Tarih biçimi `GG.AA.YYYY` mi ve geçerli bir tarih mi? (Örneklerde `0110.2026` yazım hatası var — bu tür hata engellenmelidir.)
4. Konu karakter sayımı ≤ sınır.
5. En az bir grup şirketi taraf mı?
6. En az 1 ana imzacı seçili mi?

## 9. Çıktı biçimi (.docx)

- A4, dar kenar boşlukları; tüm form kalın siyah çerçeve içinde (örneklerdeki gibi).
- Yazı tipi: Cambria (başlık ve etiketler kalın), gövde 11 pt, açıklama satırları 9 pt italik.
- İki sütunlu tablo: etiket sütunu ≈ %28, `:` ayracı, değer sütunu; satır ayırıcı ince yatay çizgiler.
- Konu metni iki yana yaslı.
- Alt alan etiketleri (`Taraflar:`, `Konu:`, `Bedel:`) kalın + altı çizili.
- PDF çıktısı v1 kapsamı dışındadır (kullanıcı Word'den PDF alır); v2 için değerlendirilecek.

## 10. Proje yapısı

```
yobf-otomator/
├─ CLAUDE.md
├─ pom.xml
├─ config/ (sirketler.json, imzacilar.json, ayarlar.json)   ← uygulama klasöründe, derleme dışı, düzenlenebilir
├─ prompts/ozet_prompt.md                                   ← uygulama klasöründe, düzenlenebilir
├─ assets/logos/*.png
├─ ornekler/*.pdf                                            ← referans + test girdisi
└─ src/main/java/legal/erk/yobf/
   ├─ App.java
   ├─ ui/        (AnaPencere, DuzenlemePaneli, ImzaciPaneli)
   ├─ extract/   (PdfMetin, DocxMetin, Maskeleme)
   ├─ ai/        (ClaudeIstemci, OzetSemasi)
   ├─ rules/     (LogoCozumleyici, UnvanNormalizer, KarakterSayaci, Dogrulayici)
   ├─ render/    (FormYazici — POI)
   └─ log/       (IslemGunlugu)
```

## 11. Testler ve kabul kriterleri

- `KarakterSayaci`: Türkçe karakterler (ç, ğ, ı, İ, ö, ş, ü, â) doğru sayılır; örnek metinler için beklenen değerler: Venüs 194, GE VO 213, O&M 215, Geocell 273, ADCB 279, Melet 313, Aydıntepe 466, Efectis 482, Green Diamond 546.
- `UnvanNormalizer`: "Kalen Elektrik Toptan Satış ve Tic. A.Ş." ile "Kalen Elektrik Toptan Satış ve Ticaret A.Ş." aynı kayda eşleşir.
- `LogoCozumleyici`: tek grup şirketi, çoklu grup şirketi (aynı/farklı logo), grup şirketi yok, logo dosyası eksik senaryoları.
- `FormYazici`: 1, 2, 3, 4, 5 imzacı ve +1 ek onay kombinasyonlarıyla .docx üretimi; çıktı görsel olarak `ornekler/` ile karşılaştırılır.
- Uçtan uca: `ornekler/` içindeki her form türü için asıl sözleşme verildiğinde aynı taraflar ve bedel yakalanmalıdır (asıl sözleşmeler kullanıcı tarafından `test-belgeler/` klasörüne eklenecek; bu klasör `.gitignore`'da olmalı).

## 12. Geliştirme sırası

1. Maven iskeleti + `KarakterSayaci`, `UnvanNormalizer`, `LogoCozumleyici` + testleri (modelsiz).
2. `FormYazici`: sabit veriyle örneklerin birebir kopyasını üret (önce görünüm doğru olsun).
3. Metin çıkarma + maskeleme.
4. Model entegrasyonu + doğrulama.
5. Swing arayüzü.
6. jlink/jpackage betiği + GitHub Actions iş akışı.

## 13. Yapılmaması gerekenler

- API anahtarını, gerçek sözleşme metinlerini veya kişisel verileri repoya koyma.
- Not alanını otomatik doldurma.
- Belgede olmayan bilgiyi (tarih, tutar, unvan) tamamlama.
- Kanonik unvan/unvan listelerini koda gömme — her zaman `config/` dosyalarından oku.
