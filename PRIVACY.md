# Xylune Privacy Policy

**Effective date: August 4, 2026**  
[Türkçe metin aşağıdadır.](#xylune-gizlilik-politikası-ve-kvkk-aydınlatma-metni)

Xylune is a local-first, bring-your-own-provider Android application maintained from Türkiye by **Ömer Faruk Nehir**. Xylune does not require a Xylune account and does not operate a Xylune-controlled backend that receives copies of chats, prompts, model responses, credentials, attachments, tool results, or cloud backups.

## Scope and responsibility boundary

This policy separates three different situations:

1. **Data kept only on the device.** Xylune can process and store data locally, but the developer does not receive, view, retrieve, or remotely delete that data.
2. **Data sent directly to a service selected by the user.** When the user connects an AI provider, cloud provider, website, WebDAV server, S3 endpoint, or another service, the app communicates directly with that service for the requested operation. The service processes the data under its own terms and privacy policy.
3. **Data voluntarily sent to the developer.** The developer may receive the content of GitHub issues, security reports, support messages, or diagnostic exports only when a user intentionally submits them.

The developer is responsible only for personal data that the developer actually receives and controls. This policy does not claim to override any mandatory legal classification or obligation.

## Data handled on the device

Xylune may store chats, prompts, model responses, drafts, attachments, memories, settings, tool results, generated content, and optional Linux-environment data on the user's device. API keys, OAuth sessions, WebDAV credentials, and S3 credentials are stored in encrypted app storage backed by Android Keystore where supported. Credentials and OAuth sessions are excluded from portable Xylune backup archives.

The official build does not automatically send analytics, advertising identifiers, conversation contents, or crash reports to a Xylune-operated service. Diagnostic data leaves the device only when the user explicitly exports or sends it.

Because the developer has no remote copy of device-only data, requests to access, correct, export, or delete that data must normally be completed on the device by the user. Clearing Xylune's Android app data or uninstalling the app removes the app's local data.

## AI providers, tools, and user-directed transfers

When a user invokes an AI provider, web research, a URL, a generated widget, a local tool that uses the network, or another external service, Xylune sends the information needed for that user-requested operation directly to the selected service. This may include prompts, conversation context, attachments, search queries, tool inputs, location when separately permitted, or generated outputs.

The developer does not operate those services, does not control their retention or training practices, and does not receive a copy merely because Xylune initiated the request. Users are responsible for selecting providers appropriate for the data they submit and for having authority to transfer that data.

## Cloud backup

A backup can include chats, attachments, settings, memories, and optional Linux-environment files selected by the user. The archive is transferred directly between the device and the selected destination:

- **Google Drive:** only the hidden `appDataFolder`, through `https://www.googleapis.com/auth/drive.appdata`.
- **Microsoft OneDrive:** only the application's OneDrive folder, through `Files.ReadWrite.AppFolder`.
- **Dropbox:** only Xylune's Dropbox App folder, using scoped account and file permissions.
- **WebDAV / Nextcloud:** only the HTTPS endpoint and folder configured by the user.
- **S3-compatible storage:** only the HTTPS endpoint, bucket, and prefix configured by the user.
- **Android document providers:** only the folder or document permission granted through Android's system picker.

Google, Microsoft, and Dropbox may return an account label, name, or email address so Xylune can display the connected account. That value and the OAuth session remain on the device. Disconnecting removes the local session or credentials but does not automatically delete backups already stored by the provider.

Xylune's use and transfer of information received from Google APIs is limited to user-requested backup, browsing, restore, and deletion functions. It is not used by the developer for advertising, profiling, sale, or model training.

## Data voluntarily sent to the developer

A GitHub issue, pull request, discussion, email, security report, or diagnostic package may contain a username, email address, device details, logs, screenshots, or any other information the sender chooses to include. The developer uses that information only to review the submission, maintain project security, diagnose a reported problem, or comply with law.

Public GitHub content is visible to others and is also handled by GitHub under GitHub's own terms. Users must not place passwords, tokens, private keys, identity documents, private conversations, or other secrets in a public issue.

The developer does not sell personal data, run behavioral advertising, or use support submissions to train models.

## Retention and deletion

- **Device-only data:** retained on the device until the user deletes it, clears app data, or uninstalls Xylune.
- **Cloud backups:** retained by the selected provider until the user deletes them through Xylune or the provider.
- **Developer-held correspondence:** retained only as reasonably needed to address the submission, maintain project history or security, resolve disputes, or comply with law. Public GitHub records may remain in GitHub's history even after editing or closure.

The developer cannot access or delete device-only data or data held solely by a user-selected provider. See the [data deletion instructions](https://omerfaruknehir.github.io/Xylune/data-deletion/).

## Contact and response times

General support, bug reports, feature requests, and ordinary correspondence are provided on a best-effort basis. **No support availability or response-time commitment is made, and a message may receive no response.**

Privacy inquiries may be initiated through the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues) without posting personal or secret information. A user who needs a private channel should request one without including sensitive details. A private contact method may also be displayed in an OAuth consent screen.

A valid request concerning personal data actually held by the developer will be handled only to the extent required by applicable law. The absence of a general support commitment does not waive mandatory legal deadlines.

## Changes

This policy may be updated when Xylune's actual data flows, connected services, or legal requirements change. The effective date above identifies the current version.

---

# Xylune Gizlilik Politikası ve KVKK Aydınlatma Metni

**Yürürlük tarihi: 4 Ağustos 2026**

Xylune, **Ömer Faruk Nehir** tarafından Türkiye'den sürdürülen, yerel öncelikli ve kullanıcının kendi sağlayıcısını bağladığı bir Android uygulamasıdır. Xylune hesabı zorunlu değildir. Xylune geliştiricisi; sohbetlerin, istemlerin, model yanıtlarının, kimlik bilgilerinin, eklerin, araç sonuçlarının veya bulut yedeklerinin kopyalarını alan Xylune kontrollü bir sunucu işletmez.

## Kapsam ve sorumluluk sınırı

Bu metin üç farklı durumu ayırır:

1. **Yalnızca cihazda kalan veriler.** Xylune bu verileri cihazda işleyip saklayabilir; geliştirici bu verileri almaz, görmez, uzaktan erişemez ve uzaktan silemez.
2. **Kullanıcının seçtiği hizmete doğrudan gönderilen veriler.** Kullanıcı bir yapay zekâ sağlayıcısı, bulut sağlayıcısı, internet sitesi, WebDAV sunucusu, S3 uç noktası veya başka bir hizmet bağladığında uygulama, talep edilen işlem için doğrudan o hizmetle iletişim kurar. İlgili hizmet kendi koşulları ve gizlilik politikası kapsamında işlem yapar.
3. **Kullanıcının geliştiriciye isteyerek gönderdiği veriler.** Geliştirici yalnızca kullanıcının bilerek gönderdiği GitHub issue içeriğini, güvenlik bildirimini, destek mesajını veya tanılama dışa aktarımını alabilir.

Geliştirici yalnızca fiilen aldığı ve kontrol ettiği kişisel veriler bakımından sorumludur. Bu ifade emredici mevzuattaki sınıflandırma veya yükümlülükleri ortadan kaldırdığı iddiası taşımaz.

## Cihazda işlenen veriler

Xylune; sohbetleri, istemleri, model yanıtlarını, taslakları, ekleri, anıları, ayarları, araç sonuçlarını, oluşturulan içerikleri ve isteğe bağlı Linux ortamı dosyalarını cihazda tutabilir. API anahtarları, OAuth oturumları, WebDAV ve S3 kimlik bilgileri desteklenen cihazlarda Android Keystore destekli şifreli uygulama alanında saklanır. Kimlik bilgileri ve OAuth oturumları taşınabilir Xylune yedeklerine dahil edilmez.

Resmî sürüm; Xylune tarafından işletilen bir servise otomatik analitik, reklam tanımlayıcısı, sohbet içeriği veya çökme raporu göndermez. Tanılama verileri yalnızca kullanıcı açıkça dışa aktarıp paylaştığında cihazdan çıkar.

Geliştiricide cihaz verilerinin uzaktaki bir kopyası bulunmadığından bu verilere erişme, düzeltme, dışa aktarma veya silme işlemleri normal olarak kullanıcı tarafından cihazda yapılır. Android uygulama verilerini temizlemek veya Xylune'u kaldırmak yerel uygulama verilerini siler.

## Yapay zekâ sağlayıcıları, araçlar ve kullanıcı yönlendirmeli aktarımlar

Kullanıcı bir yapay zekâ sağlayıcısını, web araştırmasını, URL'yi, ağ kullanan bir aracı veya başka bir dış hizmeti çalıştırdığında Xylune, kullanıcının talep ettiği işlem için gereken bilgileri doğrudan seçilen hizmete gönderebilir. Bunlara istemler, sohbet bağlamı, ekler, arama sorguları, araç girdileri, ayrıca izin verilmişse konum veya oluşturulan çıktılar dahil olabilir.

Geliştirici bu hizmetleri işletmez; bunların saklama, model eğitimi veya hesap uygulamalarını kontrol etmez ve yalnızca Xylune isteği başlattığı için verinin bir kopyasını almaz. Kullanıcı, göndereceği veri için uygun sağlayıcıyı seçmekten ve aktarım yetkisine sahip olmaktan sorumludur.

## Bulut yedekleme

Bir yedek; kullanıcının seçimine göre sohbetleri, ekleri, ayarları, anıları ve isteğe bağlı Linux ortamı dosyalarını içerebilir. Arşiv doğrudan cihaz ile seçilen hedef arasında aktarılır:

- **Google Drive:** yalnızca gizli `appDataFolder`, `https://www.googleapis.com/auth/drive.appdata` kapsamı üzerinden.
- **Microsoft OneDrive:** yalnızca uygulamanın OneDrive klasörü, `Files.ReadWrite.AppFolder` üzerinden.
- **Dropbox:** yalnızca Xylune Dropbox App klasörü.
- **WebDAV / Nextcloud:** yalnızca kullanıcının yapılandırdığı HTTPS uç noktası ve klasör.
- **S3 uyumlu depolama:** yalnızca kullanıcının yapılandırdığı HTTPS uç noktası, bucket ve prefix.
- **Android belge sağlayıcıları:** yalnızca Android sistem seçicisinde kullanıcının açıkça izin verdiği klasör veya belge.

Google, Microsoft ve Dropbox bağlı hesabı göstermek için ad veya e-posta gibi bir hesap etiketi döndürebilir. Bu değer ve OAuth oturumu cihazda kalır. Bağlantıyı kesmek cihazdaki oturumu veya kimlik bilgisini siler; sağlayıcıdaki mevcut yedekleri kendiliğinden silmez.

Google API'lerinden alınan bilgiler yalnızca kullanıcının talep ettiği yedekleme, listeleme, geri yükleme ve silme işlevleri için kullanılır. Geliştirici tarafından reklam, profilleme, satış veya model eğitimi amacıyla kullanılmaz.

## Geliştiriciye isteyerek gönderilen veriler

GitHub issue, pull request, tartışma, e-posta, güvenlik bildirimi veya tanılama paketi; kullanıcının eklemeyi seçtiği kullanıcı adı, e-posta adresi, cihaz bilgisi, log, ekran görüntüsü veya başka bilgileri içerebilir. Geliştirici bu bilgileri yalnızca bildirimi incelemek, proje güvenliğini sağlamak, bildirilen sorunu teşhis etmek veya hukuki yükümlülükleri yerine getirmek için kullanır.

Herkese açık GitHub içeriği başkaları tarafından görülebilir ve GitHub tarafından kendi koşulları kapsamında işlenir. Parola, token, özel anahtar, kimlik belgesi, özel konuşma veya başka sırlar herkese açık issue içine konulmamalıdır.

Geliştirici kişisel veri satmaz, davranışsal reklam yürütmez ve destek içeriklerini model eğitimi için kullanmaz.

## Saklama ve silme

- **Yalnızca cihazdaki veriler:** kullanıcı silene, uygulama verilerini temizleyene veya Xylune'u kaldırana kadar cihazda tutulur.
- **Bulut yedekleri:** kullanıcı Xylune veya sağlayıcı üzerinden silene kadar seçilen sağlayıcıda tutulur.
- **Geliştiricinin elindeki yazışmalar:** bildirimi değerlendirmek, proje geçmişini veya güvenliğini korumak, uyuşmazlıkları çözmek ya da hukuki yükümlülükleri yerine getirmek için makul ölçüde gerekli olduğu sürece tutulur. Herkese açık GitHub kayıtları düzenlense veya kapatılsa bile GitHub geçmişinde kalabilir.

Geliştirici yalnızca cihazda veya yalnızca kullanıcının seçtiği sağlayıcıda bulunan verilere erişemez ve bunları silemez. Ayrıntılı adımlar için [veri silme sayfasına](https://omerfaruknehir.github.io/Xylune/data-deletion/) bakın.

## İletişim ve cevap süreleri

Genel destek, hata bildirimi, özellik talebi ve olağan yazışmalar imkânlar ölçüsünde değerlendirilir. **Destek kullanılabilirliği veya cevap süresi taahhüt edilmez; bir mesaja hiç cevap verilmeyebilir.**

Gizlilikla ilgili bir talep, kişisel veya gizli bilgi paylaşılmadan [Xylune GitHub issue sayfası](https://github.com/omerfaruknehir/Xylune/issues) üzerinden başlatılabilir. Özel iletişim kanalı gereken kullanıcı, hassas ayrıntı vermeden özel kanal talep etmelidir. İlgili OAuth onay ekranında ayrıca özel bir iletişim yöntemi gösterilebilir.

Geliştiricinin fiilen elinde bulunan kişisel verilere ilişkin geçerli bir başvuru yalnızca uygulanabilir mevzuatın zorunlu kıldığı kapsamda sonuçlandırılır. Genel destek taahhüdünün bulunmaması emredici yasal süreleri ortadan kaldırmaz.

## Değişiklikler

Xylune'un gerçek veri akışları, bağlı hizmetleri veya hukuki gereklilikler değiştiğinde bu metin güncellenebilir. Yukarıdaki yürürlük tarihi güncel sürümü gösterir.
