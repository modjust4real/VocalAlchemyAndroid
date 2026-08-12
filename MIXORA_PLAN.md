# Mixora Android Uygulaması — Uygulama ve Teslim Planı

## 1. Amaç

Mixora, Android üzerinde beat ve vokali analiz ederek açıklanabilir, değiştirilebilir bir mix zinciri oluşturan; kullanıcıya kendi ses profili veya referans parça karakteri üzerinden preset üretme ve canlı autotune deneme imkânı veren çevrimdışı-öncelikli bir uygulama olacaktır.

İlk teslimin hedefi yalnızca ekran maketi değil, kurulabilir APK içinde çalışan bir uçtan uca MVP sunmaktır.

## 2. İlk Açılış Akışı

Ana ekranda iki ana seçenek bulunacaktır:

1. **Beat ve vokal mixle**
2. **Preset oluştur**

Preset oluşturma ekranında:

1. **Kendi sesimden preset**
2. **Referans sanatçı/parçadan preset**
3. **Preset testi — canlı mikrofon**

## 3. MVP Özellikleri

### 3.1 Beat ve Vokal Mixleme

- Beat dosyası seçme: WAV, FLAC, MP3, M4A desteği.
- Vokal dosyası seçme veya uygulama içinde kayıt alma.
- Dosya süreleri, sample rate, kanal yapısı ve tepe seviyesi kontrolü.
- Vokal sessizlik/noise başlangıç analizi.
- Beat ve vokalin bölüm bazlı seviye, spektrum ve dinamik analizi.
- Otomatik başlangıç zinciri:
  - input gain ve headroom,
  - high-pass/low-cut ihtiyacı,
  - tonal EQ,
  - dinamik EQ/de-essing,
  - compression,
  - saturation,
  - delay/reverb,
  - vocal/beat dengesi,
  - master-bus güvenlik limiteri.
- İşlenmiş/işlenmemiş A/B dinleme.
- Kullanıcı yoğunluk kontrolü: Doğal, Dengeli, Agresif.
- Mix değişiklik komutları için çevrimdışı Türkçe komut ayrıştırıcı:
  - “vokali öne al”,
  - “daha parlak/karanlık yap”,
  - “autotune artır/azalt”,
  - “reverb azalt/artır”,
  - “bassı temizle”,
  - “vokali genişlet/daralt”.
- Yeni sürüm üretme ve önceki sürüme dönme.
- WAV olarak render/export; cihaz desteklediğinde paylaşılabilir sıkıştırılmış kopya.

### 3.2 Kendi Sesinden Preset

- Kullanıcıdan mümkünse kuru ve efektsiz 20–45 saniyelik vokal örneği isteme.
- Profil özellikleri:
  - ortalama perde ve perde aralığı,
  - spektral eğim ve formant bölgesi,
  - sibilans/plosive yoğunluğu,
  - dinamik aralık,
  - noise ve oda etkisi,
  - uygun compression/de-essing başlangıç değerleri.
- Sabit plug-in reçetesi yerine her yeni kayda yeniden uyarlanan ses profili.
- Preseti isimlendirme, kaydetme, silme ve kopyalama.

### 3.3 Referans Sanatçı/Parça Preseti

- Spotify/YouTube/Apple Music bağlantısını tanıma ve parça kimliğini gösterme.
- Yayın servislerinden korumalı sesi indirmeme veya ayırmama.
- Spotify/YouTube gibi bağlantılarda analiz için kullanıcının sahip olduğu referans ses dosyasını ayrıca seçmesini isteme.
- Doğrudan WAV/MP3/FLAC dosya bağlantısı verilirse kullanıcı onayıyla içe aktarma.
- Referanstan çıkarılacak profil:
  - tonal denge,
  - low-end dağılımı,
  - dinamik yoğunluk,
  - vokal/beat algısal oranı,
  - stereo genişlik,
  - ambience ve yakınlık,
  - loudness ve true-peak karakteri.
- Sonuç “orijinal plug-in zinciri” olarak değil, **referans stil profili** olarak adlandırılacaktır.

### 3.4 Canlı Preset ve Autotune Testi

- Mikrofon izni ve güvenli ilk kullanım açıklaması.
- Kulaklık takılması gerektiğine dair feedback uyarısı.
- Nota/ton seçimi: 12 kök nota; majör, minör ve kromatik modlar.
- Autotune miktarı ve tepki hızı kontrolleri.
- Noise gate, basit EQ, compressor, de-esser ve ambience önizlemesi.
- Cihazın desteklediği en düşük gecikmeli audio yolu.
- Bluetooth gecikmesi uyarısı; kablolu/USB kulaklık önerisi.
- Canlı test sırasında kayıt alma ve presetler arasında karşılaştırma.

## 4. Teknik Mimari

### Android katmanı

- Kotlin.
- Jetpack Compose arayüzü.
- Navigation Compose ekran akışı.
- Android Storage Access Framework ile dosya seçimi.
- Room veya yerel JSON tabanlı preset/sürüm saklama.
- WorkManager/coroutine ile uzun analiz ve render işleri.

### Ses katmanı

