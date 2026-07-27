# Arbor source-history import

This repository was reconstructed from source archives in the ChatGPT Library. Release tags point to the exact sanitized project tree imported from each canonical archive; repository-management documentation was added only after the final release tag.

## Import policy

- Only the outer archive wrapper was removed.
- Build caches, generated build outputs, APK/AAB files, embedded Git directories, IDE workspace state, `local.properties`, and signing keystores were excluded.
- Archive SHA-256 values are hashes of the original Library bytes. Byte-identical uploads and byte-different archives with identical sanitized source content were deduplicated; genuinely different same-version trees were preserved on variant tags and branches.
- Git dates use the newest source timestamp stored inside each archive because materialization does not preserve Library filesystem timestamps. Library listing metadata remains the authoritative upload time.

## Imported archives

| Version | Original Library filename | SHA-256 | Commit | Tag | Archive timestamp used | Classification | Branch |
|---|---|---|---|---|---|---|---|
| 0.11.0 | `Arbor-0.11.0-source.zip` | `a2d5f98cc8d8cc6e6acc988ab0bfaef77f55029d2a2ae2eca4c327e4747475af` | `5fcd7d438d2f2958a14126c5d0963a7530033daf` | `v0.11.0` | 2026-07-17T16:05:28Z | canonical | `main` |
| 0.11.1 | `Arbor-0.11.1-source.zip` | `3caffb61c29e652c0111a289c0be7167f3367c121dd6f6bdad7f313d683a0a4d` | `06b9d745953fa6f88ae859e181c9172fb3c957ef` | `v0.11.1` | 2026-07-17T17:25:06Z | canonical | `main` |
| 0.12.0 | `Arbor-0.12.0-source.zip` | `7b4ae0919c3d7ff64be858c98ebf189c26b507a9afc719eafe330286ecb1776a` | `babbec25a5dad820674bcaf777629ac04d72ced6` | `v0.12.0` | 2026-07-17T18:48:46Z | canonical | `main` |
| 0.13.0 | `Arbor-0.13.0-source.zip` | `336138cad3dc686e2df03b75d43473137d40e3b0fdee345616a8dfae30efcfb9` | `61cc175086adeabeca463794c84c4b5f3185f46b` | `v0.13.0` | 2026-07-17T19:51:58Z | canonical | `main` |
| 0.14.0 | `Arbor-0.14.0-source.zip` | `53503b345d1e9fbd4fb20e3fa3f37849ce676bca427631c1aa81469ac3895ebe` | `0aa4306c8e50a4dadfae7974e5a54567d1c89d1f` | `v0.14.0` | 2026-07-17T20:17:42Z | canonical | `main` |
| 0.15.0 | `Arbor-0.15.0-source-prebuild.zip` | `9c4a3f151a40290a7bdc584598220e7316dff41c99751e79c8f979d0f137fecb` | `11627d9e5111229c40aa7500958585a622f8126b` | `v0.15.0-prebuild` | 2026-07-17T21:13:18Z | canonical | `main` |
| 0.16.0 | `Arbor-0.16.0-source.zip` | `f88ee42c482696bc16696f4ea04af120252d85a9535c00c57c259b4e9ab9aa3d` | `fe94c11018b60662c5a0a4c747bfdf6397dab30e` | `v0.16.0` | 2026-07-18T00:12:32Z | canonical | `main` |
| 0.16.1 | `Arbor-0.16.1-source.zip` | `b82a3b20da4a1141ec4fabac372fde7b22f53ee00709b12e472dc461c1a1f7ab` | `016d41ff8d434c789e62e06528976ec5e39eb996` | `v0.16.1` | 2026-07-18T01:05:30Z | canonical | `main` |
| 0.16.2 | `Arbor-0.16.2-source.zip` | `b4752b87adb4c37991dd662627c79ddeb27d9f8ca6931b4c80c8a2ab13502187` | `dc77864a34b81b02341cbf40ee77b855a74051bd` | `v0.16.2` | 2026-07-18T01:25:42Z | canonical | `main` |
| 0.16.3 | `Arbor-0.16.3-source-prebuild.zip` | `c7fdf80172b3f32ef3ead5598d3ac1a6a7807ef58c00b8578a7e0c232da548ca` | `a1dcfb6150761343ebd8c40a7189fdfd7fb61304` | `v0.16.3-prebuild` | 2026-07-18T01:45:20Z | canonical | `main` |
| 0.16.4 | `Arbor-0.16.4-source-prebuild.zip` | `140be0b5976cad05b63955ac8b343324686e38337107ab692414195db73e0b9c` | `b4cb039d9d40cd7be386f7c87d377f1b6a09625b` | `v0.16.4-prebuild` | 2026-07-18T02:24:00Z | canonical | `main` |
| 0.16.5 | `Arbor-0.16.5-source.zip` | `e0bf77cbf088633cb6c3c02999ea1102324be8bd0bee21a8decdf82f18941aea` | `9c82c11b6e592aef23b217208b99bed45d09239d` | `v0.16.5` | 2026-07-18T02:42:04Z | canonical | `main` |
| 0.16.6 | `Arbor-0.16.6-source.zip` | `ef9040bc0738526b988a98f704d521c6e68533f58e000d669595e75e9e1fdba5` | `247e43b7360cc4888e458d4c8623c74793bba1d9` | `v0.16.6` | 2026-07-18T05:10:24Z | canonical | `main` |
| 0.16.7 | `Arbor-0.16.7-source.zip` | `46d86cadc77b6ad8d6d111c3da93b688b9772e513d7cdd11a5b678adde3d62a2` | `1dfb0e5e14a2ee171b83d4c4c0b4e37abb06a035` | `v0.16.7` | 2026-07-18T12:45:32Z | canonical | `main` |
| 0.16.7 | `Arbor-0.16.7-source(1).zip` | `46d86cadc77b6ad8d6d111c3da93b688b9772e513d7cdd11a5b678adde3d62a2` | `1dfb0e5e14a2ee171b83d4c4c0b4e37abb06a035` | `v0.16.7` | 2026-07-18T12:45:32Z | duplicate | `` |
| 0.16.8 | `Arbor-0.16.8-source.zip` | `2a8e0cb8e91c37d6ec3a6d2656232c7cf2b8a28e199a9e878742dffb7468e8b4` | `8dce46c6c421224462eabe916dac4821fbdb12dc` | `v0.16.8` | 2026-07-18T15:16:38Z | canonical | `main` |
| 0.16.9 | `Arbor-0.16.9-source.zip` | `37efc7fbc9e42234856642af8654d5b8c60b53bb6ebf90a3b1d7df1848393e28` | `74b141f8376750ed98b77b6e79c61302f5eb1870` | `v0.16.9` | 2026-07-18T15:38:42Z | canonical | `main` |
| 0.16.10 | `Arbor-0.16.10-source.zip` | `d44d7962530e8778375dfacc8296823678f0e9416aaff7354f5a699bc27689e1` | `c2afe094b7a7751ad8c28e6c59b7045f10921e42` | `v0.16.10` | 2026-07-18T16:26:32Z | canonical | `main` |
| 0.16.11 | `Arbor-0.16.11-source.zip` | `9a1637a1f41aae62f067c308a52b1f39fd82749806666a291efa1062fac9f981` | `f5999876384693081e869bfb1343d63b63a8184c` | `v0.16.11` | 2026-07-18T17:08:36Z | canonical | `main` |
| 0.16.12 | `Arbor-0.16.12-source.zip` | `4cfc6683671c2f700c7390e0bdf5039874304f69aadfc0dfed14f1e4d95ae252` | `ef938ebf9a8795edb87a66a4d9b29b1b93f2447c` | `v0.16.12` | 2026-07-18T18:19:10Z | canonical | `main` |
| 0.16.14 | `Arbor-0.16.14-source.zip` | `2d819697bd4bbadd60cd1b9d56f1e8853291ec6804704da3650c094eb9a43592` | `2e7f79a037c9010d878f5d105d22fe7fb4b1a3d6` | `v0.16.14` | 2026-07-18T19:15:16Z | canonical | `main` |
| 0.16.14 | `Arbor-0.16.14-source(1).zip` | `7ee55b9ec42a71c1c7956969cc3c22fd0993d9dad286bae9b6dbb9b1cdfe0621` | `2e7f79a037c9010d878f5d105d22fe7fb4b1a3d6` | `v0.16.14` | 2026-07-18T19:15:16Z | content-duplicate | `` |
| 0.16.16 | `Arbor-0.16.16-source.zip` | `8e23eccd97528e673b674f4c38425e62e073795079bb414ebf7fd0d200c61dc5` | `1401ec32d456895968136f472d7ce71a823160c4` | `v0.16.16` | 2026-07-18T20:53:06Z | canonical | `main` |
| 0.16.17 | `Arbor-0.16.17-source.zip` | `a7bc30817c0ab84cb1d62b40ab0caa3baf8e2cd51271155fed1a0235087bbdf6` | `328d51b809ea8aefb6adae3b53f35222597207bd` | `v0.16.17` | 2026-07-18T21:24:14Z | canonical | `main` |
| 0.16.18 | `Arbor-0.16.18-source.zip` | `2852776fb58da0a27f00393812c8b0aa5f8258bc9d5427c538b50c3a74832d80` | `5e9611cadbf50d86b3365e672854a484e8fa8d0a` | `v0.16.18` | 2026-07-18T22:00:20Z | canonical | `main` |
| 0.16.19 | `Arbor-0.16.19-source.zip` | `6de9fe6831c25192970e4c39acd6b8edef744fe4d7929555c6092450dcf7c86e` | `0c3fcfc25b98d24ce23fce843e2b1610378a6b4f` | `v0.16.19` | 2026-07-18T23:02:58Z | canonical | `main` |
| 0.16.20 | `Arbor-0.16.20-source.zip` | `ffb804c9b43ab6bcef5eea57cb896141891161edbca71d3c8918639ac4f33cf1` | `fb1d95f83e5bea9bdf1c18c80c6cd3c3760e9003` | `v0.16.20` | 2026-07-19T00:50:16Z | canonical | `main` |
| 0.16.21 | `Arbor-0.16.21-source.zip` | `e3db40545d19f835a05e9eb105fb9dbea27a7c33b9e2fe883a08903437d60914` | `2984878366027f5f08e8323614ae12bb92f9b3cb` | `v0.16.21` | 2026-07-19T01:10:48Z | canonical | `main` |
| 0.16.22 | `Arbor-0.16.22-source.zip` | `64a51d316bd7e49aaeb25aca8de1e45fe5f248316e344ed3119803a470225c58` | `1721d9f293131a1ecac4d2082dfaee5b60d0a629` | `v0.16.22` | 2026-07-19T01:49:26Z | canonical | `main` |
| 0.16.23 | `Arbor-0.16.23-source.zip` | `fc7b0b3bee63d43184ac6e38d564408963eb97f1f8828a89abb23b2da72b3fe4` | `d6a5b802af512240c24e2ce2138f85bd7cddba70` | `v0.16.23` | 2026-07-19T02:29:54Z | canonical | `main` |
| 0.16.24 | `Arbor-0.16.24-source.zip` | `250c4613ea01a37813980c9da4a00d712608649ba0d0eb7d1e5d5eb0e5f49cf4` | `86cc75470bf807b0bbb5343f0794b54ec340f178` | `v0.16.24` | 2026-07-19T03:08:58Z | canonical | `main` |
| 0.16.25 | `Arbor-0.16.25-source.zip` | `577bf1ed78dc939737bda44087811ad17727b79682b5232fb74fad2413ad4a9c` | `601d03be69e1745b527950bf7cb4648b76c06ffd` | `v0.16.25` | 2026-07-19T11:29:22Z | canonical | `main` |
| 0.16.26 | `Arbor-0.16.26-source.zip` | `5f410fc273140266a2401f805df62ae1e9cb64892216b76d4400e71aacb36121` | `015e3aba01c410c0464e5ec15387820265f46dae` | `v0.16.26` | 2026-07-19T12:30:50Z | canonical | `main` |
| 0.16.27 | `Arbor-0.16.27-source.zip` | `a2816bb741c0cf673c623494d5f4639ce9e89f9d2ad8ae26ea7a74e174c31668` | `1b13c438957a65c5074ede623d4b05b105db49ff` | `v0.16.27` | 2026-07-19T12:52:10Z | canonical | `main` |
| 0.16.28 | `Arbor-0.16.28-source.zip` | `053d0956c59daea7eef05bf9245aceccb072723df357a33f2cc0ceed66cc56fe` | `aabfda71621e0924778a8b040b42f14666e636cb` | `v0.16.28` | 2026-07-19T13:26:50Z | canonical | `main` |
| 0.16.29 | `Arbor-0.16.29-source.zip` | `3f43e10cdfa23f096c2b873e2b080f35eed9018c71ef68d4dba47fd3452d81ed` | `554fe0ae0fb508568c3a11af29e152db08b88e32` | `v0.16.29` | 2026-07-19T14:13:04Z | canonical | `main` |
| 0.16.30 | `Arbor-0.16.30-source.zip` | `1efd82a6ac9d35afeeb35d431ecf5ae7447e412107d0caebe4d21218bceb2010` | `68d2c7f5da5fd86ae7f5568656a035889a96868e` | `v0.16.30` | 2026-07-19T14:54:22Z | canonical | `main` |
| 0.16.31 | `Arbor-0.16.31-source.zip` | `15a86d04a59f5f80adda3ca6550678751200584df805e6bf81bc5ce3159a70ae` | `140d30aa446ba4d608818ca0c638aa4b37cec443` | `v0.16.31-original` | 2026-07-19T16:28:40Z | variant-original | `variant/v0.16.31-original` |
| 0.16.31 | `Arbor-0.16.31-source(1).zip` | `8d161176a0f35627c809f6a8379e920d99d2e67d4e890764e903bfdea166a7ee` | `011b0ef9d69b9dc1dff20e935dd53692ed97f126` | `v0.16.31` | 2026-07-19T21:39:10Z | canonical | `main` |
| 0.16.32 | `Arbor-0.16.32-source.zip` | `e1e6ea8d941103a4ce344467e6b9086a6180c0dc81bdf12081255bd3f713d4a3` | `395129b076fccadb4a9301d4ad496879ea9ae744` | `v0.16.32-original` | 2026-07-19T17:47:32Z | variant-original | `variant/v0.16.32-original` |
| 0.16.32 | `Arbor-0.16.32-source(1).zip` | `a46c99e6aaccca768630511a278df3ec4733d725db036915cd1617f927e88591` | `a75ad047238cc974b710512e2306db899c82e0ef` | `v0.16.32` | 2026-07-19T18:31:32Z | canonical | `main` |
| 0.16.33 | `Arbor-0.16.33-source.zip` | `bec5c1ece1cec6ab4895a327fba5c5184c13696ba791924282b44e1d479464e0` | `e5883b3875a012110100fbfcb1cf4f2877f5aa48` | `v0.16.33` | 2026-07-19T19:01:48Z | canonical | `main` |
| 0.16.34 | `Arbor-0.16.34-source.zip` | `e2419f8c1b609ec39a8e138d58a04b726a33fe989cb6804fe8ef6c9ce61d9c23` | `b2bf4a82153d4a76289e6aeb887110fd208b3e04` | `v0.16.34` | 2026-07-19T19:16:56Z | canonical | `main` |
| 0.16.35 | `Arbor-0.16.35-source.zip` | `c8552e750ad976f9e571f0acc7ee59fdcd68f70c83a96833b7e6c4e603e3aacd` | `150952a963c23319dadf663dde6c824aba4aaf7d` | `v0.16.35` | 2026-07-19T19:48:52Z | canonical | `main` |
| 0.16.40 | `Arbor-0.16.40-source.zip` | `00ebbe1c70cd2315a95bca7a53175280891cdcb94dcd5bb1184fc22088e56f75` | `864542f888397ef3d6a00e34965efcdf97c199cc` | `v0.16.40` | 2026-07-19T20:37:38Z | canonical | `main` |
| 0.16.41 | `Arbor-0.16.41-source.zip` | `a0ef39fa514062dc569e292098669d1d0f89cd438e4cee7858139cfd2a1310ba` | `339d48fea50c9d38e05dc8d648b5b063b059e544` | `v0.16.41-original` | 2026-07-19T21:22:58Z | variant-original | `variant/v0.16.41-original` |
| 0.16.41 | `Arbor-0.16.41-source(1).zip` | `8a11557b9f36554073c0713e26c58e1443484889d8e05faba074caf563159a74` | `cb3b6c1c108d46a7d6c256cbbcc82743bae97e07` | `v0.16.41` | 2026-07-19T21:39:10Z | canonical | `main` |
| 0.16.42 | `Arbor-0.16.42-source.zip` | `1745afa0fa1695212aa9563083baabc2b7cee3b4928e7083538e8b70e06808aa` | `43c8c73306c0742753b2ad8c351ceac05f454bf8` | `v0.16.42` | 2026-07-19T22:41:16Z | canonical | `main` |
| 0.16.43 | `Arbor-0.16.43-source.zip` | `33cd6fd326fbb4ee3715c74da1e0e756e4b9ebbbb58868fc53d07f6733efaf5e` | `0b5a1af42c585b065b3d154de8c7ee17e9faf0d5` | `v0.16.43` | 2026-07-19T23:15:52Z | canonical | `main` |
| 0.16.44 | `Arbor-0.16.44-source.zip` | `71afd6e0011ed32f272d168f5f6dd21a38f30c4b105c967a3614db0966ecfb00` | `c6f6a38d00893cb73bc0f53567a2cee5fa97ae51` | `v0.16.44` | 2026-07-20T00:41:32Z | canonical | `main` |
| 0.16.45 | `Arbor-0.16.45-source.zip` | `d61eed58eec7659e4f39154aa47fca7739eea12fef318e9dde67449dbf6f5fdb` | `fb0a25a77bb98df9f69a18c8e07b2c6b25584f0d` | `v0.16.45` | 2026-07-20T01:18:16Z | canonical | `main` |
| 0.16.45 | `Arbor-0.16.45-source(1).zip` | `d61eed58eec7659e4f39154aa47fca7739eea12fef318e9dde67449dbf6f5fdb` | `fb0a25a77bb98df9f69a18c8e07b2c6b25584f0d` | `v0.16.45` | 2026-07-20T01:18:16Z | duplicate | `` |
| 0.16.46 | `Arbor-0.16.46-source.zip` | `63579f0e5bd6df8e0c3b5b845c8f5801db3e8df78daa966ff9ad41db4e3d9f8a` | `70532c4bd8be4e1a1a19c0e51ded093f7cd76d5a` | `v0.16.46` | 2026-07-20T02:18:40Z | canonical | `main` |
| 0.16.47 | `Arbor-0.16.47-source.zip` | `ff6bc673fd08b27424b37d153c37f50bcf5562286c9b8e7a987a498c100bc18d` | `06aa83f08dffc98ed449f8224b248983ce6403dc` | `v0.16.47` | 2026-07-20T03:04:08Z | canonical | `main` |
| 0.16.48 | `Arbor-0.16.48-source.zip` | `135a0b23f8844f0a0d82fdccba3222f5932e31d43d8ee4b652bf0fe89d3e092f` | `45db9490d546e382a364b8a5385f2735b0e82576` | `v0.16.48` | 2026-07-20T03:53:22Z | canonical | `main` |
| 0.16.49 | `Arbor-0.16.49-source.zip` | `3642e305a2f1bafde8953d8e0c2ba7a1c0a71e0cd9401ab18813d25a368967db` | `05289b8c35ad30d4d6c7a1a7037b6038e42e32ba` | `v0.16.49` | 2026-07-20T12:48:34Z | canonical | `main` |
| 0.16.50 | `Arbor-0.16.50-source.zip` | `01ea3e1943c15507b7b632b23aeb8f39b908cfaca4f6cb404ab30925f0e258d6` | `e4c5089e76d4b751c2102bc3b52f698d2e84cdcd` | `v0.16.50` | 2026-07-20T13:15:20Z | canonical | `main` |
| 0.16.51 | `Arbor-0.16.51-source.zip` | `76d35277b427bacf3c075c87d6eb367c449917f4bab62a172c43e2cf9441047b` | `029f3aab24673b611ccd6cda319deb2ee121fd49` | `v0.16.51` | 2026-07-20T15:08:44Z | canonical | `main` |
| 0.16.52 | `Arbor-0.16.52-source.zip` | `59cc33ca1d7a03922cb3a4278c9a63bb682fcc35f9934bc23796dd825538ed41` | `5b1e04e1302191495df125ff9f5c9730d2cd18d7` | `v0.16.52` | 2026-07-20T20:22:36Z | canonical | `main` |
| 0.16.52 | `Arbor-0.16.52-source(1).zip` | `59cc33ca1d7a03922cb3a4278c9a63bb682fcc35f9934bc23796dd825538ed41` | `5b1e04e1302191495df125ff9f5c9730d2cd18d7` | `v0.16.52` | 2026-07-20T20:22:36Z | duplicate | `` |
| 0.16.53 | `Arbor-0.16.53-source.zip` | `512e2a4cc075dcff015e07a03568b3261d2091098aee7ee97525d90d8275884c` | `2ed8acefb7c0b04423ea6cae33e2c4e844f7aecd` | `v0.16.53` | 2026-07-20T22:19:08Z | canonical | `main` |
| 0.16.54 | `Arbor-0.16.54-source.zip` | `14a9339561ed821d487041933000e3208e28a6609be15a14bcc319f9ce6e51eb` | `15c308f4586dd09a4323ebf7d57afe47f49f6582` | `v0.16.54` | 2026-07-20T23:56:14Z | canonical | `main` |
| 0.16.55 | `Arbor-0.16.55-source.zip` | `477393380124d3f896d144c52d577e99dbec7dab46c8ec3497df47df1586e77e` | `8b5752c9fbab1c7452a7bb22e515a572c20d69e9` | `v0.16.55` | 2026-07-21T01:53:34Z | canonical | `main` |
| 0.16.56 | `Arbor-0.16.56-source.zip` | `32c42a5a4ec5a4522e123a6d90a5bca76eb111b217a5963a9a5b16da22f2bb43` | `a3f2db680108724fc99c9dd144c4395d7de460b4` | `v0.16.56` | 2026-07-21T02:36:36Z | canonical | `main` |
| 0.16.57 | `Arbor-0.16.57-source.zip` | `343abfa4dc980ec56bf78b83d6fdf52f03a5774db10de42312944ab77a505c4e` | `b76ac8842ded1a30e27cbd391ff6a76283efe847` | `v0.16.57` | 2026-07-21T12:04:58Z | canonical | `main` |
| 0.16.58 | `Arbor-0.16.58-source.zip` | `d877d605c5e8eb2fc4dd67d2789d74eed21534c50c556855bcdd94555efec3ab` | `d078af60aa35dac271f5139c362e200013dee9a5` | `v0.16.58` | 2026-07-22T01:57:32Z | canonical | `main` |
| 0.16.59 | `Arbor-0.16.59-source.zip` | `46ba44e09e34d52a4002f61ce27c3622da6c2b6a6fd35e7b6599ccfda7abe3ad` | `f5b248f9884822b21896ab88e56d0f8d6dea5be7` | `v0.16.59` | 2026-07-22T11:16:58Z | canonical | `main` |
| 0.16.60 | `Arbor-0.16.60-source.zip` | `9dc3134d1190edf0ca6dfd121c3e13e096987fd4ef7317c414d57a496be6f853` | `1208f027f83bcc5fdb46748359a55526625c46a2` | `v0.16.60` | 2026-07-22T12:34:52Z | canonical | `main` |
| 0.16.60 | `Arbor-0.16.60-source(1).zip` | `9dc3134d1190edf0ca6dfd121c3e13e096987fd4ef7317c414d57a496be6f853` | `1208f027f83bcc5fdb46748359a55526625c46a2` | `v0.16.60` | 2026-07-22T12:34:52Z | duplicate | `` |
| 0.17.0 | `Arbor-0.17.0-source.zip` | `3b61bb7fc1ea6fa7be1cd9e5a5b3865729ec4e5d3242af79c18f77b8c27476a6` | `adbc7ffb40021e2ad64aa55dadd9012d03d37116` | `v0.17.0` | 2026-07-22T17:00:04Z | canonical | `main` |
| 0.17.1 | `Arbor-0.17.1-source.zip` | `bbd93e2413b6d6694fa4061554954126747d792738d917bfadf53b5382eb2814` | `c217a935b93650f3f16ca945a86f21d01e694235` | `v0.17.1` | 2026-07-24T17:21:34Z | canonical | `main` |
| 0.17.2 | `Arbor-0.17.2-source.zip` | `d981ec7b1099b045cad6d133eda3660ca0eaf82ca7e9b787239ac81591dbf098` | `d7619d130120c5551dafb22c3c51663730e4b06a` | `v0.17.2` | 2026-07-24T22:01:18Z | canonical | `main` |
| 0.17.3 | `Arbor-0.17.3-source.zip` | `311a7102ce034a3b79a642280d266e21a2b171585ea5b28f8ceb23ab3f81dc47` | `77eac679a5f4597fec5423c7c2bacede86d1080f` | `v0.17.3` | 2026-07-24T22:36:48Z | canonical | `main` |
| 0.17.4 | `Arbor-0.17.4-source.zip` | `c24b8751ac842d68f89d6c725a813fa7f5e1dd8fc07a7a8e9678a9c270e385b1` | `6ec4fce4d26491f35141c439c280cc4f77126c7e` | `v0.17.4` | 2026-07-25T14:59:56Z | canonical | `main` |
| 0.17.6 | `Arbor-0.17.6-source.zip` | `97ad37a54adc3a89628a291def1ddf85309a283ca2f97909cbb74455b8bc5343` | `2436e9b5b1a9ed507a0c9ebfc388306fd5acc6ee` | `v0.17.6` | 2026-07-25T15:53:42Z | canonical | `main` |
| 0.17.7 | `Arbor-0.17.7-source.zip` | `c1c665fbc91aa1e915b6f9617415f0ef9b70f2fd2fa192b1965790742eec2af5` | `81301633150845ed79ef8c5f150fc6649e066a90` | `v0.17.7` | 2026-07-25T18:09:18Z | canonical | `main` |
| 0.17.8 | `Arbor-0.17.8-source.zip` | `e40d11c7fbfa730f62a3b8ed9be2d673f5fa74c689e971e1cbb06563891b54c2` | `89ef942db94622f447d1d78018c2073e535aeda1` | `v0.17.8` | 2026-07-25T19:14:06Z | canonical | `main` |
| 0.17.8 | `Arbor-0.17.8-source_2.zip` | `e40d11c7fbfa730f62a3b8ed9be2d673f5fa74c689e971e1cbb06563891b54c2` | `89ef942db94622f447d1d78018c2073e535aeda1` | `v0.17.8` | 2026-07-25T19:14:06Z | duplicate | `` |
| 0.17.9 | `Arbor-0.17.9-source.zip` | `16fcde874b1050b0ebe9074348cfcbebbe03fd05ac1808f366de7e58ed0a979d` | `45e7320958b5c84a0549695ec4a522026cc92895` | `v0.17.9` | 2026-07-25T20:01:56Z | canonical | `main` |
| 0.17.10 | `Arbor-0.17.10-source.zip` | `8d925f05845e3ef576a1e962d60877b567a1c87e04bf1e80636a639cb742e763` | `3542292844bb0bd988fa58b3b03146ad66146838` | `v0.17.10` | 2026-07-25T20:37:24Z | canonical | `main` |
| 0.17.11 | `Arbor-0.17.11-source.zip` | `e6c38578e1bb52b178e68dc7fd911ef3ade3ec1450229c1b6e8fa96ac916b13c` | `0d38550365fd65c475e77955bde961e585beca6e` | `v0.17.11` | 2026-07-25T21:24:20Z | canonical | `main` |
| 0.17.12 | `Arbor-0.17.12-source.zip` | `1c095ebdf5906643b19fb036ae03bfeab4763836d1d870654dbc0e6d8dee5247` | `585449287563a2619c3814c709db788101aa2995` | `v0.17.12` | 2026-07-25T21:54:38Z | canonical | `main` |
| 0.17.13 | `Arbor-0.17.13-source.zip` | `77bfa8b756bce042dde44ae9bffe53dbaf81c0547687463d46bb91d9251b44cb` | `da8fad9f2c1863392f904485f299804235f97f10` | `v0.17.13` | 2026-07-25T22:14:54Z | canonical | `main` |
| 0.17.14 | `Arbor-0.17.14-source.zip` | `aa9920209715fe5be67f152194f679aed3a9b688910539daa8dbb1b3e977b470` | `ddbdc31271fb23e2aac6e18b9167fe4031021be6` | `v0.17.14` | 2026-07-25T22:54:14Z | canonical | `main` |
| 0.17.14 | `Arbor-0.17.14-source-checkpoint.zip` | `aa9920209715fe5be67f152194f679aed3a9b688910539daa8dbb1b3e977b470` | `ddbdc31271fb23e2aac6e18b9167fe4031021be6` | `v0.17.14` | 2026-07-25T22:54:14Z | duplicate | `` |
| 0.17.15 | `Arbor-0.17.15-source.zip` | `8e06ebaa8609218449365d85144ea41c73157db2f6360a8a33633c6a67f6a9a6` | `87b5a52bb29ad891e8f27c4209d22f3d16ce10d0` | `v0.17.15` | 2026-07-25T23:11:34Z | canonical | `main` |
| 0.17.15 | `Arbor-0.17.15-source-checkpoint.zip` | `8e06ebaa8609218449365d85144ea41c73157db2f6360a8a33633c6a67f6a9a6` | `87b5a52bb29ad891e8f27c4209d22f3d16ce10d0` | `v0.17.15` | 2026-07-25T23:11:34Z | duplicate | `` |
| 0.17.16 | `Arbor-0.17.16-source.zip` | `66459288acd0ab8b03bc0f75e04d58539633124bb1bd7bca0a1b2041bc53a6b2` | `62de400f046658ef5d3e0a60ce789294894c92cd` | `v0.17.16` | 2026-07-25T23:31:50Z | canonical | `main` |
| 0.17.16 | `Arbor-0.17.16-source-checkpoint.zip` | `66459288acd0ab8b03bc0f75e04d58539633124bb1bd7bca0a1b2041bc53a6b2` | `62de400f046658ef5d3e0a60ce789294894c92cd` | `v0.17.16` | 2026-07-25T23:31:50Z | duplicate | `` |
| 0.17.17 | `Arbor-0.17.17-source.zip` | `f1b344a3180c73b72e85e7399880264755d855523894f955a7008fd932f5c02a` | `e5352b110c98314da52ba203c2824e5059998eef` | `v0.17.17` | 2026-07-26T12:26:42Z | canonical | `main` |
| 0.17.18 | `Arbor-0.17.18-source.zip` | `304f6793dff532b8aeea6c7d290ceaf7dd17c56bdb9f3b517f4e4c8b36a1bf58` | `80fba282b8c08bcd58f9621a03677023d964e3a9` | `v0.17.18` | 2026-07-26T13:10:30Z | canonical | `main` |
| 0.17.18 | `Arbor-0.17.18-source_2.zip` | `304f6793dff532b8aeea6c7d290ceaf7dd17c56bdb9f3b517f4e4c8b36a1bf58` | `80fba282b8c08bcd58f9621a03677023d964e3a9` | `v0.17.18` | 2026-07-26T13:10:30Z | duplicate | `` |
| 0.17.19 | `Arbor-0.17.19-source.zip` | `d0c08a04717328e5ac1338223f3621cc6cb07b8d7a8181fe204d7971ac1a4cd5` | `04a4cb59d001fd769bfe49bbeedd1fa31676898e` | `v0.17.19` | 2026-07-26T13:51:46Z | canonical | `main` |
| 0.17.19 | `Arbor-0.17.19-source_2.zip` | `d0c08a04717328e5ac1338223f3621cc6cb07b8d7a8181fe204d7971ac1a4cd5` | `04a4cb59d001fd769bfe49bbeedd1fa31676898e` | `v0.17.19` | 2026-07-26T13:51:46Z | duplicate | `` |
| 0.17.25 | `Arbor-0.17.25-source.zip` | `cd38de8f1e514253dff4cc0e89f9c45a97d7a0db25b4506a7ce1f4313faa9d8e` | `f48fdec880ae10911198f13f35307839ddd28f88` | `v0.17.25` | 2026-07-26T18:23:36Z | canonical | `main` |
| 0.17.26 | `Arbor-0.17.26-source.zip` | `cb86c1ae9fb7063e29a8bb031a041e93074b7fbf6e32101c0e8b007fc2e9d724` | `7ac556fc6e24b835965737966838c14ba9492fa3` | `v0.17.26` | 2026-07-26T18:56:56Z | canonical | `main` |
| 0.17.27 | `Arbor-0.17.27-source.zip` | `37727d32ed1f749209f12143b0575b14062d047a8e5b2bc88dd174d0955cb3e5` | `bdcc9fc14931ed0a9317d2537f6797ccf93c1434` | `v0.17.27-original` | 2026-07-26T20:26:30Z | variant-original | `variant/v0.17.27-original` |
| 0.17.27 | `Arbor-0.17.27-source(1).zip` | `da5d6fe7975d3d7d86a6ee94a435f42ecb3b23388e57d06580ebb8bafa39704b` | `6f90efc5619010833cb3116a01ec2aa98da7d2dd` | `v0.17.27` | 2026-07-26T21:10:52Z | canonical | `main` |
| 0.18.0 | `Arbor-0.18.0-source.zip` | `61049a45179f2f420f352d1ed31e42def6c9ee307ca2268f755f368255fd94ed` | `68f7dd93fdf26625ba25af35055f8cf41277b6ff` | `v0.18.0` | 2026-07-26T21:48:44Z | canonical | `main` |
| 0.18.1 | `Arbor-0.18.1-source.zip` | `b2a090f8ed65afa93a59bc0df7f061533276fa9a5640dc451ee7fa443a068238` | `8e44bcf464b83c1a2f614874e6d702da4e56646f` | `v0.18.1` | 2026-07-26T22:17:58Z | canonical | `main` |
| 0.18.2 | `Arbor-0.18.2-source.zip` | `53cbe22191e81a1744c3042447bbd78ca727a8410373b157ed8dbd30ffccde37` | `b2823787cf0f503cecdab8c3ec5432f625c9d20f` | `v0.18.2` | 2026-07-26T23:10:20Z | canonical | `main` |
| 0.18.3 | `Arbor-0.18.3-source.zip` | `767bc3f94d347b5132341c6ddf7c97f94c8d11b3cf5100ffe9a627fd7e94a480` | `44038f5b2c5c6c86b0c37296736b9c1eea204ff2` | `v0.18.3` | 2026-07-26T23:54:58Z | canonical | `main` |
| 0.18.4 | `Arbor-0.18.4-source.zip` | `4aabccb1bce7c53337e59e44700e2e1b47160b5bb704f074ea7fe0760726740a` | `96b3ed98045b7be267c056939569a04355e4ee5c` | `v0.18.4` | 2026-07-27T01:37:52Z | canonical | `main` |
| 0.19.0 | `Arbor-0.19.0-source.zip` | `29070c7c08ccb202a880ef8626fabcdd023fc006594bee3aac4a90e59af70e34` | `8682633868ce966760f17ab8fc0b6fb88dcd3225` | `v0.19.0` | 2026-07-27T02:23:28Z | canonical | `main` |
| 0.19.1 | `Arbor-0.19.1-source.zip` | `5161ff14471812b855d2a0f87a873030dea1f4260534d9b11327f928c7b8a120` | `ba269afffb43525773ac6f8ed6c035f273c63402` | `v0.19.1` | 2026-07-27T02:48:16Z | canonical | `main` |
| 0.19.2 | `Arbor-0.19.2-source.zip` | `47c51aa9ee9d814125685b60581b5eb55a03804bfec233845bab62430a652a5c` | `5b671e8b7ec42579e6da9e41f8d841469ca1ce17` | `v0.19.2` | 2026-07-27T13:35:02Z | canonical | `main` |
| 0.19.2 | `Arbor-0.19.2-source(1).zip` | `47c51aa9ee9d814125685b60581b5eb55a03804bfec233845bab62430a652a5c` | `5b671e8b7ec42579e6da9e41f8d841469ca1ce17` | `v0.19.2` | 2026-07-27T13:35:02Z | duplicate | `` |
| 0.19.3 | `Arbor-0.19.3-source.zip` | `8a6aa7287deaee49025bd9bd4a0a6ef6949fbebda340736426598d936a795496` | `327675684fc489aec8890fc026f2054929ae37f4` | `v0.19.3` | 2026-07-27T14:27:40Z | canonical | `main` |
| 0.19.3 | `Arbor-0.19.3-source(1).zip` | `8a6aa7287deaee49025bd9bd4a0a6ef6949fbebda340736426598d936a795496` | `327675684fc489aec8890fc026f2054929ae37f4` | `v0.19.3` | 2026-07-27T14:27:40Z | duplicate | `` |
| 0.19.4 | `Arbor-0.19.4-source.zip` | `15c4aa7eeb5599c90cf120f1e0ff9a90e9b86adf30790067390f99647d45e60a` | `a093e5b4977dc265c746faabc9009904c7506f9e` | `v0.19.4` | 2026-07-27T15:12:30Z | canonical | `main` |
| 0.19.4 | `Arbor-0.19.4-source(1).zip` | `15c4aa7eeb5599c90cf120f1e0ff9a90e9b86adf30790067390f99647d45e60a` | `a093e5b4977dc265c746faabc9009904c7506f9e` | `v0.19.4` | 2026-07-27T15:12:30Z | duplicate | `` |
| 0.19.5 | `Arbor-0.19.5-source.zip` | `766b3c86da6119b685d0d1f97bbff36d02974078eee80e9f6ef4686087e2b99b` | `fc29c3a4de385e5e5bba5104c0113cf0509486a1` | `v0.19.5` | 2026-07-27T17:33:10Z | canonical | `main` |

