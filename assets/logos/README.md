# Logolar

Bu klasördeki her PNG/JPG dosyası uygulamada **Logo** açılır listesinde önizlemeli olarak görünür ve
elle seçilebilir. "Otomatik" seçiliyken logo, taraflardaki grup şirketine göre `config/sirketler.json`
içindeki `logo` alanından bulunur.

| Dosya | İçerik | Otomatik eşleşme |
|---|---|---|
| `kalyon-enerji.png` | Kalyon Enerji | varsayılan logo (zorunlu) ve logosu olmayan grup şirketlerinin yedeği |
| `kalyon-isletme.png` | Kalyon İşletme | Kalyon İşletme ve Yönetim Danışmanlığı A.Ş. |
| `kalyon-insaat.png` | Kalyon İnşaat | — (yalnızca elle seçim) |
| `kalyon.png` | Kalyon (genel) | — (yalnızca elle seçim) |
| `kalyon-amblem.png` | Kalyon "K" amblemi | — (yalnızca elle seçim) |
| `kalyon-lacivert-zemin.png` | Kalyon, lacivert zemin | — (yalnızca elle seçim) |

Yeni logo eklemek için PNG dosyasını (şeffaf veya beyaz arka plan, en az 600 px genişlik) bu klasöre
koymanız yeterlidir. Bir grup şirketine otomatik bağlamak için `config/sirketler.json` içinde o şirketin
`logo` alanına dosya adını yazın.

`sirketler.json`'da adı geçip henüz dosyası olmayan logolar (bunlar `kalyon-enerji.png`'ye düşer):
kalyon-gunes.png, kalyon-yeka-res-3.png, kalyon-yeka-res-4.png, kalyon-yeka-ges-3-4.png,
kalyon-yeka-ges-5.png, karapinar-eib-yonetici.png, kalyon-ev.png, kalen.png, melet.png
