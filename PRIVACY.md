# Xylune Privacy Policy

**Effective date: August 4, 2026**
[Türkçe metin aşağıdadır.](#xylune-gizlilik-politikası-ve-kvkk-aydınlatma-metni)

Xylune is a local-first, bring-your-own-provider Android application maintained from Türkiye by **Ömer Faruk Nehir**. It may be downloaded and used worldwide. Xylune does not require a Xylune account, does not operate an application backend that relays AI requests, and does not operate a central server that receives copies of chats or cloud backups.

This policy describes the official Xylune build and the limited processing controlled by the maintainer. Forks, modified builds, AI providers, storage providers, websites, and other third-party services have their own operators and policies.

## 1. Data that stays under the user's control

Xylune may store chats, prompts, model responses, drafts, attachments, memories, settings, tool results, generated content, workspaces, and optional Linux-environment data on the user's device. API keys, OAuth sessions, WebDAV credentials, and S3 credentials are stored in encrypted app storage backed by Android Keystore where supported. Credentials and OAuth sessions are excluded from portable Xylune archives.

The official build does not automatically send analytics, advertising identifiers, chat telemetry, or crash reports to a service operated by the Xylune maintainer. Diagnostic data reaches the maintainer only when a user deliberately exports and sends it.

The maintainer cannot remotely read, search, recover, correct, export, or delete data that remains only on a user's device. Uninstalling Xylune or clearing its Android app data is controlled by the user and Android, not by the maintainer.

## 2. Direct connections chosen by the user

When a user invokes an AI provider, web search, a URL, OAuth sign-in, cloud storage, a generated widget, or another external service, Xylune sends the information needed for that user-requested operation directly from the device to the selected service. Depending on the action, this can include prompts, conversation context, attachments, search queries, tool inputs, approximate or precise location when separately permitted, account identifiers, or generated outputs.

The maintainer does not receive a copy merely because Xylune initiated the direct connection. Each selected service independently determines its own collection, retention, training, security, international-transfer, and deletion practices under its terms and privacy policy. Users should review those documents before connecting a service.

## 3. Cloud backup scopes

A backup can include chats, attachments, settings, memories, and optional Linux-environment files selected by the user. The archive is transferred directly between the device and the selected destination:

- **Google Drive:** the hidden `appDataFolder`, through `https://www.googleapis.com/auth/drive.appdata`.
- **Microsoft OneDrive:** the application's OneDrive folder, through `Files.ReadWrite.AppFolder`.
- **Dropbox:** Xylune's Dropbox App folder, using scoped account and file permissions.
- **WebDAV / Nextcloud:** the HTTPS endpoint and folder configured by the user.
- **S3-compatible storage:** the HTTPS endpoint, bucket, and prefix configured by the user.
- **Android document providers:** the folder or document permission granted through Android's system picker.

Google, Microsoft, and Dropbox may return an account label, name, or email address so Xylune can display the connected account. That value and the OAuth session remain on the device. Disconnecting removes the local session or credentials but does not automatically delete backups already stored by the provider.

Xylune's use and transfer of information received from Google APIs is limited to user-requested backup, browsing, restore, and deletion functions. It is not used by the maintainer for advertising, profiling, or model training.

## 4. Data the maintainer may actually receive

The maintainer may process only information that is deliberately sent to channels under the maintainer's control, such as:

- public GitHub issues, discussions, or pull requests;
- private support or privacy correspondence;
- security reports and diagnostic files deliberately submitted by a user; and
- limited OAuth application administration information made available by an identity provider.

For that received information, the maintainer may determine the purpose and means of processing to operate support, security, abuse prevention, OAuth configuration, legal compliance, and project maintenance. The maintainer is not the storage intermediary for device-only chats or direct user-to-provider transfers and cannot fulfil access, correction, deletion, or portability requests for copies the maintainer never received or cannot identify.

Public GitHub content remains subject to GitHub's visibility and retention controls. Do not post passwords, API keys, tokens, identity documents, private chat logs, or other secrets in a public issue.

## 5. AI output and generated content

Xylune is a client interface, not the developer or host of the third-party AI models selected by users. The maintainer does not create, train, host, pre-review, or endorse individual model responses. Model output can be inaccurate, harmful, biased, unlawful, or unsuitable. This allocation of roles does not remove any responsibility that cannot legally be excluded. See the [Xylune Terms of Use](TERMS.md) for usage and warranty terms.

## 6. International use and transfers

Xylune can be used outside Türkiye. A user's device, AI provider, storage provider, GitHub, and user-configured endpoints may be located in different countries. International processing is initiated by the user's provider and endpoint choices. Mandatory privacy and consumer rights in the user's jurisdiction continue to apply where applicable; this policy does not waive them.

Users must not transfer personal, confidential, institutional, or third-party data unless they have the necessary authority and legal basis to do so.

## 7. Retention and deletion

Local data remains until the user deletes it in Xylune, clears Android app data, or uninstalls the app. Cloud backups remain until deleted through Xylune or the storage provider. The maintainer cannot delete a provider-side backup without access to the user's provider account and does not retain a central backup copy. See the [data deletion instructions](https://omerfaruknehir.github.io/Xylune/data-deletion/).

Information deliberately sent to the maintainer is retained only as reasonably needed for the relevant support, security, project-history, abuse-prevention, or legal purpose, subject to the controls and retention of the communication platform used.

## 8. Requests, contact, and response times

Bug reports and ordinary support requests may be submitted through the [Xylune issue tracker](https://github.com/omerfaruknehir/Xylune/issues). It is a volunteer-maintained open-source project: **no response time, availability, or support service level is promised for ordinary support**.

A private contact method may be shown in the relevant OAuth consent screen for requests that should not be public. A request should identify the communication or other data that the maintainer actually received; the maintainer may need reasonable information to verify identity and locate it. Valid privacy-rights requests are handled within any mandatory deadline that applies to the particular processing and jurisdiction. This paragraph does not create rights or obligations beyond applicable law and does not extend the maintainer's technical access to device-only or provider-controlled data.

## 9. Changes

This policy may be updated when Xylune's architecture, connected services, or legal requirements change. The effective date and repository history identify revisions.

---

# Xylune Gizlilik Politikası ve KVKK Aydınlatma Metni

**Yürürlük tarihi: 4 Ağustos 2026**

Xylune, **Ömer Faruk Nehir** tarafından Türkiye'den sürdürülen, yerel öncelikli ve kullanıcının kendi sağlayıcısını bağladığı bir Android uygulamasıdır. Dünyanın farklı ülkelerinden indirilebilir ve kullanılabilir. Xylune hesabı gerekmez; yapay zekâ isteklerini aktaran bir Xylune uygulama sunucusu veya sohbetlerin ve bulut yedeklerinin kopyalarını toplayan merkezi bir Xylune sunucusu işletilmez.

Bu metin resmî Xylune derlemesini ve geliştiricinin fiilen kontrol ettiği sınırlı işlemeyi açıklar. Forklar, değiştirilmiş derlemeler, yapay zekâ sağlayıcıları, depolama sağlayıcıları, internet siteleri ve diğer üçüncü taraf hizmetler kendi işletmecilerine ve politikalarına tabidir.

## 1. Kullanıcının kontrolünde kalan veriler

Xylune; sohbetleri, istemleri, model yanıtlarını, taslakları, ekleri, anıları, ayarları, araç sonuçlarını, oluşturulan içerikleri, çalışma alanlarını ve isteğe bağlı Linux ortamı verilerini kullanıcının cihazında tutabilir. API anahtarları, OAuth oturumları, WebDAV ve S3 kimlik bilgileri desteklenen cihazlarda Android Keystore destekli şifreli uygulama alanında saklanır. Kimlik bilgileri ve OAuth oturumları taşınabilir Xylune arşivlerine dahil edilmez.

Resmî derleme; analitik, reklam kimliği, sohbet telemetrisi veya çökme raporunu Xylune geliştiricisinin işlettiği bir servise otomatik olarak göndermez. Tanılama verileri yalnızca kullanıcı bunları bilerek dışa aktarıp geliştiriciye gönderirse geliştiriciye ulaşır.

Yalnızca cihazda kalan verileri geliştirici uzaktan okuyamaz, arayamaz, kurtaramaz, düzeltemez, dışa aktaramaz veya silemez. Uygulamayı kaldırma ve Android uygulama verisini temizleme işlemleri kullanıcı ve Android tarafından yönetilir.

## 2. Kullanıcının seçtiği doğrudan bağlantılar

Kullanıcı bir yapay zekâ sağlayıcısını, web aramasını, URL'yi, OAuth oturumunu, bulut depolamayı, oluşturulmuş widget'ı veya başka bir dış hizmeti kullandığında Xylune, kullanıcının talep ettiği işlem için gereken bilgiyi cihazdan doğrudan seçilen hizmete gönderir. İşleme göre bu bilgi; istem, sohbet bağlamı, ek, arama sorgusu, araç girdisi, ayrıca izin verilmiş yaklaşık veya kesin konum, hesap tanımlayıcısı ya da oluşturulmuş çıktı içerebilir.

Bağlantının Xylune tarafından başlatılması, geliştiricinin otomatik olarak bir kopya aldığı anlamına gelmez. Her hizmet; toplama, saklama, model eğitimi, güvenlik, yurt dışı aktarım ve silme uygulamalarını kendi koşulları ve gizlilik politikası kapsamında bağımsız olarak belirler. Kullanıcı, bir hizmeti bağlamadan önce bu belgeleri incelemelidir.

## 3. Bulut yedekleme kapsamları

Yedek; kullanıcının seçimine göre sohbet, ek, ayar, anı ve isteğe bağlı Linux ortamı dosyalarını içerebilir. Arşiv cihaz ile seçilen hedef arasında doğrudan aktarılır:

- **Google Drive:** `https://www.googleapis.com/auth/drive.appdata` kapsamıyla gizli `appDataFolder`.
- **Microsoft OneDrive:** `Files.ReadWrite.AppFolder` kapsamıyla uygulama klasörü.
- **Dropbox:** kapsamı sınırlandırılmış hesap ve dosya izinleriyle Xylune App klasörü.
- **WebDAV / Nextcloud:** kullanıcının yapılandırdığı HTTPS uç noktası ve klasör.
- **S3 uyumlu depolama:** kullanıcının yapılandırdığı HTTPS uç noktası, bucket ve prefix.
- **Android belge sağlayıcıları:** Android sistem seçicisinde izin verilen klasör veya belge.

Google, Microsoft ve Dropbox bağlı hesabı göstermek için ad veya e-posta gibi bir hesap etiketi döndürebilir. Bu değer ve OAuth oturumu cihazda kalır. Bağlantıyı kesmek cihazdaki oturumu veya kimlik bilgilerini kaldırır; sağlayıcıdaki mevcut yedekleri kendiliğinden silmez.

Google API'lerinden alınan bilgiler yalnızca kullanıcının istediği yedekleme, listeleme, geri yükleme ve silme işlevleri için kullanılır; geliştirici tarafından reklam, profilleme veya model eğitimi amacıyla kullanılmaz.

## 4. Geliştiricinin fiilen alabileceği veriler ve veri sorumluluğunun sınırı

Geliştirici yalnızca kendi kontrolündeki kanallara bilerek gönderilen şu tür bilgileri işleyebilir:

- herkese açık GitHub issue, discussion veya pull request içerikleri;
- özel destek veya gizlilik yazışmaları;
- kullanıcının bilerek ilettiği güvenlik raporları ve tanılama dosyaları; ve
- kimlik sağlayıcısının OAuth uygulama yönetimi kapsamında sunduğu sınırlı bilgiler.

Geliştirici, kendisine ulaşan bu bilgiler bakımından destek, güvenlik, kötüye kullanımın önlenmesi, OAuth yapılandırması, hukuki uyum ve proje bakımı amaç ve araçlarını belirlediği ölçüde veri sorumlusu olabilir. Geliştirici; yalnızca cihazda kalan sohbetlerin veya kullanıcıdan sağlayıcıya doğrudan aktarılan verilerin depolama aracısı değildir, bunların kopyasını elde etmez ve elde etmediği ya da kimliğini ilişkilendiremediği bir kopya için erişim, düzeltme, silme veya taşıma işlemi yapamaz.

Herkese açık GitHub içeriği GitHub'ın görünürlük ve saklama kontrollerine tabidir. Herkese açık bir issue içine parola, API anahtarı, token, kimlik belgesi, özel sohbet kaydı veya başka bir sır yazmayın.

## 5. Yapay zekâ çıktısı ve oluşturulmuş içerik

Xylune bir istemci arayüzüdür; kullanıcının seçtiği üçüncü taraf yapay zekâ modelinin geliştiricisi veya barındırıcısı değildir. Xylune geliştiricisi tek tek model yanıtlarını oluşturmaz, modeli eğitmez veya barındırmaz, yanıtları önceden incelemez ve onaylamaz. Model çıktısı yanlış, zararlı, taraflı, hukuka aykırı veya amaca elverişsiz olabilir. Bu rol ayrımı, hukuken sınırlandırılması mümkün olmayan bir sorumluluğu ortadan kaldırmaz. Kullanım ve garanti hükümleri için [Xylune Kullanım Koşulları](TERMS.md) belgesine bakın.

## 6. Yurt dışı kullanım ve aktarım

Xylune Türkiye dışında kullanılabilir. Kullanıcının cihazı, yapay zekâ sağlayıcısı, depolama sağlayıcısı, GitHub ve yapılandırdığı uç noktalar farklı ülkelerde bulunabilir. Yurt dışı işleme, kullanıcının sağlayıcı ve uç nokta seçimiyle başlatılır. Kullanıcının bulunduğu yerde uygulanması zorunlu gizlilik ve tüketici hakları varsa geçerliliğini korur; bu metin bu haklardan feragat ettirmez.

Kullanıcı; kişisel, gizli, kurumsal veya üçüncü kişilere ait verileri ancak gerekli yetki ve hukuki dayanağa sahipse aktarmalıdır.

## 7. Saklama ve silme

Yerel veriler kullanıcı Xylune içinde silene, Android uygulama verisini temizleyene veya uygulamayı kaldırana kadar cihazda kalır. Bulut yedekleri Xylune ya da depolama sağlayıcısı üzerinden silinene kadar sağlayıcıda kalır. Geliştirici, kullanıcının sağlayıcı hesabına erişmeden sağlayıcıdaki yedeği silemez ve merkezi bir yedek kopyası saklamaz. Ayrıntılı adımlar için [veri silme sayfasına](https://omerfaruknehir.github.io/Xylune/data-deletion/) bakın.

Geliştiriciye bilerek gönderilen bilgi; ilgili destek, güvenlik, proje geçmişi, kötüye kullanımın önlenmesi veya hukuki amaç için makul ölçüde gerekli olduğu sürece ve kullanılan iletişim platformunun kontrollerine tabi olarak saklanır.

## 8. Başvuru, iletişim ve yanıt süreleri

Hata bildirimleri ve olağan destek talepleri [Xylune issue sayfası](https://github.com/omerfaruknehir/Xylune/issues) üzerinden iletilebilir. Bu, gönüllü sürdürülen açık kaynaklı bir projedir: **olağan destek için yanıt süresi, erişilebilirlik veya hizmet seviyesi taahhüt edilmez**.

Herkese açık olmaması gereken başvurular için ilgili OAuth onay ekranında özel bir iletişim yöntemi gösterilebilir. Başvuru, geliştiricinin fiilen aldığı yazışmayı veya diğer veriyi tanımlamalıdır; kimliğin doğrulanması ve verinin bulunması için makul ek bilgi istenebilir. Geçerli kişisel veri başvuruları, somut işlemeye ve uygulanabilir hukuka göre zorunlu olan süre içinde sonuçlandırılır. Bu hüküm, uygulanabilir hukukun ötesinde ek bir hak veya yükümlülük yaratmaz ve geliştiriciye cihazda ya da sağlayıcı kontrolünde bulunan verilere teknik erişim sağlamaz.

6698 sayılı Kanun uygulanıyorsa, ilgili kişi Kanun'un 11. maddesindeki haklarını yalnızca geliştiricinin veri sorumlusu olduğu somut işleme bakımından kullanabilir. Başvurunun usulü, kimlik doğrulaması, ücret ve cevap süresi yürürlükteki mevzuata tabidir. Başka ülkelerdeki zorunlu haklar da uygulanabildiği ölçüde saklıdır.

## 9. Değişiklikler

Xylune mimarisi, bağlı hizmetler veya hukuki gereklilikler değiştiğinde bu politika güncellenebilir. Yürürlük tarihi ve depo geçmişi değişiklikleri gösterir.