| 0.19.6 | `Arbor-0.19.6-source.zip` | `76e10efd9d32be8edd80e86a1fcf62b3a447456224100b417714190e10ae99c0` | `f5437271d99093ab2585654da0085caa295f9a1a` | `v0.19.6` | 2026-07-27T18:46:40Z | canonical | `main` |

| 0.19.7 | `Arbor-0.19.7-source.zip` | `95bc6a241bcd140f134f137fe9402c901b65cf43b35dd05ad9864a4aa6436136` | `62f6f71af012b184328a64a7dba26b46340be296` | `v0.19.7` | 2026-07-27T19:35:00Z | canonical | `main` |

| 0.19.8 | `Arbor-0.19.8-source.zip` | `6724b043407a8fe0da11bbf58119018a3c555647821d95db7f6fbcbe03084a48` | `d9b397e9f4d9892f337c3ce878831c064f581bb6` | `v0.19.8` | 2026-07-27T20:19:32Z | canonical | `main` |

| 0.19.9 | `Arbor-0.19.9-source.zip` | `04211a8872d7e44fd016a308fe0290f85fb49db4ae724049a020dbaa2b61b528` | `ac67f8dd2f2c6a67e55b8f8cc9b832da850128f7` | `v0.19.9` | 2026-07-27T21:06:00Z | canonical | `main` |

