# Changelog

## [1.4.0](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v1.3.0...v1.4.0) (2026-01-14)


### Features

* use JSON Credential Schema for credential generation ([#100](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/100)) ([456601a](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/456601aa0de04b0dcc861c6fc3f67cf62d0beab0))


### Bug Fixes

* tpl to schema ([#103](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/103)) ([8b09696](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/8b09696f5d7376919d84868398d7494e8cb5c677))
* update credential schema ([#101](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/101)) ([f03e548](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f03e5484cdaf72c785b40d46ec20ad4a046b1e9c))
* update vs agent ([#97](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/97)) ([518a7b5](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/518a7b528ed9d83396d6f7a027f54eb7fc591ed0))
* update vs agent ([#98](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/98)) ([0d62c57](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/0d62c578fa491713355eef91ffc6a85e3747b77b))
* update vs agent to stable v1.6.0 ([#102](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/102)) ([ed29b59](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/ed29b59bbc581adaf141d16115faff1fb6de43ab))
* update vs agent unstable version ([#95](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/95)) ([1912799](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/19127994359c43012e1f740969cba24d609048ec))

## [1.3.0](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v1.2.0...v1.3.0) (2025-09-30)


### Features

* update yarn to pnpm ([#90](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/90)) ([f658e2f](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f658e2f483a8535f46eead034a23a1b76aa87858))


### Bug Fixes

* health port ([#87](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/87)) ([f564a96](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f564a9602957c3b5aa72f9f24038c48aaf8d6340))
* support sha-512 for eMRTD verification ([#93](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/93)) ([cd3b9d0](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/cd3b9d0c8a59df109d03e2d286071646d7cb7d09))

## [1.2.0](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v1.1.0...v1.2.0) (2025-09-16)


### Features

* eMRTD authenticity verification ([2128f54](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/2128f545c3f27d7fd87d3b51a06300ce71f3adaa))

## [1.1.0](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v1.0.3...v1.1.0) (2025-09-16)


### Features

* add health endpoint and k8s liveness/readiness probes ([#79](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/79)) ([0bb0dab](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/0bb0dab6f8e2e329306b39b6a48d5f3aebd03f91))
* **chart:** update vs-agent dep to latest dev version ([#80](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/80)) ([6738608](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/67386084de291d5f9e7612ed004976cd56b8b1ba))
* **chart:** update vs-agent to latest dev version ([#85](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/85)) ([e445b26](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/e445b26cbe628749cbac76a55346af090e68abcb))


### Bug Fixes

* add response validation on webrtc and vision start process ([#76](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/76)) ([5ad6758](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/5ad6758e0f4e2649578628b9a9fa3e1293fb60ef))
* call vision with a default language in case preferred language from user is not found ([#75](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/75)) ([79d4571](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/79d457133db8de62d334e79cdabb1e5e37c18caa))

## [1.0.3](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v1.0.2...v1.0.3) (2025-06-27)


### Bug Fixes

* increase request limit to 5MB ([#73](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/73)) ([ea0d287](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/ea0d28798a46d47117cc418f9833f8be11e58fe6))

## [1.0.2](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v1.0.1...v1.0.2) (2025-06-18)


### Bug Fixes

* update url chart ([#70](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/70)) ([540a1e1](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/540a1e1b6d9f2b907fd73d72d0ec38e8f7381bb3))

## [1.0.1](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v1.0.0...v1.0.1) (2025-06-17)


### Bug Fixes

* release-please version ([c0fe9aa](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/c0fe9aa70e634eac08561ea4f8edd0bef3a5403c))
* stable-release name ([c9a81fb](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/c9a81fbc887da7828638dd64ff4d40ff879a1374))

## [1.0.0](https://github.com/2060-io/hologram-gov-id-issuer-vs/compare/v0.0.5...v1.0.0) (2025-06-17)


### Features

* add fmt to clean phase ([f89d110](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f89d1104ddaeff920f45ef809bb110325f189d35))
* add linter ([a112d98](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/a112d98d86c6ca280c39edf50d83fa5fc2d87390))
* add linter ([e70da7e](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/e70da7e1206c5374cdba90e98b75384cbbe436f5))
* add mrz data ([#49](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/49)) ([a260ad0](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/a260ad0a9fbd6ad44c21415907ad3269843c8a1e))
* add mrz validation ([#46](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/46)) ([3c6d050](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/3c6d0505c0d9375d71cca75351e00d76d021afc7))
* add NFC detection capability ([#47](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/47)) ([85dcbba](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/85dcbba9910af4f3d3ae3a8dc12c70b27c63bed0))
* add preferred language support ([#6](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/6)) ([1afa550](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/1afa550a34ea4864fccb96a578f391aedce04d13))
* add used to library ([32f27c6](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/32f27c64273b11fda67f4973d65aebc56a91b343))
* add used to library ([9d2a990](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/9d2a9907b2655a0117ed0d2af971b8948ff698c7))
* adjust messages ([1a04500](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/1a04500346029eee5d11e1049ec43e86687654d2))
* adjust structure ([fca989b](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/fca989b621e918c6a32af4d1f41234520839d878))
* apply config environments on nestjs ([#28](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/28)) ([9d768dd](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/9d768dd33ccb4279644dd5fdd0cfdcf342fb8c0c))
* create basic structure ([#1](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/1)) ([0fc9ec4](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/0fc9ec47b0b472882c62f9e567ccb221c112a1b8))
* implement legacy validation ([8f10d17](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/8f10d17bc35e3a24ac8c0bde1cbc5a598cf0f69f))
* implement mrtd protocol ([a9da93d](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/a9da93dc2b44446a3a24ec148099ad6dfa2d2720))
* implement mrtd protocol ([be03722](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/be03722627d712bf942013d6215cd5dc18d26fbd))
* implement nfc reading ([#10](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/10)) ([f3915ae](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f3915aee31ced842e5ef8e32c82960ded428ab2e))
* improve connection handling and capability validation ([#48](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/48)) ([29bdc5d](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/29bdc5d18e6a3d666b3cf4cbaf1df81f2b10d098))
* interaction with WebRTC Server and Vision ([#3](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/3)) ([d65d536](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/d65d53666fa0f422c01cb405e7c9088db528606b))
* issue revocable credentials ([#43](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/43)) ([e1c3170](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/e1c3170dd0603e10dae5973bd96269bc6b20233b))
* needed on menu and identity management ([#21](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/21)) ([7c5eb71](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/7c5eb7191bc0be6d755ac06d34bf05a40462767b))
* refactor code to Node.js ([#25](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/25)) ([b546f1a](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/b546f1aa803ae336653952b3105c43202924d0e4))
* remove confirm mrz and refactor code ([#11](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/11)) ([b6813f1](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/b6813f1ed1f1dfa98cc732b6d4df176831bf6bf0))
* Remove Datastore Integration to Enable Direct Image Uploads ([#34](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/34)) ([25f1ac0](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/25f1ac0fcea92ccb9de4c06d6c4a6a933d850775))
* remove recovery credential and select credential ([#18](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/18)) ([2b99da3](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/2b99da38601cbe23f367da7294223011bcdf9cbb))
* Replace formatBirthDate with new Agent implementation and rename issuedBy to issuanceDate ([#35](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/35)) ([53258ec](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/53258ecc536631f60cda84564a862b908babcf1a))
* update call offer params ([#31](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/31)) ([92f0a57](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/92f0a57d3172fa62b87bf8cf806579dc884073c4))
* update docker image name ([9334298](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/9334298c52e191d054b526aae5c63a31a69a9598))
* update docker image name ([#50](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/50)) ([44702b3](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/44702b325983ff2812dd2228b93442cd3422cbb6))
* update MRZ_SUCCESSFUL message ([#41](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/41)) ([9dc76c7](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/9dc76c757a1f16e3783242afd91c3b51998bad54))
* update pom image ([50f9fe6](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/50f9fe654eb094542a599532756bdfa0188b2998))
* update pom image ([90c597f](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/90c597fded1f776e60cce8e608b567fd1c4687f9))
* upgrade dependencies and implement refuse behavior handling ([#33](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/33)) ([d28f7d6](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/d28f7d68cf3553e9d62e0e5e178d3c2b0533fe91))
* version ([f9936c3](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f9936c3922788dbbd774dfeaf310dad1f78d3ba4))
* vision service integration ([#12](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/12)) ([8253f54](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/8253f54750349d573669a3fbe776bedadac93e4b))


### Bug Fixes

* add ports to service ([#56](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/56)) ([f0c4fca](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f0c4fca8ed39153e9bf9118e7591b8f2a200bfd3))
* adjust camel case and rules in variables ([0e81f7d](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/0e81f7d531a4b7ff26c31150ccefcc18d1c725f8))
* adjust messages ([ffa5288](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/ffa528842297ffd811a16c2fe10b15a14d976648))
* create helm chart for issuer ([#55](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/55)) ([a842f80](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/a842f8076efb55d3d8eab39db77f3b2dcb8f511a))
* date format and logger ([#14](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/14)) ([19e7b61](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/19e7b61fb4b4571612dbdde40eeea28722099d30))
* docker image name ([#27](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/27)) ([bcacd05](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/bcacd05d23560da2968a0aefb79ae12e7417503b))
* error detected when new conection has been created ([#20](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/20)) ([1842374](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/1842374676870bf3adfb57a5bb25ff84f928045d))
* finish after rejected ([#19](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/19)) ([4ed13dc](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/4ed13dc18eaf28c16347096b6df8fdf6140120a1))
* init version ([772549c](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/772549cab6c909a97ce3ae13568dfe8e0f7d6979))
* put data passport ([85ae598](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/85ae5988b7639e4527ca7fdc0aadc503cd6a3f8d))
* remove timeout validation when the credential han been present ([#44](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/44)) ([1f3fece](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/1f3fece07ca4a0e62c88f17aad23e154b2321350))
* remove unnecessary changes ([de7918a](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/de7918a8e4e48478c4d3b0c16a5649273b5fc3a2))
* remove unnecessary changes ([6bd60bf](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/6bd60bfb75f51ff59e5ce4349e3d653cb5a709c4))
* remove unnecessary changes ([d142ea8](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/d142ea87fe914dcd8a93708feae0a623aed2c0d1))
* rename fields ([#42](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/42)) ([9638c1a](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/9638c1a494f999f2eb0d46270a03b8eab292a73a))
* restore identity ([7d1b384](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/7d1b384facc22c490115e54b4441e024b22bcf88))
* simplify charts ([#65](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/65)) ([334ce1f](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/334ce1f1b76f9aa168ea98568fd6f566edb30f5b))
* timeout ([eca2f61](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/eca2f61249dbecfefde8f7718132effb6eaecaa7))
* timeout ([7f05068](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/7f050689ea956807e27c990678b80d5f5cc88421))
* update agent chart ([#58](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/58)) ([f65476f](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/f65476fff058a8563bb37e37970d07e55edb285d))
* update chart name and deploy ([#57](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/57)) ([23bbec5](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/23bbec54d0054ac8f3131acf1b1503e261ae29ec))
* update docs and version ([#63](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/63)) ([4ee716a](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/4ee716a5f816410c5bfcf9065fd4667749297cb1))
* upgrade version agent with new environments ([#59](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/59)) ([b78d7cd](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/b78d7cd1e0c92ca9c61f63bfe6d0c98195bf5843))
* upgrade vs-agent chart version ([#64](https://github.com/2060-io/hologram-gov-id-issuer-vs/issues/64)) ([78d5fb3](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/78d5fb30b8793ef64016691ee20a3e7ddd68d7e1))
* versionning not increment on linter ([31864f5](https://github.com/2060-io/hologram-gov-id-issuer-vs/commit/31864f5d06ee4ac0d0773c6a26fe0240bc8e62f5))
