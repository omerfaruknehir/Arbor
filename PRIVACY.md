# Xylune Privacy Policy

**Effective date: August 5, 2026**

[Türkçe metin aşağıdadır.](#xylune-gizlilik-politikası-ve-kvkk-aydınlatma-metni)

Xylune is a local-first Android client maintained by **Ömer Faruk Nehir in Türkiye**. The official app has no Xylune account, advertising system, analytics service, or central chat backend. Ordinary AI requests and backups travel directly from the device to services chosen by the user.

This policy covers the official Xylune build, its official documentation site, and the limited information the maintainer actually receives. A fork, modified build, app store, AI provider, storage provider, or other connected service has its own operator and privacy terms.

## 1. The short version

| Situation | Where the data goes | What the Xylune maintainer receives |
| --- | --- | --- |
| Chats, settings, memories, workspaces, and local tools | The Android device | Nothing, unless the user deliberately sends a copy |
| AI, search, URL, or local-server request | Directly to the service or endpoint selected by the user | No relayed copy |
| Cloud backup or restore | Directly between the device and the selected storage provider | No backup copy |
| GitHub issue, security report, or private privacy/support message | The channel deliberately used by the user | The submitted content and related account/contact metadata |
| Official documentation site visit | GitHub Pages infrastructure | No Xylune analytics profile; GitHub may process ordinary request and security logs |

Xylune does not sell personal data, share it for behavioural advertising, or use chats or Google user data to train AI models.

## 2. Data kept on the device

Depending on the features used, Xylune may keep the following in app-private storage:

- chats, prompts, model responses, drafts, attachments, memories, and settings;
- provider and model configuration, account labels, usage information returned by a provider, and recent-model preferences;
- tool results, generated files, code workspaces, and optional Linux-environment files;
- backup history and the destination configuration chosen by the user; and
- API keys, OAuth sessions, WebDAV credentials, and S3-compatible credentials.

Credentials are stored in encrypted app-private storage backed by Android Keystore where supported. Credentials and OAuth sessions are excluded from portable Xylune archives. An archive is encrypted only when the user protects it with a password; a passwordless archive is not encrypted by Xylune.

The official build does not automatically send the maintainer advertising identifiers, analytics events, chats, tool transcripts, or crash reports. A diagnostic export leaves the device only when the user deliberately shares it.

The maintainer cannot remotely access, recover, correct, export, or delete device-only data. The user can remove it in Xylune, clear Xylune's Android app data, or uninstall the app.

## 3. Direct connections selected by the user

Xylune sends data only when needed to perform an action initiated or enabled by the user. Depending on that action, a selected AI provider, search service, website, local server, OAuth provider, or storage endpoint may receive:

- prompts, relevant conversation context, model and generation settings;
- attachments, images, extracted text, local OCR fallback, or generated content;
- search queries, URLs, web requests, tool inputs, and network metadata;
- account identifiers and OAuth authorization data; or
- approximate or precise location only after the relevant Android permission and feature are enabled.

These transfers are device-to-provider; they are not relayed through a Xylune server. Each provider independently controls its logs, retention, model-training choices, security, international transfers, account decisions, and deletion tools. The user should review the selected provider's terms and privacy policy before sending sensitive or third-party data.

## 4. Cloud backup and OAuth scopes

A user-selected backup may contain chats, attachments, settings, memories, and optional Linux files. It is transferred directly to the selected destination:

- **Google Drive:** the hidden `appDataFolder` using `https://www.googleapis.com/auth/drive.appdata`;
- **Microsoft OneDrive:** the application's folder using `Files.ReadWrite.AppFolder`;
- **Dropbox:** Xylune's scoped App folder;
- **WebDAV / Nextcloud:** the HTTPS endpoint and folder configured by the user;
- **S3-compatible storage:** the HTTPS endpoint, bucket, and prefix configured by the user; or
- **Android document providers:** only a target granted through Android's system picker.

Google, Microsoft, and Dropbox may return an account label, name, or email address so Xylune can show the connected account. The label and OAuth session remain on the device. Disconnecting removes the local session or credentials; it does not automatically delete backups already held by the provider.

Xylune's use and transfer of information received from Google APIs complies with the **Google API Services User Data Policy, including the Limited Use requirements**. Google user data is used only for user-requested backup listing, creation, restore, and deletion. It is not used by Xylune for advertising, profiling, credit decisions, or AI-model training, and is not transferred except as necessary to provide those user-requested functions, for security, or when legally required.

## 5. Information the maintainer may receive

The data controller for information actually received through official Xylune channels is **Ömer Faruk Nehir, Türkiye**. The maintainer may receive:

- public GitHub issues, discussions, pull requests, comments, and profile information shown by GitHub;
- private support, privacy, or security correspondence and its contact details;
- diagnostic files, screenshots, logs, or chat excerpts deliberately submitted by a user; and
- limited OAuth application administration and security information supplied by an identity provider.

This information is used to answer requests, maintain and secure the project, prevent abuse, administer OAuth integrations, establish or defend legal claims, and comply with law. Depending on the processing and applicable law, the legal basis is the user's requested pre-contractual or service action, compliance with a legal obligation, establishment or defence of rights, the maintainer's legitimate interest in supporting and securing the project, or consent where consent is specifically requested. Consent may be withdrawn for future processing without affecting earlier lawful processing.

The maintainer does not become controller of a device-only chat or a provider's independent copy merely because the official app connected the user to that provider. The maintainer cannot act on a copy never received or not reasonably identifiable.

## 6. Sharing, sale, training, and automated decisions

The maintainer does not sell personal data, rent contact lists, share data for cross-context behavioural advertising, or use received support content to train AI models. Information received by the maintainer may be disclosed only to infrastructure or communication providers used for the relevant channel, project collaborators who need it to resolve the request, professional advisers, authorities when legally required, or another project operator as part of a disclosed transfer of the official project.

The maintainer does not use received information for automated decision-making or profiling that produces legal or similarly significant effects. Connected providers may have different practices under their own policies.

## 7. International processing

The device, GitHub, an AI provider, a storage provider, and a user-configured endpoint may be in different countries. Direct provider processing is caused by the user's provider and endpoint choices and is governed by that provider's safeguards. Information deliberately sent to the maintainer may be processed through GitHub, an OAuth provider, or another communication service outside Türkiye. Where cross-border transfer law applies, an available lawful transfer mechanism or exception must be used.

Do not upload personal, confidential, institutional, or third-party information unless you have authority and a lawful basis to do so. This notice does not waive any mandatory obligation of the maintainer or a provider.

## 8. Retention and deletion

- **Device data:** retained until deleted in Xylune, cleared through Android settings, or removed by uninstalling.
- **Provider data and backups:** retained under the selected provider's controls until deleted there or through Xylune. Revoking access stops future access but may not delete existing files.
- **Private requests and security reports:** retained only as long as reasonably needed to resolve the request, protect the project, meet legal duties, or establish and defend claims.
- **Public project activity:** normally remains in the public project history under GitHub's controls, although content may be edited or removed where appropriate and technically available.

Detailed steps are on the [Xylune data deletion page](https://omerfaruknehir.github.io/Xylune/data-deletion/).

## 9. Privacy rights and contact

Rights apply only to processing for which the maintainer is legally responsible. Depending on the law that applies, a person may have rights to learn whether data is processed; obtain access and information; correct inaccurate data; request deletion, destruction, restriction, or objection; receive portable data; learn recipients; object to certain automated results; withdraw consent; seek compensation; and complain to the Turkish Personal Data Protection Authority or another competent supervisory authority.

Under Türkiye's Law No. 6698, the rights in Article 11 remain available where applicable. Where the GDPR applies, requests are handled without undue delay and normally within one month, subject to lawful extensions and identity verification. Other mandatory deadlines remain unaffected.

- Non-confidential bugs or requests: [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues)
- Controller/project contact: [Ömer Faruk Nehir on GitHub](https://github.com/omerfaruknehir)
- Confidential OAuth-related requests: use the private contact method shown on the relevant OAuth consent screen

GitHub issues are public. Never post passwords, API keys, tokens, identity documents, private chat logs, or other secrets there. A request should describe the official channel and information concerned; reasonable identity verification may be required. Ordinary open-source support has no promised response time, but that does not change a mandatory privacy deadline.

## 10. Security, children, and changes

Xylune uses Android app isolation, scoped provider permissions, and encrypted credential storage where supported. No storage or transmission method is completely secure. Users are responsible for device security, archive passwords, provider permissions, and additional backups of important data.

Xylune is not directed to children. A child may use it only with any consent or supervision required by local law and by each connected provider. The official app does not knowingly operate a separate child-profile database.

This policy may change when Xylune's features, operators, or legal duties change. The effective date and public repository history identify the current version.

---

# Xylune Gizlilik Politikası ve KVKK Aydınlatma Metni

**Yürürlük tarihi: 5 Ağustos 2026**

Xylune, **Ömer Faruk Nehir tarafından Türkiye'de** sürdürülen yerel öncelikli bir Android istemcisidir. Resmî uygulamada Xylune hesabı, reklam sistemi, analitik servisi veya merkezi sohbet sunucusu yoktur. Olağan yapay zekâ istekleri ve yedekler, cihazdan doğrudan kullanıcının seçtiği hizmete gider.

Bu politika; resmî Xylune derlemesini, resmî belge sitesini ve geliştiricinin fiilen aldığı sınırlı bilgileri kapsar. Fork, değiştirilmiş derleme, uygulama mağazası, yapay zekâ sağlayıcısı, depolama sağlayıcısı veya diğer bağlı hizmet kendi işletmecisine ve gizlilik koşullarına sahiptir.

## 1. Kısa özet

| Durum | Veri nereye gider? | Xylune geliştiricisi ne alır? |
| --- | --- | --- |
| Sohbet, ayar, anı, çalışma alanı ve yerel araçlar | Android cihaz | Kullanıcı bilerek kopya göndermedikçe hiçbir şey |
| Yapay zekâ, arama, URL veya yerel sunucu isteği | Doğrudan kullanıcının seçtiği hizmet ya da uç nokta | Aracı sunucu kopyası yok |
| Bulut yedekleme veya geri yükleme | Doğrudan cihaz ile seçilen depolama sağlayıcısı arasında | Yedek kopyası yok |
| GitHub issue, güvenlik bildirimi veya özel gizlilik/destek mesajı | Kullanıcının bilerek seçtiği kanal | Gönderilen içerik ve ilgili hesap/iletişim bilgisi |
| Resmî belge sitesi ziyareti | GitHub Pages altyapısı | Xylune analitik profili yok; GitHub olağan istek ve güvenlik kayıtlarını işleyebilir |

Xylune kişisel veri satmaz, davranışsal reklam amacıyla paylaşmaz; sohbetleri veya Google kullanıcı verilerini yapay zekâ modeli eğitmek için kullanmaz.

## 2. Cihazda tutulan veriler

Kullanılan özelliklere göre Xylune aşağıdakileri uygulamaya özel alanda tutabilir:

- sohbet, istem, model yanıtı, taslak, ek, anı ve ayarlar;
- sağlayıcı ve model yapılandırması, hesap etiketi, sağlayıcının döndürdüğü kullanım bilgisi ve son model tercihleri;
- araç sonuçları, oluşturulan dosyalar, kod çalışma alanları ve isteğe bağlı Linux ortamı dosyaları;
- yedek geçmişi ve kullanıcının seçtiği hedef yapılandırması; ve
- API anahtarı, OAuth oturumu, WebDAV ve S3 uyumlu kimlik bilgileri.

Kimlik bilgileri, desteklenen cihazlarda Android Keystore destekli şifrelenmiş uygulama alanında saklanır. Kimlik bilgileri ve OAuth oturumları taşınabilir Xylune arşivlerine dahil edilmez. Arşiv yalnızca kullanıcı parola belirlediğinde şifrelenir; parolasız arşiv Xylune tarafından şifrelenmez.

Resmî derleme; reklam kimliği, analitik olayı, sohbet, araç kaydı veya çökme raporunu geliştiriciye otomatik göndermez. Tanılama dışa aktarımı ancak kullanıcı bilerek paylaşırsa cihazdan çıkar.

Geliştirici yalnızca cihazda bulunan veriye uzaktan erişemez; bu veriyi kurtaramaz, düzeltemez, dışa aktaramaz veya silemez. Kullanıcı veriyi Xylune'dan silebilir, Android uygulama verisini temizleyebilir veya uygulamayı kaldırabilir.

## 3. Kullanıcının seçtiği doğrudan bağlantılar

Xylune yalnızca kullanıcının başlattığı veya etkinleştirdiği işlemi yapmak için gerekli veriyi gönderir. İşleme göre seçilen yapay zekâ sağlayıcısı, arama hizmeti, internet sitesi, yerel sunucu, OAuth sağlayıcısı veya depolama ucu şunları alabilir:

- istem, ilgili sohbet bağlamı, model ve üretim ayarları;
- ek, görsel, çıkarılmış metin, yerel OCR uyumluluk çıktısı veya oluşturulan içerik;
- arama sorgusu, URL, web isteği, araç girdisi ve ağ üst verisi;
- hesap tanımlayıcısı ve OAuth yetkilendirme verisi; veya
- yalnızca ilgili Android izni ve özellik etkinleştirildikten sonra yaklaşık ya da kesin konum.

Bu aktarımlar cihazdan sağlayıcıya yapılır; Xylune sunucusundan geçirilmez. Her sağlayıcı kayıt, saklama, model eğitimi, güvenlik, yurt dışı aktarım, hesap kararı ve silme araçlarını kendi koşulları kapsamında bağımsız belirler. Kullanıcı hassas veya üçüncü kişiye ait veri göndermeden önce seçtiği sağlayıcının metinlerini incelemelidir.

## 4. Bulut yedekleme ve OAuth kapsamları

Kullanıcının seçtiği yedek; sohbet, ek, ayar, anı ve isteğe bağlı Linux dosyalarını içerebilir. Yedek doğrudan şu hedeflerden birine aktarılır:

- **Google Drive:** `https://www.googleapis.com/auth/drive.appdata` kapsamıyla gizli `appDataFolder`;
- **Microsoft OneDrive:** `Files.ReadWrite.AppFolder` kapsamıyla uygulama klasörü;
- **Dropbox:** Xylune'un kapsamı sınırlandırılmış App klasörü;
- **WebDAV / Nextcloud:** kullanıcının yapılandırdığı HTTPS uç noktası ve klasör;
- **S3 uyumlu depolama:** kullanıcının yapılandırdığı HTTPS uç noktası, bucket ve prefix; veya
- **Android belge sağlayıcıları:** yalnızca Android sistem seçicisinde izin verilen hedef.

Google, Microsoft ve Dropbox bağlı hesabı göstermek için hesap etiketi, ad veya e-posta döndürebilir. Bu etiket ve OAuth oturumu cihazda kalır. Bağlantıyı kesmek yerel oturumu veya kimlik bilgisini kaldırır; sağlayıcıdaki mevcut yedeği kendiliğinden silmez.

Xylune'un Google API'lerinden alınan bilgileri kullanması ve aktarması, **Sınırlı Kullanım gereklilikleri dahil Google API Hizmetleri Kullanıcı Verileri Politikası** ile uyumludur. Google kullanıcı verisi yalnızca kullanıcının istediği yedekleri listeleme, oluşturma, geri yükleme ve silme işlevlerinde kullanılır. Xylune bu veriyi reklam, profilleme, kredi kararı veya yapay zekâ modeli eğitimi için kullanmaz; veriyi yalnızca bu kullanıcı işlevini sağlamak, güvenliği korumak veya hukuki zorunluluğa uymak için gerektiğinde aktarır.

## 5. Geliştiricinin alabileceği bilgiler

Resmî Xylune kanallarından fiilen alınan bilgiler bakımından veri sorumlusu **Ömer Faruk Nehir, Türkiye**'dir. Geliştirici şunları alabilir:

- herkese açık GitHub issue, discussion, pull request, yorum ve GitHub'ın gösterdiği profil bilgileri;
- özel destek, gizlilik veya güvenlik yazışması ve iletişim bilgisi;
- kullanıcının bilerek gönderdiği tanılama dosyası, ekran görüntüsü, kayıt veya sohbet kesiti; ve
- kimlik sağlayıcısının sunduğu sınırlı OAuth uygulama yönetimi ve güvenlik bilgisi.

Bu bilgiler; talebi cevaplamak, projeyi sürdürmek ve güvenliğini sağlamak, kötüye kullanımı önlemek, OAuth bağlantılarını yönetmek, bir hakkı tesis/kullanmak/savunmak ve hukuka uymak için kullanılır. Somut işleme ve uygulanabilir hukuka göre hukuki sebep; kullanıcının talep ettiği sözleşme öncesi veya hizmet işlemi, hukuki yükümlülük, bir hakkın tesisi/kullanılması/korunması, projeyi destekleme ve güvenli tutmaya yönelik meşru menfaat ya da rızanın özellikle istendiği durumda rızadır. Rıza, önceki hukuka uygun işlemenin geçerliliğini etkilemeden gelecek için geri çekilebilir.

Resmî uygulamanın kullanıcıyı sağlayıcıya bağlaması, geliştiriciyi yalnızca cihazda bulunan sohbetin veya sağlayıcının bağımsız kopyasının veri sorumlusu yapmaz. Geliştirici hiç almadığı veya makul biçimde ilişkilendiremediği bir kopya üzerinde işlem yapamaz.

## 6. Paylaşım, satış, eğitim ve otomatik karar

Geliştirici kişisel veri satmaz, iletişim listesi kiralamaz, farklı bağlamlar arası davranışsal reklam amacıyla veri paylaşmaz ve aldığı destek içeriğini yapay zekâ modeli eğitmek için kullanmaz. Geliştiricinin aldığı bilgi yalnızca ilgili kanalın altyapı/iletişim sağlayıcısına, talebi çözmesi gereken proje katkıcısına, mesleki danışmana, hukuken zorunluysa yetkili makama veya resmî projenin açıklanmış devri kapsamında yeni işletmeciye aktarılabilir.

Geliştirici, aldığı bilgi üzerinde hukuki veya benzer derecede önemli sonuç doğuran otomatik karar ya da profilleme yapmaz. Bağlı sağlayıcıların kendi politikaları farklı olabilir.

## 7. Yurt dışı işleme

Cihaz, GitHub, yapay zekâ sağlayıcısı, depolama sağlayıcısı ve kullanıcının yapılandırdığı uç nokta farklı ülkelerde olabilir. Doğrudan sağlayıcı işlemesi kullanıcının sağlayıcı/uç nokta seçimiyle başlar ve ilgili sağlayıcının güvencelerine tabidir. Geliştiriciye bilerek gönderilen bilgiler GitHub, OAuth sağlayıcısı veya başka bir iletişim hizmeti üzerinden Türkiye dışında işlenebilir. Yurt dışı aktarım mevzuatı uygulandığında kullanılabilir hukuka uygun aktarım mekanizması veya istisna kullanılmalıdır.

Kişisel, gizli, kurumsal veya üçüncü kişiye ait veriyi yalnızca gerekli yetki ve hukuki sebebe sahipseniz yükleyin. Bu açıklama geliştiricinin veya sağlayıcının emredici yükümlülüklerinden feragat ettirmez.

## 8. Saklama ve silme

- **Cihaz verisi:** Xylune içinde silinene, Android ayarlarından temizlenene veya uygulama kaldırılana kadar tutulur.
- **Sağlayıcı verisi ve yedek:** ilgili sağlayıcının kontrollerine göre, Xylune veya sağlayıcı üzerinden silinene kadar tutulur. Erişimi iptal etmek gelecekteki erişimi durdurur; mevcut dosyayı silmeyebilir.
- **Özel başvuru ve güvenlik bildirimi:** talebi çözmek, projeyi korumak, hukuki yükümlülüğü yerine getirmek veya bir hakkı tesis/savunmak için makul ölçüde gerekli süre tutulur.
- **Herkese açık proje faaliyeti:** uygun ve teknik olarak mümkün olduğunda içerik düzenlenebilse veya kaldırılabilse de, normalde GitHub kontrolleri altında herkese açık proje geçmişinde kalır.

Ayrıntılı adımlar [Xylune veri silme sayfasındadır](https://omerfaruknehir.github.io/Xylune/data-deletion/).

## 9. İlgili kişi hakları ve iletişim

Haklar yalnızca geliştiricinin hukuken sorumlu olduğu işlemeye uygulanır. Uygulanabilir hukuka göre ilgili kişi; veri işlenip işlenmediğini öğrenme, erişim ve bilgi alma, yanlış veriyi düzeltme, silme/yok etme/kısıtlama/itiraz isteme, taşınabilir veri alma, alıcıları öğrenme, belirli otomatik sonuçlara itiraz etme, rızayı geri çekme, zararın giderilmesini isteme ve Kişisel Verileri Koruma Kurumu'na veya başka bir yetkili denetim makamına şikâyet etme haklarına sahip olabilir.

6698 sayılı Kanun uygulanıyorsa 11. maddedeki haklar saklıdır. GDPR uygulandığında başvurular, hukuka uygun uzatma ve kimlik doğrulama halleri saklı olmak üzere, gecikmeksizin ve normalde bir ay içinde sonuçlandırılır. Diğer emredici süreler değişmez.

- Gizli olmayan hata ve talepler: [Xylune issue sayfası](https://github.com/omerfaruknehir/Xylune/issues)
- Veri sorumlusu/proje iletişimi: [GitHub'da Ömer Faruk Nehir](https://github.com/omerfaruknehir)
- Gizli OAuth talepleri: ilgili OAuth onay ekranında gösterilen özel iletişim yöntemini kullanın

GitHub issue'ları herkese açıktır. Parola, API anahtarı, token, kimlik belgesi, özel sohbet veya başka bir sır paylaşmayın. Başvuru ilgili resmî kanalı ve bilgiyi açıklamalıdır; makul kimlik doğrulaması istenebilir. Olağan açık kaynak desteği için yanıt süresi taahhüt edilmez; bu durum emredici kişisel veri başvuru süresini değiştirmez.

## 10. Güvenlik, çocuklar ve değişiklikler

Xylune; Android uygulama izolasyonu, kapsamı sınırlandırılmış sağlayıcı izinleri ve desteklenen cihazlarda şifreli kimlik bilgisi depolaması kullanır. Hiçbir saklama veya aktarım yöntemi tamamen güvenli değildir. Cihaz güvenliği, arşiv parolası, sağlayıcı izinleri ve önemli verilerin ek yedekleri kullanıcı tarafından korunmalıdır.

Xylune çocuklara yönelik değildir. Bir çocuk yalnızca yerel hukukun ve bağlı her sağlayıcının gerektirdiği izin veya gözetimle kullanabilir. Resmî uygulama bilerek ayrı bir çocuk profili veritabanı işletmez.

Xylune'un özellikleri, işletmecileri veya hukuki yükümlülükleri değiştiğinde bu politika güncellenebilir. Yürürlük tarihi ve herkese açık depo geçmişi güncel sürümü gösterir.