| 0.19.10 | `Arbor-0.19.10-source.zip` | `afe3a74db82eaab47be13e7ef7a62fe2a8435892a4717a4de04fba37d028556c` | `6710f04d5046429c564ba77f0685e02cb85f0a03` | `v0.19.10` | 2026-07-27T21:55:00Z | canonical | `main` |

| 0.19.11 | `Arbor-0.19.11-source.zip` | `7b6eca0fc481723da09fb69ef859c56bfc1d14b6a475ba42d711f071fd4ca15e` | `937409b98623cebe44a596728ded5b9f3a086ad7` | `v0.19.11` | 2026-07-27T23:33:03Z | canonical | `main` |

## Canonical variant selection

- **0.16.31:** the later `(1)` archive is canonical; it adds the corrected scrolling/streaming implementation and regression test. The earlier tree is `v0.16.31-original`.
- **0.16.32:** the later `(1)` archive is canonical; its corrected Markdown/scroll implementation supersedes the earlier upload. The earlier tree is `v0.16.32-original`.
- **0.16.41:** the later `(1)` archive is canonical; it corrects version code 56 to 57 and includes the release notes/tests. The earlier tree is `v0.16.41-original`.
- **0.17.27:** the later `(1)` archive is canonical; it contains the corrected blur implementation and matching regression test. The earlier tree is `v0.17.27-original`.

## Source unavailable in Library

`0.16.13`, `0.16.15`, `0.16.36`, `0.16.37`, `0.16.38`, `0.16.39`, `0.17.5`, `0.17.20`, `0.17.21`, `0.17.22`, `0.17.23`, `0.17.24`

No source commit was fabricated from checksum, verification, release-note, or skills files. A missing version is reconstructable only when an exact parent and exact applicable patch can be verified; no such reconstruction was used in this import.

## Excluded generated/private files

`.gradle/`, `.kotlin/`, every `build/` directory, APK/AAB outputs, `.git/`, `local.properties`, IDE workspace/cache state, `.jks`, `.keystore`, and temporary extraction files.