- Gerçek zamanlı yol: native C++ ve Oboe/AAudio.
- Tek native audio stream; uygulama içi mixing ve DSP.
- Audio callback içinde dosya/ağ işlemi, bellek tahsisi veya ağır analiz yapılmayacak.
- Dahili float işleme; cihazın native sample rate’i, çoğunlukla 48 kHz.
- Offline render motoru gerçek zamanlı zincirle aynı parametre modelini kullanacak.

### Analiz ve karar katmanı

- İlk APK’de harici OpenAI API veya ücretli servis olmayacak.
- Özellik çıkarımı ve kural/optimizasyon tabanlı adaptif DSP cihazda çalışacak.
- Ağır source separation ilk sürümde final ses yolu için zorunlu olmayacak; gerekirse sonraki sürümde arka plan/sunucu seçeneği olarak eklenecek.
- Her otomatik karar kullanıcıya okunabilir parametreler hâlinde gösterilecek.

## 5. Veri, Gizlilik ve Güvenlik

- Kullanıcı dosyaları varsayılan olarak cihazdan çıkmayacak.
- Mikrofon yalnızca kullanıcı başlattığında kullanılacak.
- Presetler ve analiz sonuçları yerel saklanacak.
- Referans bağlantıları platform politikalarına uygun kullanılacak.
- Uygulama, bir sanatçının gerçek plug-in zincirini çıkardığını iddia etmeyecek.

## 6. Ekranlar

1. Karşılama/ana seçim.
2. Beat + vokal içe aktarma.
3. Analiz ilerleme ve sorun raporu.
4. Mix çalışma alanı.
5. Değişiklik isteği ve sürüm geçmişi.
6. Export.
7. Preset yöntemi seçimi.
8. Ses profili oluşturma.
9. Referans bağlantısı/dosya analizi.
10. Preset kitaplığı.
11. Canlı preset/autotune testi.
12. Ayarlar ve cihaz gecikme testi.

## 7. Geliştirme Sırası

### Aşama A — Proje ve temel arayüz

- Android proje iskeleti, tema, navigasyon.
- Ana ekran ve bütün temel ekranların çalışan akışı.
- Dosya seçimi, mikrofon izinleri ve yerel saklama.

### Aşama B — Analiz ve offline mix

- Audio decode/encode.
- Seviye, loudness, peak, spektrum ve dynamics ölçümü.
- Adaptif vocal-chain parametre üretimi.
- Beat/vokal mix, A/B ve WAV export.
- Çevrimdışı değişiklik komutları ve sürüm geçmişi.

### Aşama C — Preset sistemleri

- Kendi sesinden profil.
- Referans stil profili.
- Preset kitaplığı ve profile göre yeni mix.

### Aşama D — Canlı test

- Oboe tabanlı düşük gecikmeli mikrofon monitoring.
- Ton seçimi ve pitch-correction çekirdeği.
- Canlı chain kontrolleri, feedback/Bluetooth uyarıları.

### Aşama E — QA ve APK

- Birim testleri: analiz, parametre sınırları, preset serileştirme, komut ayrıştırma.
- APK kurulum ve açılış testi.
- Emülatörde ana navigasyon, dosya seçimi hata akışları, izinler ve export akışı.
- Gerçek cihaz gerektiren mikrofon gecikmesi için cihaz içi teşhis ekranı.
- Crash/logcat kontrolü.
- İmzalı veya kurulabilir debug APK teslimi.

## 8. MVP Kabul Kriterleri

- APK Android cihaza kurulup açılmalı.
- Ana ekrandaki iki yol tamamıyla gezilebilir olmalı.
- Beat ve vokal dosyası seçilebilmeli.
- Uygulama dosyaları analiz edip değiştirilebilir bir başlangıç mixi üretmeli.
- En az altı Türkçe mix değişiklik komutu gerçekten parametreleri değiştirmeli.
- Kendi sesinden oluşturulan profil saklanıp yeni projede kullanılabilmeli.
- Referans bağlantısı tanınmalı ve yasal/teknik olarak gerektiğinde referans dosyası istemeli.
- Mikrofon test ekranında ton ve autotune miktarı seçilebilmeli.
- İşlenmiş sonuç WAV olarak dışa aktarılabilmeli.
- Uygulama kritik akışlarda çökmemeli ve hataları kullanıcıya açıklamalı.

## 9. Bilinen İlk Sürüm Sınırları

- Spotify/YouTube akışından korumalı sesi indirerek analiz yapılmayacak.
- Her Android cihazda aynı canlı monitoring gecikmesi garanti edilemez.
- Bluetooth kulaklıkla autotune monitoring gecikmeli olacaktır.
- İlk pitch-correction motoru stüdyo kalitesindeki ticari Auto-Tune ürünlerinin bütün algoritmalarını birebir kopyalamayacaktır; işlevsel ve geliştirilebilir bir çekirdek olacaktır.
- Tam otomatik mix çıktısı “son söz” değil, düzenlenebilir başlangıç mixidir.

## 10. Teslimler

- Bu plan dosyası.
- Tam kaynak kodu.
- Derleme talimatları ve teknik README.
- Test raporu.
- Kurulabilir APK.

