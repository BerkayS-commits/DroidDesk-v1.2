# DroidDesk 1.6

Android 7.0 (API 24) ve üzeri. Derleme hedefi API 35.

## 1.6'da düzeltilen hatalar
- **Android 10+ sürümlerinde uygulama hiç açılmıyordu.** Neden: `window.insetsController`, `setContentView`'den önce çağrılıyordu; Android 11+ sürümlerinde o anda decor view yok ve sistem içeride `NullPointerException` fırlatıyor (Android 9 ve altında bu kod yolu çalışmadığı için açılıyordu). Çağrı artık `setContentView`'den sonra ve hata yakalamalı yapılıyor.
- **Simge şekilleri değişmiyordu.** Neden: maske `drawPath` + `DST_IN` ile uygulanıyordu; Porter-Duff modları yalnızca *çizilen* pikselleri etkiler, şeklin dışı hiç silinmiyordu. Ayarlardaki önizleme düz çizim olduğu için doğru görünüyor, gerçek simgeler değişmiyordu. Artık tam boyutlu maske bitmap'i kullanılıyor; yuvarlatılmış kare, su damlası, yaprak, daire, altıgen masaüstü, görev çubuğu, çekmece, son uygulamalar ve bildirim panelinde uygulanır.
- **Fareyle üzerine gelince tepki yoktu / çok silikti.** Vurgu belirgin hale getirildi: dolgu + vurgu renginde çerçeve, büyüme efekti, el imleci, basınca küçülme. Klavyeyle gezerken odaklanan öğe çerçevelenir.

## Bildirim paneli (sağ alt)
- **Tek dokunuş = aç/kapat, uzun basma = o ayarın Android sayfası.** Kutular açıkken renkli, kapalıyken sönük görünür.
- Yeni kutular: NFC, Rahatsız etmeyin, El feneri, Otomatik döndür, Koyu mod, Sessiz, Ekran açık kalsın, Konum, Pil tasarrufu, Ekran görüntüsü, Ekranı kilitle (Wi-Fi, Bluetooth, Uçak modu, Ekran, Ses, Güç, Ayarlar ile birlikte toplam 18).
- **Cihaz bilgisi kartları:** pil (yüzde, şarj, sıcaklık), ağ, depolama, bellek, cihaz modeli.
- **Hangilerinin görüneceği** Ayarlar → Bildirimler sekmesinde tek tek seçilir (kutular ve bilgi kartları ayrı).

## Son uygulamalar
- **Uygulama önizlemesi:** Ayarlar → Görev çubuğu → "Son uygulamalarda uygulama önizlemesi". Öndeki uygulamanın küçük ekran görüntüsü alınır ve bloklarda gösterilir (Android 11+, erişilebilirlik hizmeti gerekir). Görüntüler yalnızca bellekte tutulur; kaydedilmez, gönderilmez. Varsayılan kapalıdır.

## Uygulamanın üstüne gelen görev çubuğu
- Artık ayrı bir şerit değil, **gerçek görev çubuğunun kendisi**: aynı düzen, Başlat düğmesi, arama, gezinme düğmeleri, sabitli ve etkin uygulamalar, ışıklar, tepsi simgeleri ve saat. Aynı koddan çiziliyor.
- **Geri getirme bölgesi** (Ayarlar → Fare ve tuşlar): kapalı / alt kenar / sağ alt köşe / sol alt köşe. Bir uygulama açıkken fareyi o bölgeye götürünce görev çubuğu yeniden görünür. Kısayol tuşu ve dışına dokununca kapanma aynen duruyor.

## İzinler
Yeni (hepsi isteğe bağlı): Wi-Fi/Bluetooth durumu ve değiştirme, NFC, Rahatsız etmeyin erişimi, Sistem ayarlarını değiştirme (otomatik döndürme için). İlk kullanımda ilgili sistem ekranı açılır.

## Sınırlamalar (dürüst liste)
- **Android üçüncü taraf uygulamaya bu anahtarları vermez:** Android 10+ sürümlerinde Wi-Fi, Bluetooth (Android 12+), Uçak modu, NFC ve **koyu mod** doğrudan açılıp kapatılamaz. Bu kutular **durumu gösterir**, dokununca ilgili sistem panelini açar (Wi-Fi ve NFC için hızlı panel, diğerleri için ayar sayfası). Android 9 ve altında Wi-Fi, Android 11 ve altında Bluetooth gerçekten açılıp kapanır. El feneri, Rahatsız etmeyin (izinle), Otomatik döndür (izinle), Sessiz, Ekran açık kalsın, Ekran görüntüsü ve Kilitle gerçekten çalışır. Ekran görüntüsü ve Kilitle Android 9+ ve erişilebilirlik hizmeti ister.
- **Önizleme** korumalı pencerelerde (bankacılık vb.) çalışmaz; Android 10 ve altında hiç kullanılamaz. Önizleme, uygulamayı en son önde gördüğümüz andaki görüntüdür, canlı içerik değildir.
- Daha önce belirtilen kısıtlar geçerlidir: son uygulamalar listesi Android'in gerçek görev listesi değildir, "Kapat" arka plan işlemlerini sonlandırır, pencere modu cihazın serbest pencere desteğine bağlıdır.
- Kaynak kod Android SDK ile **derlenip cihazda denenmemiştir**; Android 16 ve 17'de davranış doğrulanamadı.
