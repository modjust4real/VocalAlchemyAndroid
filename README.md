# Mixora Android

Mixora, beat ve vokali cihaz üzerinde analiz edip düzenlenebilir bir ilk mix hazırlayan; ses veya izinli referans kayıttan preset çıkaran ve seçilen ton/gamda canlı mikrofon testi sunan Android uygulamasıdır.

## İlk APK kapsamı

- Beat ve vokal dosyası seçme (Android Storage Access Framework)
- Cihaz üzerinde ses çözme ve 6 dakikaya kadar WAV render
- Otomatik seviye eşleme, high-pass, presence, stereo kompresyon, saturasyon, reverb, stereo genişlik ve limiter
- Pitch algılama ve ton/gama kilitlenen autotune miktarı
- Türkçe revize komutları: vokali öne/geri al, parlak/karanlık yap, autotune ve reverb artır/azalt, bassı temizle, vokali genişlet/daralt
- Kendi vokalinden analiz tabanlı preset
- Şarkı bağlantısı + kullanma izni olan yerel referans dosyadan preset
- Kayıtlı presetleri mix ve canlı testte kullanma
- Mikrofonu düşük gecikmeli izleme ve hedef nota göstergesi
- 44.1 kHz / 16-bit stereo WAV dışa aktarma

## Gizlilik ve telif yaklaşımı

Ses çözme, analiz, preset ve mix işlemleri cihazda çalışır. Uygulama YouTube, Spotify veya başka platformlardan telifli ses indirmez. Şarkı bağlantısı yalnızca referansı tanımlamak için kullanılır; analiz için kullanıcının sahip olduğu ya da kullanma izni bulunan ses dosyası seçilir.

## Derleme

GitHub Actions her `main` güncellemesinde Java 17, Android SDK 35 ve Gradle 8.7 ile birim testlerini çalıştırır, ardından `app-debug.apk` üretir.

Yerel ortamda Android SDK varsa:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Teknik notlar

- Minimum Android: 8.0 (API 26)
- Hedef Android: API 35
- Arayüz: Kotlin + Jetpack Compose
- Ses motoru: Android `MediaExtractor` / `MediaCodec`, `AudioRecord` / `AudioTrack`, yorumlanabilir Kotlin DSP
- Canlı testte kulaklık gerekir. Bluetooth kulaklık işletim sistemi kaynaklı belirgin gecikme ekleyebilir.
